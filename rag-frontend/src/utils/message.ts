import { message, type MessageArgsProps } from 'ant-design-vue'

/**
 * 消息提示配置
 */
const defaultOptions: Partial<MessageArgsProps> = {
  duration: 3,
}

/**
 * 显示成功消息
 */
export function showSuccess(content: string): void {
  message.success({
    ...defaultOptions,
    content,
  })
}

/**
 * 显示警告消息
 */
export function showWarning(content: string): void {
  message.warning({
    ...defaultOptions,
    content,
  })
}

/**
 * 显示错误消息
 */
export function showError(content: string): void {
  message.error({
    ...defaultOptions,
    content,
    duration: 5,
  })
}

/**
 * 显示信息消息
 */
export function showInfo(content: string): void {
  message.info({
    ...defaultOptions,
    content,
  })
}

/**
 * 显示加载中消息
 * @returns 返回关闭函数
 */
export function showLoading(content: string = '加载中...'): () => void {
  const hide = message.loading({
    ...defaultOptions,
    content,
    duration: 0,
  })
  return hide
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
