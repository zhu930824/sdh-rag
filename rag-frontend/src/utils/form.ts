import type { Rule } from 'ant-design-vue/es/form'

/**
 * 创建必填规则
 */
export function createRequiredRule(message: string, trigger: string | string[] = 'blur'): Rule {
  return {
    required: true,
    message,
    trigger,
  }
}

/**
 * 创建长度规则
 */
export function createLengthRule(min: number, max: number, message?: string): Rule {
  return {
    min,
    max,
    message: message || `长度在 ${min} 到 ${max} 个字符`,
    trigger: 'blur',
  }
}

/**
 * 创建正则规则
 */
export function createPatternRule(pattern: RegExp, message: string): Rule {
  return {
    pattern,
    message,
    trigger: 'blur',
  }
}

/**
 * 创建邮箱规则
 */
export function createEmailRule(message: string = '请输入正确的邮箱地址'): Rule {
  return {
    type: 'email',
    message,
    trigger: 'blur',
  }
}

/**
 * 创建手机号规则
 */
export function createPhoneRule(message: string = '请输入正确的手机号'): Rule {
  return {
    pattern: /^1[3-9]\d{9}$/,
    message,
    trigger: 'blur',
  }
}

/**
 * 创建密码规则
 */
export function createPasswordRule(min: number = 6, max: number = 20): Rule[] {
  return [
    createRequiredRule('请输入密码'),
    createLengthRule(min, max, `密码长度在 ${min} 到 ${max} 个字符`),
  ]
}

/**
 * 创建用户名规则
 */
export function createUsernameRule(min: number = 3, max: number = 20): Rule[] {
  return [
    createRequiredRule('请输入用户名'),
    createLengthRule(min, max, `用户名长度在 ${min} 到 ${max} 个字符`),
    createPatternRule(/^[a-zA-Z0-9_]+$/, '用户名只能包含字母、数字和下划线'),
  ]
}

export default {
  required: createRequiredRule,
  length: createLengthRule,
  pattern: createPatternRule,
  email: createEmailRule,
  phone: createPhoneRule,
  password: createPasswordRule,
  username: createUsernameRule,
}
