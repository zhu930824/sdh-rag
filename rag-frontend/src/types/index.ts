// API响应通用结构
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface PageParams {
  page: number
  pageSize: number
  keyword?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export interface RouteMeta {
  title?: string
  icon?: string
  hidden?: boolean
  requiresAuth?: boolean
  roles?: string[]
  breadcrumb?: boolean
  affix?: boolean
  keepAlive?: boolean
}

// ==================== 用户相关类型 ====================

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email?: string
  avatar?: string
  roles: string[]
  createTime: string
}

export interface LoginParams {
  username: string
  password: string
  remember?: boolean
}

export interface LoginResult {
  token: string
  user: UserInfo
}

// ==================== 模型配置类型 ====================

export interface ModelConfig {
  id: number
  name: string
  provider: string
  modelType: string
  modelId: string
  hasApiKey?: boolean
  apiKeyMasked?: string
  baseUrl?: string
  temperature: number
  maxTokens: number
  status: number
  isDefault: number
  sort: number
  isLocal?: number
  isBuiltIn?: number
  icon?: string
  createTime?: string
  updateTime?: string
}

// ==================== 工作流类型 ====================

export interface Workflow {
  id: number
  name: string
  description?: string
  flowData?: string
  status: number
  userId?: number
  createTime?: string
  updateTime?: string
}

export interface WorkflowNode {
  id: number
  workflowId: number
  nodeType: string
  nodeName: string
  nodeConfig?: string
  positionX: number
  positionY: number
  createTime?: string
}

// ==================== 嵌入配置类型 ====================

export interface EmbedConfig {
  id: number
  name: string
  theme: string
  position: string
  width: number
  height: number
  primaryColor: string
  title: string
  welcomeMsg: string
  knowledgeIds?: string
  status: number
  createTime?: string
  updateTime?: string
}

// ==================== 敏感词类型 ====================

export interface SensitiveWord {
  id: number
  word: string
  category: string
  status: number
  createTime?: string
  updateTime?: string
}

// ==================== 操作日志类型 ====================

export interface OperationLog {
  id: number
  userId?: number
  username?: string
  type: string
  content?: string
  ip?: string
  status: number
  browser?: string
  os?: string
  requestUrl?: string
  requestMethod?: string
  requestParams?: string
  responseData?: string
  errorMsg?: string
  duration?: number
  createTime?: string
}

// ==================== 热点词类型 ====================

export interface HotwordRecord {
  id: number
  word: string
  count: number
  queryDate: string
  userId?: number
  sessionId?: string
  createTime?: string
  updateTime?: string
}

// ==================== 系统设置类型 ====================

export interface SystemSettings {
  basic?: {
    systemName: string
    defaultTheme: string
  }
  model?: {
    defaultModel: string
    temperature: number
    maxTokens: number
  }
  notification?: {
    enableNotification: boolean
    enableEmail: boolean
    notificationEmail: string
  }
}

// ==================== 知识库类型 ====================

export interface KnowledgeDocument {
  id: number
  title: string
  content?: string
  fileType?: string
  filePath?: string
  fileSize?: number
  categoryId?: number
  userId: number
  status: number
  createTime?: string
  updateTime?: string
}

export interface DocumentCategory {
  id: number
  name: string
  description?: string
  parentId?: number
  sort: number
  createTime?: string
}

// ==================== 聊天类型 ====================

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
  timestamp: number
}

export interface Source {
  id: string
  documentId: number
  documentTitle: string
  content: string
  score: number
}

export interface ChatSession {
  id: string
  title: string
  createTime: string
  updateTime?: string
}

// ==================== NLP查询类型 ====================

export interface TableInfo {
  name: string
  comment: string
}

export interface ColumnInfo {
  name: string
  type: string
  comment: string
}

export interface NlpQueryResult {
  sql: string
  results: any[]
  message: string
}

// ==================== 角色权限类型 ====================

export interface Role {
  id: number
  name: string
  code: string
  description?: string
  status: number
  createTime?: string
}

export interface MenuPermission {
  id: number
  name: string
  path: string
  component?: string
  icon?: string
  parentId?: number
  sort: number
  visible: number
  permission?: string
  createTime?: string
}

export interface UserRole {
  userId: number
  roleId: number
}

export interface RoleMenu {
  roleId: number
  menuId: number
}