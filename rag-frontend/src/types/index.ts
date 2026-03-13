// 用户信息类型
export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  avatar?: string
  roles: string[]
  createTime: string
}

// 登录请求参数
export interface LoginParams {
  username: string
  password: string
  remember?: boolean
}

// 登录响应
export interface LoginResult {
  token: string
  user: UserInfo
}

// API响应通用结构
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 分页请求参数
export interface PageParams {
  page: number
  pageSize: number
  keyword?: string
}

// 分页响应数据
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

// 路由元信息类型
export interface RouteMeta {
  title?: string
  icon?: string
  hidden?: boolean
  requiresAuth?: boolean
  roles?: string[]
  breadcrumb?: boolean // 是否显示面包屑
  affix?: boolean // 是否固定在标签页
  keepAlive?: boolean // 是否缓存页面
}
