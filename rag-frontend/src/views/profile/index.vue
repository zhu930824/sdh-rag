<template>
  <div class="profile-page">
    <a-row :gutter="24" class="profile-row">
      <!-- 左侧用户卡片 -->
      <a-col :span="6" class="left-col">
        <div class="left-sidebar">
          <a-card class="user-card" :loading="loading">
            <div class="user-info">
              <div class="avatar-wrapper">
                <a-avatar :size="100" :src="userInfo.avatar" class="avatar">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <div class="avatar-overlay" @click="handleUploadAvatar">
                  <CameraOutlined />
                  <span>更换头像</span>
                </div>
                <input
                  ref="avatarInput"
                  type="file"
                  accept="image/*"
                  style="display: none"
                  @change="handleAvatarChange"
                />
              </div>
              <h2 class="username">{{ userInfo.nickname || userInfo.username }}</h2>
              <div class="user-level">
                <a-tag color="blue">Lv.{{ userLevel }}</a-tag>
                <span class="experience">{{ experience }} 经验</span>
              </div>
              <p class="signature">{{ userInfo.signature || '这个人很懒，什么都没留下...' }}</p>
            </div>

            <a-divider style="margin: 16px 0" />

            <!-- 快捷统计 -->
            <div class="quick-stats">
              <div class="stat-item" @click="handleNavigate('/knowledge')">
                <div class="stat-value">{{ stats.knowledgeCount }}</div>
                <div class="stat-label">知识库</div>
              </div>
              <div class="stat-item" @click="handleNavigate('/chat')">
                <div class="stat-value">{{ stats.chatCount }}</div>
                <div class="stat-label">对话数</div>
              </div>
              <div class="stat-item" @click="handleNavigate('/workflow')">
                <div class="stat-value">{{ stats.workflowCount }}</div>
                <div class="stat-label">工作流</div>
              </div>
            </div>
          </a-card>

          <!-- 成就卡片 -->
          <a-card class="achievement-card" title="今日活跃">
            <div class="today-stats">
              <div class="today-item">
                <MessageOutlined class="icon" />
                <span>{{ stats.todayChatCount }} 次对话</span>
              </div>
            </div>
            <div class="level-progress">
              <div class="progress-label">
                <span>升级进度</span>
                <span>{{ experience }} / {{ nextLevelExp }}</span>
              </div>
              <a-progress :percent="levelProgress" :show-info="false" stroke-color="#1890ff" />
            </div>
          </a-card>
        </div>
      </a-col>

      <!-- 右侧内容区 -->
      <a-col :span="18" class="right-col">
        <a-card class="content-card">
          <template #title>
            <div class="card-title-wrapper">
              <h2 class="card-title">个人中心</h2>
              <p class="card-subtitle">管理您的账户信息和偏好设置</p>
            </div>
          </template>
          <a-tabs v-model:activeKey="activeTab">
            <!-- 个人资料 -->
            <a-tab-pane key="profile" tab="个人资料">
              <div class="tab-content">
                <a-form
                  :model="profileForm"
                  :label-col="{ span: 4 }"
                  :wrapper-col="{ span: 16 }"
                  @finish="handleSaveProfile"
                >
                  <a-form-item label="用户名">
                    <a-input :value="userInfo.username" disabled />
                  </a-form-item>
                  <a-form-item label="昵称" name="nickname" :rules="[{ required: true, message: '请输入昵称' }]">
                    <a-input v-model:value="profileForm.nickname" placeholder="请输入昵称" />
                  </a-form-item>
                  <a-form-item label="邮箱" name="email">
                    <a-input v-model:value="profileForm.email" placeholder="请输入邮箱" />
                  </a-form-item>
                  <a-form-item label="手机号" name="phone">
                    <a-input v-model:value="profileForm.phone" placeholder="请输入手机号" />
                  </a-form-item>
                  <a-form-item label="个性签名" name="signature">
                    <a-textarea
                      v-model:value="profileForm.signature"
                      placeholder="写点什么介绍自己吧..."
                      :rows="3"
                      :maxlength="100"
                      show-count
                    />
                  </a-form-item>
                  <a-form-item label="角色">
                    <a-tag :color="roleColor">{{ roleLabel }}</a-tag>
                  </a-form-item>
                  <a-form-item label="注册时间">
                    <span>{{ formatTime(userInfo.createTime) }}</span>
                  </a-form-item>
                  <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
                    <a-button type="primary" html-type="submit" :loading="saving">保存修改</a-button>
                  </a-form-item>
                </a-form>
              </div>
            </a-tab-pane>

            <!-- 账号安全 -->
            <a-tab-pane key="security" tab="账号安全">
              <div class="tab-content">
                <div class="security-section">
                  <div class="section-title">
                    <LockOutlined />
                    <span>登录密码</span>
                  </div>
                  <div class="section-content">
                    <p class="section-desc">定期更换密码可以提高账号安全性</p>
                    <a-button type="primary" ghost @click="showPasswordModal = true">修改密码</a-button>
                  </div>
                </div>

                <a-divider />

                <div class="security-section">
                  <div class="section-title">
                    <MailOutlined />
                    <span>邮箱绑定</span>
                  </div>
                  <div class="section-content">
                    <p class="section-desc">
                      {{ userInfo.email ? `已绑定：${userInfo.email}` : '未绑定邮箱' }}
                    </p>
                    <a-button type="primary" ghost>
                      {{ userInfo.email ? '更换邮箱' : '绑定邮箱' }}
                    </a-button>
                  </div>
                </div>

                <a-divider />

                <div class="security-section">
                  <div class="section-title">
                    <PhoneOutlined />
                    <span>手机绑定</span>
                  </div>
                  <div class="section-content">
                    <p class="section-desc">
                      {{ userInfo.phone ? `已绑定：${maskPhone(userInfo.phone)}` : '未绑定手机' }}
                    </p>
                    <a-button type="primary" ghost>
                      {{ userInfo.phone ? '更换手机' : '绑定手机' }}
                    </a-button>
                  </div>
                </div>
              </div>
            </a-tab-pane>

            <!-- 使用统计 -->
            <a-tab-pane key="stats" tab="使用统计">
              <div class="tab-content">
                <a-row :gutter="16">
                  <a-col :span="8">
                    <a-card class="stat-card" hoverable @click="handleNavigate('/knowledge')">
                      <DatabaseOutlined class="stat-icon" style="color: #1890ff" />
                      <div class="stat-content">
                        <div class="stat-number">{{ stats.knowledgeCount }}</div>
                        <div class="stat-title">知识库</div>
                      </div>
                    </a-card>
                  </a-col>
                  <a-col :span="8">
                    <a-card class="stat-card" hoverable @click="handleNavigate('/document')">
                      <FileTextOutlined class="stat-icon" style="color: #52c41a" />
                      <div class="stat-content">
                        <div class="stat-number">{{ stats.documentCount }}</div>
                        <div class="stat-title">文档</div>
                      </div>
                    </a-card>
                  </a-col>
                  <a-col :span="8">
                    <a-card class="stat-card" hoverable @click="handleNavigate('/chat')">
                      <MessageOutlined class="stat-icon" style="color: #722ed1" />
                      <div class="stat-content">
                        <div class="stat-number">{{ stats.chatCount }}</div>
                        <div class="stat-title">对话</div>
                      </div>
                    </a-card>
                  </a-col>
                </a-row>

                <a-row :gutter="16" style="margin-top: 16px">
                  <a-col :span="8">
                    <a-card class="stat-card" hoverable @click="handleNavigate('/workflow')">
                      <ApartmentOutlined class="stat-icon" style="color: #fa8c16" />
                      <div class="stat-content">
                        <div class="stat-number">{{ stats.workflowCount }}</div>
                        <div class="stat-title">工作流</div>
                      </div>
                    </a-card>
                  </a-col>
                  <a-col :span="8">
                    <a-card class="stat-card" hoverable @click="handleNavigate('/prompt')">
                      <BulbOutlined class="stat-icon" style="color: #eb2f96" />
                      <div class="stat-content">
                        <div class="stat-number">{{ stats.promptCount }}</div>
                        <div class="stat-title">提示词</div>
                      </div>
                    </a-card>
                  </a-col>
                  <a-col :span="8">
                    <a-card class="stat-card">
                      <ThunderboltOutlined class="stat-icon" style="color: #13c2c2" />
                      <div class="stat-content">
                        <div class="stat-number">{{ stats.todayChatCount }}</div>
                        <div class="stat-title">今日对话</div>
                      </div>
                    </a-card>
                  </a-col>
                </a-row>
              </div>
            </a-tab-pane>

            <!-- 偏好设置 -->
            <a-tab-pane key="preference" tab="偏好设置">
              <div class="tab-content">
                <a-form
                  :model="preferenceForm"
                  :label-col="{ span: 6 }"
                  :wrapper-col="{ span: 14 }"
                  @finish="handleSavePreference"
                >
                  <a-form-item label="默认模型">
                    <a-select v-model:value="preferenceForm.defaultModelId" placeholder="选择默认模型" allow-clear>
                      <a-select-option v-for="model in modelList" :key="model.id" :value="model.id">
                        {{ model.name }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                  <a-form-item label="界面主题">
                    <a-radio-group v-model:value="preferenceForm.theme">
                      <a-radio-button value="light">浅色</a-radio-button>
                      <a-radio-button value="dark">深色</a-radio-button>
                      <a-radio-button value="auto">跟随系统</a-radio-button>
                    </a-radio-group>
                  </a-form-item>
                  <a-form-item label="界面语言">
                    <a-select v-model:value="preferenceForm.language" placeholder="选择语言">
                      <a-select-option value="zh-CN">简体中文</a-select-option>
                      <a-select-option value="en-US">English</a-select-option>
                    </a-select>
                  </a-form-item>
                  <a-form-item label="回复语言">
                    <a-select v-model:value="preferenceForm.replyLanguage" placeholder="选择回复语言" allow-clear>
                      <a-select-option value="auto">自动识别</a-select-option>
                      <a-select-option value="zh">中文</a-select-option>
                      <a-select-option value="en">英文</a-select-option>
                    </a-select>
                  </a-form-item>
                  <a-form-item label="邮件通知">
                    <a-switch v-model:checked="preferenceForm.emailNotification" />
                    <span class="form-tip">接收系统通知和重要消息</span>
                  </a-form-item>
                  <a-form-item label="声音提醒">
                    <a-switch v-model:checked="preferenceForm.soundNotification" />
                    <span class="form-tip">对话完成时播放提示音</span>
                  </a-form-item>
                  <a-form-item :wrapper-col="{ offset: 6, span: 14 }">
                    <a-button type="primary" html-type="submit" :loading="saving">保存设置</a-button>
                  </a-form-item>
                </a-form>
              </div>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </a-col>
    </a-row>

    <!-- 修改密码弹窗 -->
    <a-modal
      v-model:open="showPasswordModal"
      title="修改密码"
      :width="450"
      ok-text="确认修改"
      cancel-text="取消"
      @ok="handleSavePassword"
    >
      <a-form :model="passwordForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="原密码" name="oldPassword" :rules="[{ required: true, message: '请输入原密码' }]">
          <a-input-password v-model:value="passwordForm.oldPassword" placeholder="请输入原密码" />
        </a-form-item>
        <a-form-item label="新密码" name="newPassword" :rules="[{ required: true, message: '请输入新密码' }]">
          <a-input-password v-model:value="passwordForm.newPassword" placeholder="请输入新密码（6-20位）" />
        </a-form-item>
        <a-form-item
          label="确认密码"
          name="confirmPassword"
          :rules="[{ required: true, message: '请再次输入新密码' }]"
        >
          <a-input-password v-model:value="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  UserOutlined,
  CameraOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  MessageOutlined,
  ApartmentOutlined,
  BulbOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores'
import type { UserStatsResponse } from '@/api/user'
import { getActiveModels } from '@/api/model'
import type { ModelConfig } from '@/types'

const router = useRouter()
const userStore = useUserStore()

// 状态
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('profile')
const showPasswordModal = ref(false)
const avatarInput = ref<HTMLInputElement>()
const modelList = ref<ModelConfig[]>([])

// 用户信息
const userInfo = computed(() => userStore.userInfo || {
  username: '',
  nickname: '',
  email: '',
  phone: '',
  signature: '',
  avatar: '',
  roles: [],
  createTime: '',
})

const userLevel = computed(() => userStore.userLevel)
const experience = computed(() => userStore.experience)

// 角色显示
const roleLabel = computed(() => {
  const role = userInfo.value.role
  const roleMap: Record<string, string> = {
    admin: '管理员',
    user: '普通用户',
  }
  return roleMap[role || ''] || role || '普通用户'
})

const roleColor = computed(() => {
  const role = userInfo.value.role
  if (role === 'admin') return 'red'
  return 'blue'
})

// 下一级所需经验
const nextLevelExp = computed(() => {
  return userLevel.value * 100
})

const levelProgress = computed(() => {
  return Math.min((experience.value / nextLevelExp.value) * 100, 100)
})

// 统计数据
const stats = ref<UserStatsResponse>({
  knowledgeCount: 0,
  documentCount: 0,
  chatCount: 0,
  todayChatCount: 0,
  workflowCount: 0,
  promptCount: 0,
  userLevel: 1,
  experience: 0,
})

// 表单
const profileForm = reactive({
  nickname: '',
  email: '',
  phone: '',
  signature: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const preferenceForm = reactive({
  defaultModelId: undefined as number | undefined,
  theme: 'light',
  language: 'zh-CN',
  replyLanguage: 'auto',
  emailNotification: false,
  soundNotification: true,
})

// 初始化
onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    // 并行加载数据
    const [statsResult, modelsResult] = await Promise.all([
      userStore.fetchUserStats(),
      getActiveModels(),
    ])

    if (statsResult) {
      stats.value = statsResult
    }

    if (modelsResult.code === 200 || modelsResult.code === 0) {
      modelList.value = modelsResult.data || []
    }

    // 初始化表单
    profileForm.nickname = userInfo.value.nickname || ''
    profileForm.email = userInfo.value.email || ''
    profileForm.phone = userInfo.value.phone || ''
    profileForm.signature = userInfo.value.signature || ''

    preferenceForm.defaultModelId = userInfo.value.defaultModelId
    preferenceForm.theme = userInfo.value.theme || 'light'
    preferenceForm.language = userInfo.value.language || 'zh-CN'
    preferenceForm.replyLanguage = userInfo.value.replyLanguage || 'auto'
    preferenceForm.emailNotification = userInfo.value.emailNotification ?? false
    preferenceForm.soundNotification = userInfo.value.soundNotification ?? true
  } finally {
    loading.value = false
  }
}

// 头像上传
function handleUploadAvatar() {
  avatarInput.value?.click()
}

async function handleAvatarChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // 检查文件类型
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return
  }

  // 检查文件大小 (最大 2MB)
  if (file.size > 2 * 1024 * 1024) {
    message.error('图片大小不能超过 2MB')
    return
  }

  const result = await userStore.uploadAvatar(file)
  if (result) {
    // 清空 input 以便再次选择相同文件
    target.value = ''
  }
}

// 保存个人资料
async function handleSaveProfile() {
  saving.value = true
  try {
    await userStore.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: profileForm.phone,
      signature: profileForm.signature,
    })
  } finally {
    saving.value = false
  }
}

// 修改密码
async function handleSavePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.error('两次输入的密码不一致')
    return
  }

  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 20) {
    message.error('密码长度必须在6-20个字符之间')
    return
  }

  saving.value = true
  try {
    const success = await userStore.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword,
    })

    if (success) {
      showPasswordModal.value = false
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    }
  } finally {
    saving.value = false
  }
}

// 保存偏好设置
async function handleSavePreference() {
  saving.value = true
  try {
    await userStore.updatePreference({
      defaultModelId: preferenceForm.defaultModelId,
      theme: preferenceForm.theme,
      language: preferenceForm.language,
      replyLanguage: preferenceForm.replyLanguage,
      emailNotification: preferenceForm.emailNotification,
      soundNotification: preferenceForm.soundNotification,
    })
  } finally {
    saving.value = false
  }
}

// 导航
function handleNavigate(path: string) {
  router.push(path)
}

// 格式化时间
function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

// 遮蔽手机号
function maskPhone(phone: string) {
  if (!phone || phone.length < 7) return phone
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}
</script>

<style scoped lang="scss">
.profile-page {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.profile-row {
  flex: 1;
  min-height: 0;
}

.left-col {
  height: 100%;
}

.right-col {
  height: 100%;
}

.left-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-card {
  flex-shrink: 0;

  :deep(.ant-card-body) {
    padding: 20px;
  }

  .user-info {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 8px 0;

    .avatar-wrapper {
      position: relative;
      cursor: pointer;

      .avatar {
        border: 4px solid var(--bg-light);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }

      .avatar-overlay {
        position: absolute;
        top: 0;
        left: 0;
        width: 100px;
        height: 100px;
        border-radius: 50%;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        color: #fff;
        opacity: 0;
        transition: opacity 0.3s;

        .anticon {
          font-size: 20px;
          margin-bottom: 4px;
        }

        span {
          font-size: 12px;
        }
      }

      &:hover .avatar-overlay {
        opacity: 1;
      }
    }

    .username {
      margin: 16px 0 8px;
      font-size: 20px;
      font-weight: 600;
    }

    .user-level {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;

      .experience {
        font-size: 12px;
        color: var(--text-secondary);
      }
    }

    .signature {
      color: var(--text-secondary);
      font-size: 14px;
      text-align: center;
      margin: 0;
    }
  }

  .quick-stats {
    display: flex;
    justify-content: space-around;
    padding: 8px 0;

    .stat-item {
      text-align: center;
      cursor: pointer;
      transition: transform 0.2s;

      &:hover {
        transform: scale(1.05);
      }

      .stat-value {
        font-size: 24px;
        font-weight: 600;
        color: var(--primary-color);
      }

      .stat-label {
        font-size: 12px;
        color: var(--text-secondary);
        margin-top: 4px;
      }
    }
  }
}

.achievement-card {
  flex: 1;

  :deep(.ant-card-body) {
    padding: 16px 20px;
  }

  :deep(.ant-card-head) {
    min-height: 40px;
    padding: 0 20px;

    .ant-card-head-title {
      padding: 12px 0;
    }
  }

  .today-stats {
    .today-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 0;

      .icon {
        font-size: 18px;
        color: var(--primary-color);
      }
    }
  }

  .level-progress {
    margin-top: 16px;

    .progress-label {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      font-size: 12px;
      color: var(--text-secondary);
    }
  }
}

.content-card {
  height: 100%;

  :deep(.ant-card-head) {
    border-bottom: 1px solid var(--border-color);
    padding: 16px 24px;
    min-height: auto;
  }

  :deep(.ant-card-body) {
    height: calc(100% - 73px);
    padding: 0 24px 24px;
    display: flex;
    flex-direction: column;
  }

  .card-title-wrapper {
    .card-title {
      margin: 0 0 4px;
      font-size: 20px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .card-subtitle {
      margin: 0;
      font-size: 14px;
      color: var(--text-secondary);
    }
  }

  :deep(.ant-tabs) {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  :deep(.ant-tabs-content) {
    flex: 1;
    min-height: 0;
  }

  :deep(.ant-tabs-tabpane) {
    height: 100%;
  }

  .tab-content {
    height: 100%;
    overflow-y: auto;
    padding: 24px 0;
  }
}

.security-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;

  .section-title {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 16px;
    font-weight: 500;

    .anticon {
      font-size: 20px;
      color: var(--primary-color);
    }
  }

  .section-content {
    display: flex;
    align-items: center;
    gap: 16px;

    .section-desc {
      color: var(--text-secondary);
      margin: 0;
    }
  }
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;

  .stat-icon {
    font-size: 36px;
    margin-right: 16px;
  }

  .stat-content {
    .stat-number {
      font-size: 28px;
      font-weight: 600;
    }

    .stat-title {
      font-size: 14px;
      color: var(--text-secondary);
      margin-top: 4px;
    }
  }
}

.form-tip {
  margin-left: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
