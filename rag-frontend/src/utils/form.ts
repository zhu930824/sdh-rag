import type { FormInstance, FormRules } from 'element-plus'

/**
 * 表单验证结果
 */
export interface ValidationResult {
  valid: boolean
  errors: Record<string, string[]>
}

/**
 * 验证表单
 * @param formRef 表单引用
 * @returns 验证结果
 */
export async function validateForm(formRef: FormInstance | undefined): Promise<boolean> {
  if (!formRef) return false

  try {
    await formRef.validate()
    return true
  } catch {
    return false
  }
}

/**
 * 验证表单字段
 * @param formRef 表单引用
 * @param props 字段名数组
 * @returns 验证结果
 */
export async function validateField(
  formRef: FormInstance | undefined,
  props: string | string[]
): Promise<boolean> {
  if (!formRef) return false

  try {
    await formRef.validateField(props)
    return true
  } catch {
    return false
  }
}

/**
 * 重置表单
 * @param formRef 表单引用
 */
export function resetForm(formRef: FormInstance | undefined): void {
  formRef?.resetFields()
}

/**
 * 清除表单验证
 * @param formRef 表单引用
 * @param props 字段名数组（可选）
 */
export function clearValidate(formRef: FormInstance | undefined, props?: string | string[]): void {
  formRef?.clearValidate(props)
}

/**
 * 常用验证规则
 */
export const FormRules = {
  // 必填
  required(message: string = '此项为必填项'): FormRules[string] {
    return { required: true, message, trigger: 'blur' }
  },

  // 用户名
  username(): FormRules[string][] {
    return [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
    ]
  },

  // 密码
  password(): FormRules[string][] {
    return [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' },
    ]
  },

  // 邮箱
  email(): FormRules[string][] {
    return [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
    ]
  },

  // 手机号
  phone(): FormRules[string][] {
    return [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
    ]
  },

  // URL
  url(): FormRules[string][] {
    return [
      { required: true, message: '请输入网址', trigger: 'blur' },
      { type: 'url', message: '请输入正确的网址', trigger: 'blur' },
    ]
  },

  // 数字范围
  numberRange(min: number, max: number): FormRules[string][] {
    return [
      { required: true, message: '请输入数值', trigger: 'blur' },
      { type: 'number', min, max, message: `数值范围在 ${min} 到 ${max} 之间`, trigger: 'blur' },
    ]
  },

  // 长度范围
  lengthRange(min: number, max: number, message?: string): FormRules[string][] {
    return [
      { required: true, message: '请输入内容', trigger: 'blur' },
      { min, max, message: message || `长度在 ${min} 到 ${max} 个字符`, trigger: 'blur' },
    ]
  },

  // 选择必填
  selectRequired(message: string = '请选择此项'): FormRules[string] {
    return { required: true, message, trigger: 'change' }
  },

  // 多选必填
  checkboxRequired(message: string = '请至少选择一项'): FormRules[string] {
    return { required: true, message, trigger: 'change', type: 'array' }
  },
}

export default {
  validateForm,
  validateField,
  resetForm,
  clearValidate,
  FormRules,
}
