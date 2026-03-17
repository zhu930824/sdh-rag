# Checklist

## 基础设施迁移
- [x] Element Plus 依赖已移除
- [x] Ant Design Vue 4.x 依赖已安装
- [x] @ant-design/icons-vue 依赖已安装
- [x] Vite 配置已更新（自动导入解析器）
- [x] main.ts 入口文件已更新
- [x] 中文语言包已配置
- [x] 图标组件已正确注册
- [x] 主题配置已完成

## 全局样式系统
- [x] CSS 变量已更新为 Ant Design 设计系统
- [x] 科技感配色方案已应用
- [x] 深色模式样式变量已配置
- [x] Element Plus 样式覆盖已移除
- [x] Ant Design Vue 样式覆盖已添加

## 工具函数迁移
- [x] message.ts 已迁移到 Ant Design Vue message API
- [x] confirm.ts 已迁移到 Ant Design Vue Modal.confirm API
- [x] form.ts 已适配 Ant Design Vue 表单验证

## 通用组件迁移
- [x] EmptyState 组件已迁移
- [x] Loading 组件已迁移
- [x] Skeleton 组件已迁移
- [x] GlobalSearch 组件已迁移
- [x] TableToolbar 组件已迁移

## 布局组件迁移
- [x] Sidebar 组件已迁移（a-layout-sider, a-menu, a-drawer）
- [x] Header 组件已迁移（a-layout-header, a-dropdown, a-avatar）
- [x] Breadcrumb 组件已迁移（a-breadcrumb）
- [x] Tabs 组件已迁移（a-tabs）
- [x] MainLayout 组件已迁移（a-layout）
- [x] 所有图标已迁移到 @ant-design/icons-vue

## 登录页面迁移
- [x] 登录表单已迁移（a-form, a-input, a-button）
- [x] 粒子背景效果保持正常
- [x] 品牌区域设计保持正常
- [x] 主题切换功能正常
- [x] 表单验证功能正常
- [x] 记住密码功能正常
- [x] 注册页面已迁移

## 知识库管理页面迁移
- [x] 分类树已迁移（a-tree）
- [x] 文档列表已迁移（a-card）
- [x] 搜索功能正常
- [x] 分页功能正常（a-pagination）
- [x] 上传对话框已迁移（a-modal, a-upload-dragger）
- [x] 分类管理已迁移
- [x] 文档详情抽屉已迁移（a-drawer）
- [x] 所有图标已迁移

## 智能问答页面迁移
- [x] 会话列表已迁移（a-list）
- [x] 消息列表已迁移
- [x] 流式输出功能正常
- [x] 输入框组件已迁移（a-textarea）
- [x] 来源卡片已迁移（a-card, a-progress）
- [x] 来源详情抽屉已迁移（a-drawer）
- [x] 所有图标已迁移

## 仪表盘页面迁移
- [x] 栅格布局已迁移（a-row, a-col）
- [x] 统计卡片已迁移（a-card, a-statistic）
- [x] 快捷操作已迁移
- [x] 最近文档列表已迁移
- [x] 最近问答列表已迁移
- [x] 系统信息已迁移（a-descriptions）
- [x] 数字递增动画效果正常
- [x] 所有图标已迁移

## 用户管理页面迁移
- [x] 搜索表单已迁移（a-form, a-input, a-select）
- [x] 用户表格已迁移（a-table）
- [x] 表格排序、筛选功能正常
- [x] 表格分页功能正常（a-pagination）
- [x] 表格行选择功能正常
- [x] 状态开关已迁移（a-switch）
- [x] 用户编辑对话框已迁移（a-modal, a-form）
- [x] 所有图标已迁移

## 其他页面迁移
- [x] 个人中心页面已迁移
- [x] 系统设置页面已迁移
- [x] 404 错误页面已迁移（a-result）

## 响应式设计验证
- [ ] 桌面端布局正常
- [ ] 平板端布局正常
- [ ] 移动端布局正常
- [ ] 移动端抽屉菜单正常

## 深色模式验证
- [ ] 深色模式切换正常
- [ ] 所有组件深色模式样式正确
- [ ] 主题状态持久化正常

## 功能完整性验证
- [ ] 用户登录/注册流程正常
- [ ] 知识库管理功能正常
- [ ] 文档上传功能正常
- [ ] 智能问答功能正常
- [ ] 用户管理功能正常
- [ ] 个人中心功能正常
- [ ] 系统设置功能正常

## 性能验证
- [ ] 组件按需加载正常
- [ ] 图标按需加载正常
- [ ] 构建产物大小合理
- [ ] 页面加载速度正常
