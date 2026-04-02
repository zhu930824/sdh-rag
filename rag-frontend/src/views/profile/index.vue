<template>
  <div class="profile-page">
    <a-row :gutter="20">
      <a-col :span="8">
        <a-card class="user-card">
          <div class="user-info">
            <a-avatar :size="100" :src="userInfo.avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <h2 class="username">{{ userInfo.nickname }}</h2>
            <p class="role">{{ userInfo.roles?.join(', ') || '普通用户' }}</p>
            <a-button type="primary" ghost @click="handleEditAvatar">
              更换头像
            </a-button>
          </div>
        </a-card>
      </a-col>

      <a-col :span="16">
        <a-card class="info-card">
          <template #title>
            <div class="card-header">
              <span class="title">个人信息</span>
              <a-button type="link" @click="handleEdit">
                <EditOutlined />
                编辑
              </a-button>
            </div>
          </template>

          <a-descriptions :column="1" bordered>
            <a-descriptions-item label="用户名">
              {{ userInfo.username }}
            </a-descriptions-item>
            <a-descriptions-item label="昵称">
              {{ userInfo.nickname }}
            </a-descriptions-item>
            <a-descriptions-item label="邮箱">
              {{ userInfo.email }}
            </a-descriptions-item>
            <a-descriptions-item label="角色">
              {{ userInfo.roles?.join(', ') || '普通用户' }}
            </a-descriptions-item>
            <a-descriptions-item label="注册时间">
              {{ userInfo.createTime }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card class="security-card">
          <template #title>
            <div class="card-header">
              <span class="title">安全设置</span>
            </div>
          </template>

          <div class="security-item">
            <div class="security-info">
              <LockOutlined class="icon" />
              <div class="text">
                <div class="label">登录密码</div>
                <div class="desc">定期更换密码可以提高账号安全性</div>
              </div>
            </div>
            <a-button type="link" @click="handleChangePassword">
              修改
            </a-button>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-modal
      v-model:open="editDialogVisible"
      title="编辑个人信息"
      :width="500"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleSaveEdit"
      @cancel="editDialogVisible = false"
    >
      <a-form :model="editForm" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="昵称">
          <a-input v-model:value="editForm.nickname" placeholder="请输入昵称" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="editForm.email" placeholder="请输入邮箱" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="passwordDialogVisible"
      title="修改密码"
      :width="500"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleSavePassword"
      @cancel="passwordDialogVisible = false"
    >
      <a-form :model="passwordForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="原密码">
          <a-input-password
            v-model:value="passwordForm.oldPassword"
            placeholder="请输入原密码"
          />
        </a-form-item>
        <a-form-item label="新密码">
          <a-input-password
            v-model:value="passwordForm.newPassword"
            placeholder="请输入新密码"
          />
        </a-form-item>
        <a-form-item label="确认新密码">
          <a-input-password
            v-model:value="passwordForm.confirmPassword"
            placeholder="请再次输入新密码"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { UserOutlined, EditOutlined, LockOutlined } from '@ant-design/icons-vue'
import { showSuccess, showError, showInfo } from '@/utils/message'
import { useUserStore } from '@/stores'

const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo || {
  username: '',
  nickname: '',
  email: '',
  avatar: '',
  roles: [],
  createTime: '',
})

const editDialogVisible = ref(false)
const editForm = reactive({
  nickname: '',
  email: '',
})

const passwordDialogVisible = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function handleEdit(): void {
  editForm.nickname = userInfo.value.nickname
  editForm.email = userInfo.value.email
  editDialogVisible.value = true
}

function handleSaveEdit(): void {
  showSuccess('保存成功')
  editDialogVisible.value = false
}

function handleEditAvatar(): void {
  showInfo('头像上传功能开发中')
}

function handleChangePassword(): void {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

function handleSavePassword(): void {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    showError('两次输入的密码不一致')
    return
  }
  showSuccess('密码修改成功')
  passwordDialogVisible.value = false
}
</script>

<style scoped lang="scss">
.profile-page {
  height: 100%;
}

.user-card {
  .user-info {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px 0;

    .username {
      margin: 16px 0 8px;
      font-size: 20px;
      font-weight: 600;
    }

    .role {
      margin-bottom: 16px;
      color: var(--text-secondary);
      font-size: 14px;
    }
  }
}

.info-card,
.security-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .title {
      font-size: 16px;
      font-weight: 600;
    }
  }
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid var(--border-lighter);

  &:last-child {
    border-bottom: none;
  }

  .security-info {
    display: flex;
    align-items: center;
    gap: 16px;

    .icon {
      font-size: 24px;
      color: var(--primary-color);
    }

    .text {
      .label {
        font-size: 14px;
        font-weight: 500;
        margin-bottom: 4px;
      }

      .desc {
        font-size: 12px;
        color: var(--text-secondary);
      }
    }
  }
}
</style>
