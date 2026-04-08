import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  login,
  register,
  getUserInfo,
  logout,
  updateProfile,
  changePassword,
  uploadAvatar,
  updatePreference,
  getUserStats,
  type LoginRequest,
  type RegisterRequest,
  type UserInfo,
  type UpdateProfileRequest,
  type ChangePasswordRequest,
  type UserPreferenceRequest,
  type UserStatsResponse,
} from '@/api/user'
import { message } from 'ant-design-vue'

// Token存储键名
const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'
const REMEMBER_KEY = 'rememberLogin'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)
  const loading = ref(false)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const permissions = computed(() => userInfo.value?.permissions || [])
  const role = computed(() => userInfo.value?.role || '')
  const userLevel = computed(() => userInfo.value?.userLevel || 1)
  const experience = computed(() => userInfo.value?.experience || 0)

  // 保存Token到本地存储
  function saveToken(newToken: string, remember: boolean = false): void {
    token.value = newToken
    if (remember) {
      localStorage.setItem(TOKEN_KEY, newToken)
      localStorage.setItem(REMEMBER_KEY, 'true')
    } else {
      sessionStorage.setItem(TOKEN_KEY, newToken)
      localStorage.removeItem(REMEMBER_KEY)
    }
  }

  // 清除Token和用户信息
  function clearAuth(): void {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
    localStorage.removeItem(REMEMBER_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
  }

  // 登录方法
  async function loginAction(data: LoginRequest & { remember?: boolean }): Promise<boolean> {
    loading.value = true
    try {
      const { remember, ...loginData } = data
      const result = await login(loginData)
      if (result.code === 200 || result.code === 0) {
        saveToken(result.data.token, remember)
        message.success('登录成功')
        return true
      }
      message.error(result.message || '登录失败')
      return false
    } catch (error) {
      console.error('登录失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  // 注册方法
  async function registerAction(data: RegisterRequest): Promise<boolean> {
    loading.value = true
    try {
      const result = await register(data)
      if (result.code === 200 || result.code === 0) {
        message.success('注册成功，请登录')
        return true
      }
      message.error(result.message || '注册失败')
      return false
    } catch (error) {
      console.error('注册失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  // 登出方法
  async function logoutAction(): Promise<void> {
    try {
      await logout()
    } catch (error) {
      console.error('登出接口调用失败:', error)
    } finally {
      clearAuth()
    }
  }

  // 获取用户信息
  async function fetchUserInfo(): Promise<UserInfo | null> {
    if (!token.value) return null

    try {
      const result = await getUserInfo()
      console.log('getUserInfo result:', result)
      console.log('getUserInfo data:', result.data)
      console.log('getUserInfo permissions:', result.data?.permissions)
      if (result.code === 200 || result.code === 0) {
        userInfo.value = result.data
        return result.data
      }
      return null
    } catch (error) {
      console.error('获取用户信息失败:', error)
      return null
    }
  }

  // 更新个人信息
  async function updateProfileAction(data: UpdateProfileRequest): Promise<boolean> {
    loading.value = true
    try {
      const result = await updateProfile(data)
      if (result.code === 200 || result.code === 0) {
        message.success('更新成功')
        // 重新获取用户信息
        await fetchUserInfo()
        return true
      }
      message.error(result.message || '更新失败')
      return false
    } catch (error) {
      console.error('更新个人信息失败:', error)
      message.error('更新失败')
      return false
    } finally {
      loading.value = false
    }
  }

  // 修改密码
  async function changePasswordAction(data: ChangePasswordRequest): Promise<boolean> {
    loading.value = true
    try {
      const result = await changePassword(data)
      if (result.code === 200 || result.code === 0) {
        message.success('密码修改成功')
        return true
      }
      message.error(result.message || '密码修改失败')
      return false
    } catch (error) {
      console.error('修改密码失败:', error)
      message.error('密码修改失败')
      return false
    } finally {
      loading.value = false
    }
  }

  // 上传头像
  async function uploadAvatarAction(file: File): Promise<string | null> {
    loading.value = true
    try {
      const result = await uploadAvatar(file)
      if (result.code === 200 || result.code === 0) {
        message.success('头像上传成功')
        // 更新本地用户信息
        if (userInfo.value) {
          userInfo.value.avatar = result.data.avatar
        }
        return result.data.avatar
      }
      message.error(result.message || '头像上传失败')
      return null
    } catch (error) {
      console.error('上传头像失败:', error)
      message.error('头像上传失败')
      return null
    } finally {
      loading.value = false
    }
  }

  // 更新偏好设置
  async function updatePreferenceAction(data: UserPreferenceRequest): Promise<boolean> {
    loading.value = true
    try {
      const result = await updatePreference(data)
      if (result.code === 200 || result.code === 0) {
        message.success('设置保存成功')
        // 重新获取用户信息
        await fetchUserInfo()
        return true
      }
      message.error(result.message || '设置保存失败')
      return false
    } catch (error) {
      console.error('更新偏好设置失败:', error)
      message.error('设置保存失败')
      return false
    } finally {
      loading.value = false
    }
  }

  // 获取用户统计数据
  async function fetchUserStats(): Promise<UserStatsResponse | null> {
    try {
      const result = await getUserStats()
      if (result.code === 200 || result.code === 0) {
        return result.data
      }
      return null
    } catch (error) {
      console.error('获取用户统计数据失败:', error)
      return null
    }
  }

  // 自动恢复登录状态
  async function restoreLoginState(): Promise<boolean> {
    // 检查是否有保存的token
    const savedToken = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY)
    if (!savedToken) return false

    token.value = savedToken

    // 尝试获取用户信息以验证token有效性
    const info = await fetchUserInfo()
    return !!info
  }

  return {
    // 状态
    token,
    userInfo,
    loading,
    // 计算属性
    isLoggedIn,
    username,
    nickname,
    avatar,
    permissions,
    role,
    userLevel,
    experience,
    // 方法
    login: loginAction,
    register: registerAction,
    logout: logoutAction,
    fetchUserInfo,
    updateProfile: updateProfileAction,
    changePassword: changePasswordAction,
    uploadAvatar: uploadAvatarAction,
    updatePreference: updatePreferenceAction,
    fetchUserStats,
    restoreLoginState,
    clearAuth,
  }
})
