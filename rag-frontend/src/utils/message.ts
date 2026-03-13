import { ElMessage, type MessageParams } from 'element-plus'

/**
 * 消息提示配置
 */
const defaultOptions: Partial<MessageParams> = {
  duration: 3000,
  grouping: true, // 相同消息合并显示
}

/**
 * 显示成功消息
 */
export function showSuccess(message: string): void {
  ElMessage.success({
    ...defaultOptions,
    message,
  })
}

/**
 * 显示警告消息
 */
export function showWarning(message: string): void {
  ElMessage.warning({
    ...defaultOptions,
    message,
  })
}

/**
 * 显示错误消息
 */
export function showError(message: string): void {
  ElMessage.error({
    ...defaultOptions,
    message,
    duration: 5000, // 错误消息显示时间更长
  })
}

/**
 * 显示信息消息
 */
export function showInfo(message: string): void {
  ElMessage.info({
    ...defaultOptions,
    message,
  })
}

/**
 * 显示加载中消息
 * @returns 返回关闭函数
 */
export function showLoading(message: string = '加载中...'): () => void {
  const instance = ElMessage({
    ...defaultOptions,
    message,
    type: 'info',
    duration: 0, // 不自动关闭
    iconClass: 'el-icon-loading',
  })
  return () => instance.close()
}

/**
 * 显示操作成功提示
 */
export function showSaveSuccess(): void {
  showSuccess('保存成功')
}

/**
 * 显示删除成功提示
 */
export function showDeleteSuccess(): void {
  showSuccess('删除成功')
}

/**
 * 显示操作失败提示
 */
export function showOperationError(operation: string = '操作'): void {
  showError(`${operation}失败，请稍后重试`)
}

/**
 * 显示网络错误提示
 */
export function showNetworkError(): void {
  showError('网络连接失败，请检查网络后重试')
}

export default {
  success: showSuccess,
  warning: showWarning,
  error: showError,
  info: showInfo,
  loading: showLoading,
  saveSuccess: showSaveSuccess,
  deleteSuccess: showDeleteSuccess,
  operationError: showOperationError,
  networkError: showNetworkError,
}
