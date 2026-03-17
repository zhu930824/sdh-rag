import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import router from '@/router'

// 创建Axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// 请求拦截器 - 添加JWT Token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    // 如果存在token则添加到请求头
//     if (userStore.token) {
//       config.headers.Authorization = `Bearer ${userStore.token}`
//     }
    if (userStore.token && userStore.token.trim()) {
          // 确保 headers 对象存在
          config.headers = config.headers || {}
          // 使用 token 作为 header 名称
          if (typeof config.headers.set === 'function') {
            config.headers.set('token', userStore.token)
          } else {
            config.headers.token = userStore.token
          }
        }
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理错误
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response
    // 根据业务状态码处理
    if (data.code === 200 || data.code === 0) {
      return data
    }
    // 业务错误
    message.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  (error) => {
    const { response } = error
    if (response) {
      const { status, data } = response
      switch (status) {
        case 401:
          // 未授权，清除token并跳转登录页
          message.error('登录已过期，请重新登录')
          const userStore = useUserStore()
          userStore.clearAuth()
          router.push('/login')
          break
        case 403:
          message.error('没有权限访问该资源')
          break
        case 404:
          message.error('请求的资源不存在')
          break
        case 500:
          message.error('服务器内部错误')
          break
        default:
          message.error(data?.message || '请求失败')
      }
    } else if (error.code === 'ECONNABORTED') {
      message.error('请求超时，请稍后重试')
    } else {
      message.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

// 封装请求方法
const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config)
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config)
  },
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.patch(url, data, config)
  },
}

export default request
export { service as axiosInstance }
