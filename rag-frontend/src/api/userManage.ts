import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

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
 * 获取用户列表
 */
export function getUserList(params: UserQueryParams): Promise<ApiResponse<PageResult<UserItem>>> {
  return request.get('/api/user/list', { params })
}

/**
 * 获取用户详情
 */
export function getUserDetail(id: number): Promise<ApiResponse<UserItem>> {
  return request.get(`/api/user/${id}`)
}

/**
 * 创建用户
 */
export function createUser(data: UserFormData): Promise<ApiResponse<null>> {
  return request.post('/api/user', data)
}

/**
 * 更新用户
 */
export function updateUser(id: number, data: Partial<UserFormData>): Promise<ApiResponse<null>> {
  return request.post(`/api/user/update/${id}`, data)
}

/**
 * 删除用户
 */
export function deleteUser(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/user/delete/${id}`)
}

/**
 * 批量删除用户
 */
export function batchDeleteUsers(ids: number[]): Promise<ApiResponse<null>> {
  return request.post('/api/user/batch-delete', { ids })
}

/**
 * 重置用户密码
 */
export function resetUserPassword(id: number): Promise<ApiResponse<{ password: string }>> {
  return request.post(`/api/user/${id}/reset-password`)
}

/**
 * 切换用户状态
 */
export function toggleUserStatus(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/user/status/${id}`)
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
