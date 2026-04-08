import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 登录请求参数
export interface LoginRequest {
  username: string
  password: string
}

// 注册请求参数
export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

// 用户信息
export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  email?: string
  phone?: string
  signature?: string
  role?: string
  permissions?: string[]
  status: number
  createTime: string
  defaultModelId?: number
  theme?: string
  language?: string
  emailNotification?: boolean
  soundNotification?: boolean
  replyLanguage?: string
  userLevel?: number
  experience?: number
}

// 更新个人信息请求
export interface UpdateProfileRequest {
  nickname: string
  email?: string
  signature?: string
  phone?: string
}

// 修改密码请求
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

// 用户偏好设置请求
export interface UserPreferenceRequest {
  defaultModelId?: number
  theme?: string
  language?: string
  emailNotification?: boolean
  soundNotification?: boolean
  replyLanguage?: string
}

// 用户统计数据
export interface UserStatsResponse {
  knowledgeCount: number
  documentCount: number
  chatCount: number
  todayChatCount: number
  workflowCount: number
  promptCount: number
  userLevel: number
  experience: number
}

// 登录
export function login(data: LoginRequest): Promise<ApiResponse<{ token: string }>> {
  return request.post('/api/user/login', data)
}

// 注册
export function register(data: RegisterRequest): Promise<ApiResponse<null>> {
  return request.post('/api/user/register', data)
}

// 获取用户信息
export function getUserInfo(): Promise<ApiResponse<UserInfo>> {
  return request.get('/api/user/info')
}

// 登出
export function logout(): Promise<ApiResponse<null>> {
  return request.post('/api/user/logout')
}

// 更新个人信息
export function updateProfile(data: UpdateProfileRequest): Promise<ApiResponse<null>> {
  return request.put('/api/user/profile', data)
}

// 修改密码
export function changePassword(data: ChangePasswordRequest): Promise<ApiResponse<null>> {
  return request.put('/api/user/password', data)
}

// 上传头像
export function uploadAvatar(file: File): Promise<ApiResponse<{ avatar: string }>> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/user/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 更新用户偏好设置
export function updatePreference(data: UserPreferenceRequest): Promise<ApiResponse<null>> {
  return request.put('/api/user/preference', data)
}

// 获取用户统计数据
export function getUserStats(): Promise<ApiResponse<UserStatsResponse>> {
  return request.get('/api/user/stats')
}

export default {
  login,
  register,
  getUserInfo,
  logout,
  updateProfile,
  changePassword,
  uploadAvatar,
  updatePreference,
  getUserStats,
}
