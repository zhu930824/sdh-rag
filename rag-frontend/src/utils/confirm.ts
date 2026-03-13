import { ElMessageBox, type MessageBoxData } from 'element-plus'

/**
 * 确认对话框配置接口
 */
interface ConfirmOptions {
  title?: string
  message?: string
  confirmText?: string
  cancelText?: string
  type?: 'warning' | 'info' | 'success' | 'error'
}

/**
 * 显示确认对话框
 */
export async function showConfirm(options: ConfirmOptions): Promise<boolean> {
  const { title = '提示', message, confirmText = '确定', cancelText = '取消', type = 'warning' } = options

  try {
    await ElMessageBox.confirm(message || '', title, {
      confirmButtonText: confirmText,
      cancelButtonText: cancelText,
      type,
      closeOnClickModal: false, // 点击遮罩不关闭
      closeOnPressEscape: true, // 按 ESC 关闭
    })
    return true
  } catch {
    return false
  }
}

/**
 * 显示删除确认对话框
 */
export async function showDeleteConfirm(itemName: string = '该数据'): Promise<boolean> {
  return showConfirm({
    title: '删除确认',
    message: `确定要删除${itemName}吗？删除后将无法恢复。`,
    confirmText: '删除',
    type: 'warning',
  })
}

/**
 * 显示批量删除确认对话框
 */
export async function showBatchDeleteConfirm(count: number): Promise<boolean> {
  return showConfirm({
    title: '批量删除确认',
    message: `确定要删除选中的 ${count} 条数据吗？删除后将无法恢复。`,
    confirmText: '删除',
    type: 'warning',
  })
}

/**
 * 显示操作确认对话框
 */
export async function showOperationConfirm(operation: string, message?: string): Promise<boolean> {
  return showConfirm({
    title: `${operation}确认`,
    message: message || `确定要执行${operation}操作吗？`,
    confirmText: operation,
    type: 'info',
  })
}

/**
 * 显示状态切换确认对话框
 */
export async function showStatusChangeConfirm(status: '启用' | '禁用'): Promise<boolean> {
  return showConfirm({
    title: '状态切换确认',
    message: `确定要${status}该用户吗？`,
    confirmText: status,
    type: 'info',
  })
}

/**
 * 显示重置密码确认对话框
 */
export async function showResetPasswordConfirm(): Promise<boolean> {
  return showConfirm({
    title: '重置密码确认',
    message: '确定要重置该用户的密码吗？重置后密码将变为默认密码。',
    confirmText: '重置',
    type: 'warning',
  })
}

/**
 * 显示提示对话框（只有确定按钮）
 */
export async function showAlert(message: string, title: string = '提示'): Promise<void> {
  await ElMessageBox.alert(message, title, {
    confirmButtonText: '确定',
    type: 'info',
  })
}

/**
 * 显示输入对话框
 */
export async function showPrompt(
  message: string,
  title: string = '请输入',
  inputPattern?: RegExp,
  inputErrorMessage?: string
): Promise<string | null> {
  try {
    const result: MessageBoxData = await ElMessageBox.prompt(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern,
      inputErrorMessage: inputErrorMessage || '输入格式不正确',
      closeOnClickModal: false,
    })
    return result.value
  } catch {
    return null
  }
}

export default {
  confirm: showConfirm,
  delete: showDeleteConfirm,
  batchDelete: showBatchDeleteConfirm,
  operation: showOperationConfirm,
  statusChange: showStatusChangeConfirm,
  resetPassword: showResetPasswordConfirm,
  alert: showAlert,
  prompt: showPrompt,
}
