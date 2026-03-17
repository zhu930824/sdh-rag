# Tasks

## Phase 1: 基础设施迁移

- [x] Task 1: 更新项目依赖配置
  - [x] SubTask 1.1: 移除 Element Plus 相关依赖（element-plus, @element-plus/icons-vue）
  - [x] SubTask 1.2: 添加 Ant Design Vue 4.x 依赖（ant-design-vue, @ant-design/icons-vue）
  - [x] SubTask 1.3: 更新 Vite 配置中的自动导入解析器（从 ElementPlusResolver 改为 AntDesignVueResolver）
  - [x] SubTask 1.4: 更新 package.json 的构建配置

- [x] Task 2: 更新入口文件和全局配置
  - [x] SubTask 2.1: 更新 main.ts，移除 Element Plus 注册，添加 Ant Design Vue 注册
  - [x] SubTask 2.2: 配置 Ant Design Vue 中文语言包
  - [x] SubTask 2.3: 注册 Ant Design Vue 图标组件
  - [x] SubTask 2.4: 配置主题和设计令牌

- [x] Task 3: 更新全局样式系统
  - [x] SubTask 3.1: 更新 CSS 变量以适配 Ant Design 设计系统
  - [x] SubTask 3.2: 配置科技感配色方案
  - [x] SubTask 3.3: 更新深色模式样式变量
  - [x] SubTask 3.4: 移除 Element Plus 样式覆盖，添加 Ant Design Vue 样式覆盖

## Phase 2: 工具函数迁移

- [x] Task 4: 迁移消息提示工具
  - [x] SubTask 4.1: 更新 message.ts，使用 Ant Design Vue 的 message API
  - [x] SubTask 4.2: 保持现有 API 接口不变

- [x] Task 5: 迁移确认对话框工具
  - [x] SubTask 5.1: 更新 confirm.ts，使用 Ant Design Vue 的 Modal.confirm API
  - [x] SubTask 5.2: 保持现有 API 接口不变

- [x] Task 6: 迁移表单工具
  - [x] SubTask 6.1: 更新 form.ts，适配 Ant Design Vue 表单验证规则

## Phase 3: 通用组件迁移

- [x] Task 7: 迁移 EmptyState 组件
  - [x] SubTask 7.1: 使用 a-empty 组件重新实现
  - [x] SubTask 7.2: 保持现有 props 和插槽

- [x] Task 8: 迁移 Loading 组件
  - [x] SubTask 8.1: 使用 a-spin 组件重新实现
  - [x] SubTask 8.2: 保持现有样式效果

- [x] Task 9: 迁移 Skeleton 组件
  - [x] SubTask 9.1: 使用 a-skeleton 组件重新实现
  - [x] SubTask 9.2: 保持现有样式效果

- [x] Task 10: 迁移 GlobalSearch 组件
  - [x] SubTask 10.1: 使用 a-input 组件重新实现
  - [x] SubTask 10.2: 保持搜索功能

- [x] Task 11: 迁移 TableToolbar 组件
  - [x] SubTask 11.1: 使用 a-space、a-dropdown 等组件重新实现
  - [x] SubTask 11.2: 保持列配置和密度切换功能

## Phase 4: 布局组件迁移

- [x] Task 12: 迁移 Sidebar 组件
  - [x] SubTask 12.1: 使用 a-layout-sider 替代 el-aside
  - [x] SubTask 12.2: 使用 a-menu 替代 el-menu
  - [x] SubTask 12.3: 使用 a-drawer 实现移动端抽屉模式
  - [x] SubTask 12.4: 迁移图标到 @ant-design/icons-vue
  - [x] SubTask 12.5: 保持折叠/展开功能

- [x] Task 13: 迁移 Header 组件
  - [x] SubTask 13.1: 使用 a-layout-header 替代 el-header
  - [x] SubTask 13.2: 使用 a-dropdown 替代 el-dropdown
  - [x] SubTask 13.3: 使用 a-input 替代 el-input
  - [x] SubTask 13.4: 使用 a-tooltip 替代 el-tooltip
  - [x] SubTask 13.5: 使用 a-avatar 替代 el-avatar
  - [x] SubTask 13.6: 迁移图标到 @ant-design/icons-vue

- [x] Task 14: 迁移 Breadcrumb 组件
  - [x] SubTask 14.1: 使用 a-breadcrumb 替代 el-breadcrumb
  - [x] SubTask 14.2: 保持路由导航功能

- [x] Task 15: 迁移 Tabs 组件
  - [x] SubTask 15.1: 使用 a-tabs 替代 el-tabs
  - [x] SubTask 15.2: 保持标签页管理功能

- [x] Task 16: 迁移 MainLayout 组件
  - [x] SubTask 16.1: 使用 a-layout 替代 el-container
  - [x] SubTask 16.2: 使用 a-layout-content 替代 el-main
  - [x] SubTask 16.3: 保持页面过渡动画

## Phase 5: 登录页面迁移（保持设计）

- [x] Task 17: 迁移登录页面
  - [x] SubTask 17.1: 使用 a-form 替代 el-form，保持表单验证
  - [x] SubTask 17.2: 使用 a-input 替代 el-input，保持样式
  - [x] SubTask 17.3: 使用 a-button 替代 el-button，保持渐变样式
  - [x] SubTask 17.4: 使用 a-checkbox 替代 el-checkbox
  - [x] SubTask 17.5: 使用 a-switch 替代 el-switch（主题切换）
  - [x] SubTask 17.6: 使用 a-tooltip 替代 el-tooltip
  - [x] SubTask 17.7: 使用 a-divider 替代 el-divider
  - [x] SubTask 17.8: 迁移图标到 @ant-design/icons-vue
  - [x] SubTask 17.9: 保持粒子背景效果
  - [x] SubTask 17.10: 保持品牌区域设计

- [x] Task 18: 迁移注册页面
  - [x] SubTask 18.1: 使用 a-form 替代 el-form
  - [x] SubTask 18.2: 使用 a-input 替代 el-input
  - [x] SubTask 18.3: 使用 a-button 替代 el-button
  - [x] SubTask 18.4: 迁移图标到 @ant-design/icons-vue

## Phase 6: 知识库管理页面迁移

- [x] Task 19: 迁移知识库列表页面
  - [x] SubTask 19.1: 使用 a-card 替代 el-card
  - [x] SubTask 19.2: 使用 a-tree 替代 el-tree（分类树）
  - [x] SubTask 19.3: 使用 a-input 替代 el-input（搜索框）
  - [x] SubTask 19.4: 使用 a-button 替代 el-button
  - [x] SubTask 19.5: 使用 a-empty 替代 el-empty
  - [x] SubTask 19.6: 使用 a-tag 替代 el-tag
  - [x] SubTask 19.7: 使用 a-pagination 替代 el-pagination
  - [x] SubTask 19.8: 使用 a-drawer 替代 el-drawer
  - [x] SubTask 19.9: 迁移图标到 @ant-design/icons-vue

- [x] Task 20: 迁移上传对话框组件
  - [x] SubTask 20.1: 使用 a-modal 替代 el-dialog
  - [x] SubTask 20.2: 使用 a-form 替代 el-form
  - [x] SubTask 20.3: 使用 a-select 替代 el-select
  - [x] SubTask 20.4: 使用 a-upload-dragger 替代 el-upload
  - [x] SubTask 20.5: 使用 a-progress 替代 el-progress

- [x] Task 21: 迁移分类管理组件
  - [x] SubTask 21.1: 使用 a-modal 替代 el-dialog
  - [x] SubTask 21.2: 使用 a-form 替代 el-form
  - [x] SubTask 21.3: 使用 a-input 替代 el-input
  - [x] SubTask 21.4: 使用 a-tree 替代 el-tree

- [x] Task 22: 迁移文档详情页面
  - [x] SubTask 22.1: 使用 a-descriptions 替代 el-descriptions
  - [x] SubTask 22.2: 使用 a-tag 替代 el-tag
  - [x] SubTask 22.3: 使用 a-button 替代 el-button

## Phase 7: 智能问答页面迁移

- [x] Task 23: 迁移会话列表组件
  - [x] SubTask 23.1: 使用 a-list 替代自定义列表
  - [x] SubTask 23.2: 使用 a-button 替代 el-button
  - [x] SubTask 23.3: 使用 a-empty 替代 el-empty
  - [x] SubTask 23.4: 迁移图标到 @ant-design/icons-vue

- [x] Task 24: 迁移消息列表组件
  - [x] SubTask 24.1: 使用 a-list 替代自定义列表
  - [x] SubTask 24.2: 使用 a-card 替代消息卡片
  - [x] SubTask 24.3: 使用 a-spin 替代 el-icon 加载状态
  - [x] SubTask 24.4: 保持流式输出功能

- [x] Task 25: 迁移输入框组件
  - [x] SubTask 25.1: 使用 a-textarea 替代 el-input
  - [x] SubTask 25.2: 使用 a-button 替代 el-button
  - [x] SubTask 25.3: 使用 a-tooltip 替代 el-tooltip

- [x] Task 26: 迁移来源卡片组件
  - [x] SubTask 26.1: 使用 a-card 替代 el-card
  - [x] SubTask 26.2: 使用 a-progress 替代 el-progress

- [x] Task 27: 迁移聊天主页面
  - [x] SubTask 27.1: 使用 a-drawer 替代 el-drawer
  - [x] SubTask 27.2: 使用 a-button 替代 el-button
  - [x] SubTask 27.3: 使用 a-progress 替代 el-progress
  - [x] SubTask 27.4: 迁移图标到 @ant-design/icons-vue

## Phase 8: 仪表盘页面迁移

- [x] Task 28: 迁移仪表盘页面
  - [x] SubTask 28.1: 使用 a-row 和 a-col 替代 el-row 和 el-col
  - [x] SubTask 28.2: 使用 a-card 替代 el-card
  - [x] SubTask 28.3: 使用 a-statistic 替代自定义统计组件
  - [x] SubTask 28.4: 使用 a-tag 替代 el-tag
  - [x] SubTask 28.5: 使用 a-descriptions 替代 el-descriptions
  - [x] SubTask 28.6: 使用 a-empty 替代 el-empty
  - [x] SubTask 28.7: 使用 a-link 替代 el-link
  - [x] SubTask 28.8: 迁移图标到 @ant-design/icons-vue
  - [x] SubTask 28.9: 保持数字递增动画效果

## Phase 9: 用户管理页面迁移

- [x] Task 29: 迁移用户管理页面
  - [x] SubTask 29.1: 使用 a-card 替代 el-card
  - [x] SubTask 29.2: 使用 a-form 替代 el-form（搜索表单）
  - [x] SubTask 29.3: 使用 a-input 替代 el-input
  - [x] SubTask 29.4: 使用 a-select 替代 el-select
  - [x] SubTask 29.5: 使用 a-button 替代 el-button
  - [x] SubTask 29.6: 使用 a-table 替代 el-table
  - [x] SubTask 29.7: 使用 a-switch 替代 el-switch
  - [x] SubTask 29.8: 使用 a-tag 替代 el-tag
  - [x] SubTask 29.9: 使用 a-pagination 替代 el-pagination
  - [x] SubTask 29.10: 使用 a-modal 替代 el-dialog
  - [x] SubTask 29.11: 使用 a-radio 替代 el-radio
  - [x] SubTask 29.12: 迁移图标到 @ant-design/icons-vue

## Phase 10: 其他页面迁移

- [x] Task 30: 迁移个人中心页面
  - [x] SubTask 30.1: 使用 a-card 替代 el-card
  - [x] SubTask 30.2: 使用 a-form 替代 el-form
  - [x] SubTask 30.3: 使用 a-avatar 替代 el-avatar
  - [x] SubTask 30.4: 使用 a-upload 替代 el-upload
  - [x] SubTask 30.5: 迁移图标到 @ant-design/icons-vue

- [x] Task 31: 迁移系统设置页面
  - [x] SubTask 31.1: 使用 a-card 替代 el-card
  - [x] SubTask 31.2: 使用 a-form 替代 el-form
  - [x] SubTask 31.3: 使用 a-switch 替代 el-switch
  - [x] SubTask 31.4: 使用 a-select 替代 el-select
  - [x] SubTask 31.5: 迁移图标到 @ant-design/icons-vue

- [x] Task 32: 迁移 404 错误页面
  - [x] SubTask 32.1: 使用 a-result 替代 el-result
  - [x] SubTask 32.2: 使用 a-button 替代 el-button

## Phase 11: 验证和优化

- [ ] Task 33: 功能验证
  - [ ] SubTask 33.1: 验证用户登录/注册流程
  - [ ] SubTask 33.2: 验证知识库管理功能
  - [ ] SubTask 33.3: 验证智能问答功能
  - [ ] SubTask 33.4: 验证用户管理功能
  - [ ] SubTask 33.5: 验证深色模式切换

- [ ] Task 34: 响应式验证
  - [ ] SubTask 34.1: 验证桌面端布局
  - [ ] SubTask 34.2: 验证平板端布局
  - [ ] SubTask 34.3: 验证移动端布局

- [ ] Task 35: 性能优化
  - [ ] SubTask 35.1: 优化组件按需加载
  - [ ] SubTask 35.2: 优化图标按需加载
  - [ ] SubTask 35.3: 检查构建产物大小

# Task Dependencies
- Task 2 依赖 Task 1
- Task 3 依赖 Task 2
- Task 4, Task 5, Task 6 依赖 Task 2
- Task 7-11 依赖 Task 2, Task 3
- Task 12-16 依赖 Task 2, Task 3
- Task 17, Task 18 依赖 Task 2, Task 3
- Task 19-22 依赖 Task 2, Task 3, Task 12-16
- Task 23-27 依赖 Task 2, Task 3, Task 12-16
- Task 28 依赖 Task 2, Task 3, Task 12-16
- Task 29 依赖 Task 2, Task 3, Task 12-16
- Task 30-32 依赖 Task 2, Task 3, Task 12-16
- Task 33-35 依赖所有前置任务完成
