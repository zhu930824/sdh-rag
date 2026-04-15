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

          <!-- 账户信息卡片 -->
          <a-card class="info-card" title="账户信息">
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">角色</span>
                <a-tag :color="roleColor">{{ roleLabel }}</a-tag>
              </div>
              <div class="info-item">
                <span class="info-label">状态</span>
                <a-tag :color="userInfo.status === 1 ? 'green' : 'red'">
                  {{ userInfo.status === 1 ? '正常' : '禁用' }}
                </a-tag>
              </div>
              <div class="info-item">
                <span class="info-label">注册时间</span>
                <span class="info-value">{{ formatTime(userInfo.createTime) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">上次更新</span>
                <span class="info-value">{{ formatTime(userInfo.updateTime) }}</span>
              </div>
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
              <p class="card-subtitle">管理您的账户信息和安全设置</p>
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
                    <a-button type="primary" ghost @click="showEmailModal = true">
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
                    <a-button type="primary" ghost @click="showPhoneModal = true">
                      {{ userInfo.phone ? '更换手机' : '绑定手机' }}
                    </a-button>
                  </div>
                </div>
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
      :confirm-loading="saving"
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

    <!-- 修改邮箱弹窗 -->
    <a-modal
      v-model:open="showEmailModal"
      title="绑定邮箱"
      :width="450"
      ok-text="确认"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="handleSaveEmail"
    >
      <a-form :model="emailForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="邮箱地址" name="email" :rules="[{ required: true, type: 'email', message: '请输入有效的邮箱地址' }]">
          <a-input v-model:value="emailForm.email" placeholder="请输入邮箱地址" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 修改手机弹窗 -->
    <a-modal
      v-model:open="showPhoneModal"
      title="绑定手机"
      :width="450"
      ok-text="确认"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="handleSavePhone"
    >
      <a-form :model="phoneForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="手机号码" name="phone" :rules="[{ required: true, pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号码' }]">
          <a-input v-model:value="phoneForm.phone" placeholder="请输入手机号码" />
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
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores'
import type { UserStatsResponse } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

// 状态
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('profile')
const showPasswordModal = ref(false)
const showEmailModal = ref(false)
const showPhoneModal = ref(false)
const avatarInput = ref<HTMLInputElement>()

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
  updateTime: '',
  status: 1,
})

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

const emailForm = reactive({
  email: '',
})

const phoneForm = reactive({
  phone: '',
})

// 初始化
onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    // 加载统计数据
    const statsResult = await userStore.fetchUserStats()
    if (statsResult) {
      stats.value = statsResult
    }

    // 初始化表单
    profileForm.nickname = userInfo.value.nickname || ''
    profileForm.email = userInfo.value.email || ''
    profileForm.phone = userInfo.value.phone || ''
    profileForm.signature = userInfo.value.signature || ''
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

  try {
    const result = await userStore.uploadAvatar(file)
    if (result) {
      // 清空 input 以便再次选择相同文件
      target.value = ''
    }
  } catch (error) {
    console.error('上传头像失败:', error)
    message.error('上传头像失败')
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

// 保存邮箱
async function handleSaveEmail() {
  if (!emailForm.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailForm.email)) {
    message.error('请输入有效的邮箱地址')
    return
  }

  saving.value = true
  try {
    const success = await userStore.updateProfile({
      nickname: profileForm.nickname,
      email: emailForm.email,
      phone: profileForm.phone,
      signature: profileForm.signature,
    })

    if (success) {
      showEmailModal.value = false
      profileForm.email = emailForm.email
    }
  } finally {
    saving.value = false
  }
}

// 保存手机号
async function handleSavePhone() {
  if (!phoneForm.phone || !/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    message.error('请输入有效的手机号码')
    return
  }

  saving.value = true
  try {
    const success = await userStore.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: phoneForm.phone,
      signature: profileForm.signature,
    })

    if (success) {
      showPhoneModal.value = false
      profileForm.phone = phoneForm.phone
    }
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

.info-card {
  flex: 1;

  :deep(.ant-card-head) {
    min-height: 40px;
    padding: 0 20px;

    .ant-card-head-title {
      padding: 12px 0;
    }
  }

  :deep(.ant-card-body) {
    padding: 16px 20px;
  }

  .info-list {
    .info-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;

      .info-label {
        color: var(--text-secondary);
        font-size: 14px;
      }

      .info-value {
        font-size: 14px;
        color: var(--text-primary);
      }
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
</style>
