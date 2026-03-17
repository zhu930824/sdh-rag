# UI 优化和功能扩展 Spec

## Why
用户反馈了多个 UI 问题需要修复，同时需要增加新的管理功能模块。

## What Changes
- 移除顶部标签页切换组件
- 修复左侧菜单点击无响应的问题
- 优化首页仪表盘的左右间距
- 新增日志管理、敏感词管理、热点词分析三个功能模块

## Impact
- Affected specs: 布局组件、路由配置、新增页面
- Affected code: 
  - `rag-frontend/src/layouts/MainLayout.vue` - 移除 Tabs 组件
  - `rag-frontend/src/layouts/components/Sidebar.vue` - 修复菜单点击事件
  - `rag-frontend/src/views/dashboard/index.vue` - 优化间距样式
  - `rag-frontend/src/router/index.ts` - 新增路由配置
  - `rag-frontend/src/views/log/index.vue` - 新增日志管理页面
  - `rag-frontend/src/views/sensitive/index.vue` - 新增敏感词管理页面
  - `rag-frontend/src/views/hotwords/index.vue` - 新增热点词分析页面

## ADDED Requirements

### Requirement: 移除标签页切换组件
系统 SHALL 移除顶部的标签页切换组件，简化界面布局。

#### Scenario: 移除 Tabs 组件
- **WHEN** 用户访问系统时
- **THEN** 不应显示顶部的标签页切换栏
- **AND** 页面内容区域应直接显示在头部下方

### Requirement: 修复左侧菜单点击问题
系统 SHALL 确保左侧菜单点击能正常响应并跳转到对应页面。

#### Scenario: 桌面端菜单点击
- **WHEN** 用户在桌面端点击左侧菜单项时
- **THEN** 应正确跳转到对应页面
- **AND** 菜单项应显示选中状态

#### Scenario: 移动端菜单点击
- **WHEN** 用户在移动端点击抽屉菜单项时
- **THEN** 应正确跳转到对应页面
- **AND** 抽屉应自动关闭

### Requirement: 优化首页仪表盘布局
系统 SHALL 优化首页仪表盘的左右间距，提升视觉效果。

#### Scenario: 仪表盘间距优化
- **WHEN** 用户访问首页仪表盘时
- **THEN** 左右间距应适中，不应过大
- **AND** 内容应更好地利用屏幕空间
- **AND** 响应式布局应保持良好

### Requirement: 日志管理功能
系统 SHALL 提供日志管理功能，支持查看系统操作日志。

#### Scenario: 日志列表展示
- **WHEN** 用户访问日志管理页面时
- **THEN** 应显示日志列表，包含时间、用户、操作类型、操作内容等字段
- **AND** 应支持按时间范围筛选
- **AND** 应支持按操作类型筛选
- **AND** 应支持分页显示

#### Scenario: 日志详情查看
- **WHEN** 用户点击某条日志时
- **THEN** 应显示日志详细信息
- **AND** 应显示请求参数和响应结果

### Requirement: 敏感词管理功能
系统 SHALL 提供敏感词管理功能，支持添加、编辑、删除敏感词。

#### Scenario: 敏感词列表展示
- **WHEN** 用户访问敏感词管理页面时
- **THEN** 应显示敏感词列表，包含词汇、分类、状态、创建时间等字段
- **AND** 应支持搜索功能
- **AND** 应支持分页显示

#### Scenario: 敏感词添加
- **WHEN** 用户点击添加敏感词时
- **THEN** 应显示添加表单
- **AND** 应支持输入词汇、选择分类
- **AND** 应验证词汇是否已存在

#### Scenario: 敏感词编辑
- **WHEN** 用户编辑敏感词时
- **THEN** 应显示编辑表单
- **AND** 应保存修改后的数据

#### Scenario: 敏感词删除
- **WHEN** 用户删除敏感词时
- **THEN** 应显示确认对话框
- **AND** 确认后应删除该敏感词

### Requirement: 热点词分析功能
系统 SHALL 提供热点词分析功能，展示用户查询的高频词汇。

#### Scenario: 热点词统计展示
- **WHEN** 用户访问热点词分析页面时
- **THEN** 应显示热点词统计图表
- **AND** 应显示热点词列表，包含词汇、查询次数、占比等字段
- **AND** 应支持按时间范围筛选

#### Scenario: 热点词趋势分析
- **WHEN** 用户查看热点词趋势时
- **THEN** 应显示趋势图表
- **AND** 应支持选择不同的时间维度（日、周、月）

## MODIFIED Requirements

### Requirement: 侧边栏菜单
侧边栏菜单 SHALL 包含以下菜单项：
- 首页
- 知识库
- 智能问答
- 用户管理
- 日志管理（新增）
- 敏感词管理（新增）
- 热点词分析（新增）
- 系统设置
