<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <!-- 左侧用户信息 -->
      <el-col :span="8">
        <el-card class="user-card">
          <div class="user-info">
            <el-avatar :size="100" :src="userInfo.avatar">
              <el-icon :size="50"><UserFilled /></el-icon>
            </el-avatar>
            <h2 class="username">{{ userInfo.nickname }}</h2>
            <p class="role">{{ userInfo.roles?.join(', ') || '普通用户' }}</p>
            <el-button type="primary" plain @click="handleEditAvatar">
              更换头像
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧详细信息 -->
      <el-col :span="16">
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span class="title">个人信息</span>
              <el-button type="primary" text @click="handleEdit">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">
              {{ userInfo.username }}
            </el-descriptions-item>
            <el-descriptions-item label="昵称">
              {{ userInfo.nickname }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ userInfo.email }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              {{ userInfo.roles?.join(', ') || '普通用户' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ userInfo.createTime }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 安全设置 -->
        <el-card class="security-card">
          <template #header>
            <div class="card-header">
              <span class="title">安全设置</span>
            </div>
          </template>

          <div class="security-item">
            <div class="security-info">
              <el-icon class="icon"><Lock /></el-icon>
              <div class="text">
                <div class="label">登录密码</div>
                <div class="desc">定期更换密码可以提高账号安全性</div>
              </div>
            </div>
            <el-button type="primary" text @click="handleChangePassword">
              修改
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑信息对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑个人信息" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="500px">
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item label="原密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores'

const userStore = useUserStore()

// 用户信息
const userInfo = computed(() => userStore.userInfo || {
  username: '',
  nickname: '',
  email: '',
  avatar: '',
  roles: [],
  createTime: '',
})

// 编辑对话框
const editDialogVisible = ref(false)
const editForm = reactive({
  nickname: '',
  email: '',
})

// 修改密码对话框
const passwordDialogVisible = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

// 编辑个人信息
function handleEdit(): void {
  editForm.nickname = userInfo.value.nickname
  editForm.email = userInfo.value.email
  editDialogVisible.value = true
}

// 保存编辑
function handleSaveEdit(): void {
  // TODO: 调用API更新用户信息
  ElMessage.success('保存成功')
  editDialogVisible.value = false
}

// 更换头像
function handleEditAvatar(): void {
  ElMessage.info('头像上传功能开发中')
}

// 修改密码
function handleChangePassword(): void {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 保存密码
function handleSavePassword(): void {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  // TODO: 调用API修改密码
  ElMessage.success('密码修改成功')
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
