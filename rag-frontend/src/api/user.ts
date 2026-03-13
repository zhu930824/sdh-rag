import request from '@/utils/request'

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
}

// 通用响应结构
export interface Result<T> {
  code: number
  message: string
  data: T
}

// 登录
export function login(data: LoginRequest): Promise<Result<{ token: string }>> {
  return request.post('/api/user/login', data)
}

// 注册
export function register(data: RegisterRequest): Promise<Result<null>> {
  return request.post('/api/user/register', data)
}

// 获取用户信息
export function getUserInfo(): Promise<Result<UserInfo>> {
  return request.get('/api/user/info')
}

// 登出
export function logout(): Promise<Result<null>> {
  return request.post('/api/user/logout')
}

// 默认导出所有接口
export default {
  login,
  register,
  getUserInfo,
  logout,
}
