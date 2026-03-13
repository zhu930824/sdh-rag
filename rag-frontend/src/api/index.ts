import request from '@/utils/request'
import type { LoginParams, LoginResult, UserInfo, ApiResponse } from '@/types'

// 用户认证相关接口
export const authApi = {
  // 登录
  login(params: LoginParams): Promise<LoginResult> {
    return request.post('/api/auth/login', params)
  },

  // 登出
  logout(): Promise<ApiResponse> {
    return request.post('/api/auth/logout')
  },

  // 刷新token
  refreshToken(): Promise<{ token: string }> {
    return request.post('/api/auth/refresh')
  },
}

// 用户管理相关接口
export const userApi = {
  // 获取用户信息
  getInfo(): Promise<UserInfo> {
    return request.get('/api/user/info')
  },

  // 获取用户列表
  getList(params: { page: number; pageSize: number; keyword?: string }): Promise<any> {
    return request.get('/api/user/list', { params })
  },

  // 创建用户
  create(data: Partial<UserInfo>): Promise<UserInfo> {
    return request.post('/api/user', data)
  },

  // 更新用户
  update(id: number, data: Partial<UserInfo>): Promise<UserInfo> {
    return request.put(`/api/user/${id}`, data)
  },

  // 删除用户
  delete(id: number): Promise<ApiResponse> {
    return request.delete(`/api/user/${id}`)
  },
}

// 知识库相关接口
export const knowledgeApi = {
  // 获取知识库列表
  getList(params: { page: number; pageSize: number; keyword?: string }): Promise<any> {
    return request.get('/api/knowledge/list', { params })
  },

  // 获取知识库详情
  getDetail(id: number): Promise<any> {
    return request.get(`/api/knowledge/${id}`)
  },

  // 创建知识库
  create(data: { name: string; description?: string }): Promise<any> {
    return request.post('/api/knowledge', data)
  },

  // 更新知识库
  update(id: number, data: { name?: string; description?: string }): Promise<any> {
    return request.put(`/api/knowledge/${id}`, data)
  },

  // 删除知识库
  delete(id: number): Promise<ApiResponse> {
    return request.delete(`/api/knowledge/${id}`)
  },
}

// 文档相关接口
export const documentApi = {
  // 获取文档列表
  getList(params: { page: number; pageSize: number; knowledgeId?: number; keyword?: string }): Promise<any> {
    return request.get('/api/document/list', { params })
  },

  // 上传文档
  upload(formData: FormData): Promise<any> {
    return request.post('/api/document/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  // 删除文档
  delete(id: number): Promise<ApiResponse> {
    return request.delete(`/api/document/${id}`)
  },
}

// 问答相关接口
export const chatApi = {
  // 发送问题
  ask(data: { question: string; knowledgeId: number }): Promise<any> {
    return request.post('/api/chat/ask', data)
  },

  // 获取历史记录
  getHistory(params: { page: number; pageSize: number }): Promise<any> {
    return request.get('/api/chat/history', { params })
  },
}
