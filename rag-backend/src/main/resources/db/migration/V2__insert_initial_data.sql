-- ============================================
-- SDH-RAG 初始数据
-- Flyway Migration V2
-- ============================================

SET NAMES utf8mb4;

-- ----------------------------
-- 默认用户
-- ----------------------------
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `status`, `create_time`, `update_time`, `email`, `phone`, `signature`, `role`, `default_model_id`, `theme`, `language`, `email_notification`, `sound_notification`, `reply_language`, `user_level`, `experience`) VALUES
(1, 'admin', '$2a$10$sP2HwA2xtb7r44xmI.5ukO2fw7C6n72WOWyqkbIb/SYQpQP0w7wly', '管理员', NULL, 1, NOW(), NOW(), NULL, NULL, NULL, 'admin', NULL, 'light', 'zh-CN', 0, 1, NULL, 1, 0);

-- ----------------------------
-- 角色数据
-- ----------------------------
INSERT INTO `role` (`id`, `name`, `code`, `description`, `status`, `create_time`, `update_time`, `permissions`) VALUES
(1, '超级管理员', 'admin', '拥有所有权限', 1, NOW(), NOW(), '/dashboard,/chat,/knowledge,/document,/graph,/workflow,/model,/stats,/hotwords,/feedback,/approval,/tag,/user,/role,/log,/sensitive,/announcement,/settings,/session,/scheduled-task,/webhook,/voice,/prompt,/nlp-query'),
(2, '普通用户', 'user', '普通用户权限', 1, NOW(), NOW(), NULL),
(3, '访客', 'guest', '只读权限', 1, NOW(), NOW(), NULL);

-- ----------------------------
-- 菜单权限数据
-- ----------------------------
INSERT INTO `menu_permission` (`id`, `name`, `path`, `component`, `icon`, `parent_id`, `sort`, `visible`, `permission`, `create_time`, `update_time`) VALUES
(1, '首页', '/dashboard', 'dashboard/index', 'HomeOutlined', NULL, 1, 1, NULL, NOW(), NOW()),
(2, '知识库', '/knowledge', 'knowledge/index', 'FolderOutlined', NULL, 2, 1, NULL, NOW(), NOW()),
(3, '智能问答', '/chat', 'chat/index', 'MessageOutlined', NULL, 3, 1, NULL, NOW(), NOW()),
(4, '用户管理', '/user', 'user/index', 'UserOutlined', NULL, 4, 1, NULL, NOW(), NOW()),
(5, '敏感词管理', '/sensitive', 'sensitive/index', 'WarningOutlined', NULL, 5, 1, NULL, NOW(), NOW()),
(6, '日志管理', '/log', 'log/index', 'FileTextOutlined', NULL, 6, 1, NULL, NOW(), NOW()),
(7, '热点词分析', '/hotwords', 'hotwords/index', 'LineChartOutlined', NULL, 7, 1, NULL, NOW(), NOW()),
(8, '系统设置', '/settings', 'settings/index', 'SettingOutlined', NULL, 8, 1, NULL, NOW(), NOW()),
(9, '大模型管理', '/model', 'model/index', 'RobotOutlined', NULL, 9, 1, NULL, NOW(), NOW()),
(10, '工作流编排', '/workflow', 'workflow/index', 'BranchesOutlined', NULL, 10, 1, NULL, NOW(), NOW()),
(13, '知识图谱', '/graph', 'graph/index', 'ApartmentOutlined', NULL, 11, 1, NULL, NOW(), NOW()),
(15, '数据统计', '/stats', 'stats/index', 'BarChartOutlined', NULL, 12, 1, NULL, NOW(), NOW()),
(16, '公告管理', '/announcement', 'announcement/index', 'NotificationOutlined', NULL, 13, 1, NULL, NOW(), NOW()),
(21, '标签管理', '/tag', 'tag/index', 'TagsOutlined', NULL, 14, 1, NULL, NOW(), NOW()),
(24, '定时任务', '/scheduled-task', 'scheduled-task/index', 'ClockCircleOutlined', NULL, 15, 1, NULL, NOW(), NOW());

-- ----------------------------
-- 用户角色关联
-- ----------------------------
INSERT INTO `user_role` (`user_id`, `role_id`, `create_time`) VALUES (1, 1, NOW());

-- ----------------------------
-- 系统配置
-- ----------------------------
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('file.upload.path', './uploads', '文件上传路径'),
('file.upload.maxSize', '10485760', '文件上传最大大小（字节）'),
('file.upload.allowedTypes', 'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,txt,md', '允许上传的文件类型');

-- ----------------------------
-- 模型配置
-- ----------------------------
INSERT INTO `model_config` (`id`, `name`, `provider`, `model_type`, `model_id`, `api_key`, `base_url`, `temperature`, `max_tokens`, `status`, `is_default`, `sort`, `is_local`, `is_built_in`, `icon`, `create_time`, `update_time`) VALUES
(5, 'text-embedding-v2', 'alibaba', 'embedding', 'text-embedding-v2', '', NULL, 0.7, 2000, 1, 0, 5, 0, 1, NULL, NOW(), NOW()),
(8, 'qwen3', 'alibaba', 'chat', 'qwen3', '', '', 0.7, 2000, 1, 1, 0, 0, 0, NULL, NOW(), NOW()),
(9, 'text-embedding-v4', 'alibaba', 'embedding', 'text-embedding-v4', '', '', 0.7, 4000, 1, 0, 5, 0, 1, NULL, NOW(), NOW()),
(10, 'qwen3-rerank', 'alibaba', 'reranker', 'qwen3-rerank', '', '', 0.7, 2000, 1, 0, 5, 0, 1, NULL, NOW(), NOW());
