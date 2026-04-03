import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getUserInfo, logout, type LoginRequest, type RegisterRequest, type UserInfo } from '@/api/user'
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
    // 方法
    login: loginAction,
    register: registerAction,
    logout: logoutAction,
    fetchUserInfo,
    restoreLoginState,
    clearAuth,
  }
})
