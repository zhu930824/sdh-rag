# Tasks

## Phase 1: UI 问题修复

- [x] Task 1: 移除标签页切换组件
  - [x] SubTask 1.1: 从 MainLayout.vue 中移除 Tabs 组件导入和使用
  - [x] SubTask 1.2: 清理相关的 store 状态（visitedTabs 等）

- [x] Task 2: 修复左侧菜单点击问题
  - [x] SubTask 2.1: 检查并修复 Sidebar.vue 中 a-menu 的点击事件处理
  - [x] SubTask 2.2: 确保桌面端菜单点击能正确跳转
  - [x] SubTask 2.3: 确保移动端抽屉菜单点击后能正确关闭

- [x] Task 3: 优化首页仪表盘布局
  - [x] SubTask 3.1: 调整 dashboard/index.vue 的左右间距
  - [x] SubTask 3.2: 优化 max-width 和 padding 设置
  - [x] SubTask 3.3: 确保响应式布局正常

## Phase 2: 新增功能模块

- [x] Task 4: 新增日志管理功能
  - [x] SubTask 4.1: 创建日志管理页面 views/log/index.vue
  - [x] SubTask 4.2: 实现日志列表展示（表格、分页）
  - [x] SubTask 4.3: 实现日志筛选功能（时间范围、操作类型）
  - [x] SubTask 4.4: 实现日志详情查看功能

- [x] Task 5: 新增敏感词管理功能
  - [x] SubTask 5.1: 创建敏感词管理页面 views/sensitive/index.vue
  - [x] SubTask 5.2: 实现敏感词列表展示（表格、分页、搜索）
  - [x] SubTask 5.3: 实现敏感词添加功能（对话框表单）
  - [x] SubTask 5.4: 实现敏感词编辑功能
  - [x] SubTask 5.5: 实现敏感词删除功能（确认对话框）

- [x] Task 6: 新增热点词分析功能
  - [x] SubTask 6.1: 创建热点词分析页面 views/hotwords/index.vue
  - [x] SubTask 6.2: 实现热点词统计图表展示
  - [x] SubTask 6.3: 实现热点词列表展示
  - [x] SubTask 6.4: 实现时间范围筛选功能

## Phase 3: 路由和菜单配置

- [x] Task 7: 更新路由配置
  - [x] SubTask 7.1: 在 router/index.ts 中添加日志管理路由
  - [x] SubTask 7.2: 在 router/index.ts 中添加敏感词管理路由
  - [x] SubTask 7.3: 在 router/index.ts 中添加热点词分析路由

- [x] Task 8: 更新侧边栏菜单
  - [x] SubTask 8.1: 在 Sidebar.vue 中添加日志管理菜单项
  - [x] SubTask 8.2: 在 Sidebar.vue 中添加敏感词管理菜单项
  - [x] SubTask 8.3: 在 Sidebar.vue 中添加热点词分析菜单项

# Task Dependencies
- Task 2 依赖 Task 1（移除 Tabs 后菜单问题可能更明显）
- Task 4-6 可以并行执行
- Task 7-8 依赖 Task 4-6 完成
