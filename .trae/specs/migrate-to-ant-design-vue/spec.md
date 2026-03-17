# UI 组件库迁移 Spec - Element Plus 到 Ant Design Vue

## Why
当前项目使用 Element Plus 作为 UI 组件库，需要迁移到 Ant Design Vue 以实现更现代、科技感的整体设计风格。登录页面需要保持现有设计不变。

## What Changes
- **BREAKING**: 将 Element Plus 完全替换为 Ant Design Vue 4.x
- 更新所有组件导入和使用方式
- 更新图标库从 @element-plus/icons-vue 到 @ant-design/icons-vue
- 更新全局样式和 CSS 变量以适配 Ant Design 设计系统
- 更新 Vite 配置中的自动导入解析器
- 保持登录页现有设计风格不变

## Impact
- Affected specs: 前端所有页面和组件
- Affected code: 
  - `rag-frontend/src/main.ts` - 入口文件
  - `rag-frontend/vite.config.ts` - 构建配置
  - `rag-frontend/package.json` - 依赖配置
  - `rag-frontend/src/styles/index.scss` - 全局样式
  - `rag-frontend/src/views/**/*.vue` - 所有页面组件
  - `rag-frontend/src/components/**/*.vue` - 所有通用组件
  - `rag-frontend/src/layouts/**/*.vue` - 所有布局组件
  - `rag-frontend/src/utils/message.ts` - 消息提示工具
  - `rag-frontend/src/utils/confirm.ts` - 确认对话框工具

## ADDED Requirements

### Requirement: Ant Design Vue 组件库集成
系统 SHALL 使用 Ant Design Vue 4.x 作为主要 UI 组件库，提供科技感、现代、有设计感的界面风格。

#### Scenario: 组件库安装和配置
- **WHEN** 项目初始化时
- **THEN** 应正确安装 ant-design-vue@4.x 和 @ant-design/icons-vue
- **AND** 应正确配置 Vite 自动导入解析器
- **AND** 应正确注册组件库和图标

#### Scenario: 主题配置
- **WHEN** 应用启动时
- **THEN** 应加载 Ant Design Vue 主题配置
- **AND** 应支持深色模式切换
- **AND** 应使用科技感的配色方案

### Requirement: 登录页面保持现有设计
登录页面 SHALL 保持现有的视觉设计和交互效果，仅替换底层组件实现。

#### Scenario: 登录页面迁移
- **WHEN** 迁移登录页面时
- **THEN** 应保持现有的粒子背景效果
- **AND** 应保持现有的品牌区域设计
- **AND** 应保持现有的表单布局和样式
- **AND** 应使用 Ant Design Vue 的 Form、Input、Button 组件替代 Element Plus 组件

### Requirement: 主布局组件迁移
主布局 SHALL 使用 Ant Design Vue 的 Layout 组件实现，保持响应式设计。

#### Scenario: 侧边栏迁移
- **WHEN** 迁移侧边栏组件时
- **THEN** 应使用 a-layout-sider 组件
- **AND** 应使用 a-menu 组件实现导航菜单
- **AND** 应保持折叠/展开功能
- **AND** 应保持移动端抽屉模式

#### Scenario: 头部导航迁移
- **WHEN** 迁移头部组件时
- **THEN** 应使用 a-layout-header 组件
- **AND** 应使用 a-dropdown 组件实现用户菜单
- **AND** 应保持深色模式切换功能

### Requirement: 知识库管理页面迁移
知识库管理页面 SHALL 使用 Ant Design Vue 组件实现，保持现有功能。

#### Scenario: 文档列表展示
- **WHEN** 展示文档列表时
- **THEN** 应使用 a-card 组件展示文档卡片
- **AND** 应使用 a-tree 组件展示分类树
- **AND** 应使用 a-pagination 组件实现分页
- **AND** 应使用 a-drawer 组件展示文档详情

#### Scenario: 文档上传
- **WHEN** 上传文档时
- **THEN** 应使用 a-modal 组件展示上传对话框
- **AND** 应使用 a-upload-dragger 组件实现拖拽上传

### Requirement: 智能问答页面迁移
智能问答页面 SHALL 使用 Ant Design Vue 组件实现，保持流式输出功能。

#### Scenario: 消息列表展示
- **WHEN** 展示消息列表时
- **THEN** 应使用 a-list 组件展示消息
- **AND** 应支持流式输出显示
- **AND** 应支持引用来源展示

#### Scenario: 会话管理
- **WHEN** 管理会话时
- **THEN** 应使用 a-list 组件展示会话列表
- **AND** 应支持新建、删除会话
- **AND** 应支持会话切换

### Requirement: 仪表盘页面迁移
仪表盘页面 SHALL 使用 Ant Design Vue 组件实现，保持数据可视化效果。

#### Scenario: 统计卡片展示
- **WHEN** 展示统计数据时
- **THEN** 应使用 a-card 组件展示统计卡片
- **AND** 应使用 a-row 和 a-col 组件实现栅格布局
- **AND** 应保持数字递增动画效果

### Requirement: 用户管理页面迁移
用户管理页面 SHALL 使用 Ant Design Vue 组件实现，保持 CRUD 功能。

#### Scenario: 用户表格展示
- **WHEN** 展示用户列表时
- **THEN** 应使用 a-table 组件
- **AND** 应支持排序、筛选、分页
- **AND** 应支持行选择和批量操作

#### Scenario: 用户表单
- **WHEN** 编辑用户信息时
- **THEN** 应使用 a-modal 组件展示表单对话框
- **AND** 应使用 a-form 组件实现表单验证

### Requirement: 通用组件迁移
通用组件 SHALL 使用 Ant Design Vue 基础组件重新实现。

#### Scenario: 消息提示工具
- **WHEN** 显示消息提示时
- **THEN** 应使用 message 全局方法
- **AND** 应保持现有的 API 接口

#### Scenario: 确认对话框
- **WHEN** 显示确认对话框时
- **THEN** 应使用 Modal.confirm 全局方法
- **AND** 应保持现有的 API 接口

### Requirement: 响应式设计保持
迁移后的应用 SHALL 保持响应式设计，支持桌面端和移动端。

#### Scenario: 移动端适配
- **WHEN** 在移动设备上访问时
- **THEN** 应正确显示移动端布局
- **AND** 应支持触摸操作
- **AND** 应优化交互体验

### Requirement: 深色模式支持
迁移后的应用 SHALL 支持深色模式切换。

#### Scenario: 主题切换
- **WHEN** 用户切换主题时
- **THEN** 应正确切换所有组件的主题
- **AND** 应保持主题状态持久化
- **AND** 应平滑过渡主题变化

## MODIFIED Requirements

### Requirement: 全局样式系统
全局样式 SHALL 基于 Ant Design Vue 的设计令牌系统，使用科技感的配色方案。

- 主色调：使用科技蓝 (#1890ff) 作为主色
- 渐变色：使用蓝紫渐变作为强调色
- 深色模式：使用 Ant Design Vue 的暗色主题配置

## REMOVED Requirements

### Requirement: Element Plus 相关依赖
**Reason**: 完全迁移到 Ant Design Vue
**Migration**: 
- 移除 element-plus 依赖
- 移除 @element-plus/icons-vue 依赖
- 移除 Element Plus 相关的自动导入配置
