import request from '@/utils/request'

/**
 * 用户信息
 */
export interface UserItem {
  id: number
  username: string
  nickname: string
  email: string
  role: string
  status: number
  createTime: string
  updateTime?: string
}

/**
 * 用户查询参数
 */
export interface UserQueryParams {
  page: number
  pageSize: number
  keyword?: string
  status?: number
  role?: string
}

/**
 * 用户创建/编辑参数
 */
export interface UserFormData {
  id?: number
  username: string
  nickname: string
  email: string
  role: string
  status: number
  password?: string
}

/**
 * 分页响应
 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/**
 * 通用响应
 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 获取用户列表
 */
export function getUserList(params: UserQueryParams): Promise<Result<PageResult<UserItem>>> {
  return request.get('/api/user/list', { params })
}

/**
 * 获取用户详情
 */
export function getUserDetail(id: number): Promise<Result<UserItem>> {
  return request.get(`/api/user/${id}`)
}

/**
 * 创建用户
 */
export function createUser(data: UserFormData): Promise<Result<null>> {
  return request.post('/api/user', data)
}

/**
 * 更新用户
 */
export function updateUser(id: number, data: Partial<UserFormData>): Promise<Result<null>> {
  return request.put(`/api/user/${id}`, data)
}

/**
 * 删除用户
 */
export function deleteUser(id: number): Promise<Result<null>> {
  return request.delete(`/api/user/${id}`)
}

/**
 * 批量删除用户
 */
export function batchDeleteUsers(ids: number[]): Promise<Result<null>> {
  return request.post('/api/user/batch-delete', { ids })
}

/**
 * 重置用户密码
 */
export function resetUserPassword(id: number): Promise<Result<{ password: string }>> {
  return request.post(`/api/user/${id}/reset-password`)
}

/**
 * 切换用户状态
 */
export function toggleUserStatus(id: number): Promise<Result<null>> {
  return request.put(`/api/user/${id}/status`)
}

export default {
  getUserList,
  getUserDetail,
  createUser,
  updateUser,
  deleteUser,
  batchDeleteUsers,
  resetUserPassword,
  toggleUserStatus,
}
