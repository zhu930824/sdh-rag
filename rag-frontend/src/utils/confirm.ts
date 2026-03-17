import { Modal } from 'ant-design-vue'
import type { ModalFuncProps } from 'ant-design-vue'

/**
 * 默认确认对话框配置
 */
const defaultConfig: Partial<ModalFuncProps> = {
  okText: '确定',
  cancelText: '取消',
  centered: true,
}

/**
 * 显示删除确认对话框
 */
export async function showDeleteConfirm(name: string = '该数据'): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      ...defaultConfig,
      title: '删除确认',
      content: `确定要删除 ${name} 吗？删除后无法恢复`,
      okType: 'danger',
      okButtonProps: { danger: true },
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

/**
 * 显示批量删除确认对话框
 */
export async function showBatchDeleteConfirm(count: number): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      ...defaultConfig,
      title: '批量删除确认',
      content: `确定要删除选中的 ${count} 条数据吗？删除后无法恢复`,
      okType: 'danger',
      okButtonProps: { danger: true },
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

/**
 * 显示重置密码确认对话框
 */
export async function showResetPasswordConfirm(): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      ...defaultConfig,
      title: '重置密码确认',
      content: '确定要重置该用户的密码吗？重置后密码将变为随机生成的密码',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

/**
 * 显示通用确认对话框
 */
export async function showConfirm(
  title: string,
  content: string,
  options?: Partial<ModalFuncProps>
): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      ...defaultConfig,
      title,
      content,
      ...options,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

export default {
  deleteConfirm: showDeleteConfirm,
  batchDeleteConfirm: showBatchDeleteConfirm,
  resetPasswordConfirm: showResetPasswordConfirm,
  confirm: showConfirm,
}
