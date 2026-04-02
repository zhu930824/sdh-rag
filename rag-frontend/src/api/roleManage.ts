import request from '@/utils/request'

/**
 * 角色信息
 */
export interface RoleItem {
  id: number
  name: string
  code: string
  description: string
  permissions: string
  status: number
  createTime: string
}

/**
 * 角色查询参数
 */
export interface RoleQueryParams {
  page: number
  pageSize: number
  keyword?: string
  status?: number
}

/**
 * 角色创建/编辑参数
 */
export interface RoleFormData {
  id?: number
  name: string
  code: string
  description: string
  permissions: string[]
  status: number
}

/**
 * 分页响应（MyBatis Plus IPage格式）
 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
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
 * 获取角色列表
 */
export function getRoleList(params: RoleQueryParams): Promise<Result<PageResult<RoleItem>>> {
  return request.get('/api/role/list', { params })
}

/**
 * 获取所有启用的角色
 */
export function getAllRoles(): Promise<Result<RoleItem[]>> {
  return request.get('/api/role/all')
}

/**
 * 获取角色详情
 */
export function getRoleDetail(id: number): Promise<Result<RoleItem>> {
  return request.get(`/api/role/${id}`)
}

/**
 * 创建角色
 */
export function createRole(data: RoleFormData): Promise<Result<null>> {
  return request.post('/api/role', data)
}

/**
 * 更新角色
 */
export function updateRole(id: number, data: Partial<RoleFormData>): Promise<Result<null>> {
  return request.put(`/api/role/${id}`, data)
}

/**
 * 删除角色
 */
export function deleteRole(id: number): Promise<Result<null>> {
  return request.delete(`/api/role/${id}`)
}

/**
 * 批量删除角色
 */
export function batchDeleteRoles(ids: number[]): Promise<Result<{ success: number; fail: number }>> {
  return request.post('/api/role/batch-delete', { ids })
}

/**
 * 切换角色状态
 */
export function toggleRoleStatus(id: number): Promise<Result<null>> {
  return request.put(`/api/role/${id}/status`)
}

export default {
  getRoleList,
  getAllRoles,
  getRoleDetail,
  createRole,
  updateRole,
  deleteRole,
  batchDeleteRoles,
  toggleRoleStatus,
}
