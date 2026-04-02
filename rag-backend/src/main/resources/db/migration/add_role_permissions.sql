-- 给角色表添加权限字段
ALTER TABLE `role` ADD COLUMN `permissions` TEXT COMMENT '菜单权限，逗号分隔的菜单路径' AFTER `description`;

-- 插入默认角色数据
INSERT INTO `role` (`name`, `code`, `description`, `permissions`, `status`) VALUES
('管理员', 'admin', '系统管理员，拥有所有权限', '/dashboard,/knowledge-base,/knowledge,/chat,/graph,/workflow,/model,/stats,/hotwords,/feedback,/approval,/process-task,/tag,/voice,/prompt,/session,/scheduled-task,/webhook,/user,/role,/log,/sensitive,/announcement,/settings', 1),
('普通用户', 'user', '普通用户，拥有基本权限', '/dashboard,/knowledge-base,/knowledge,/chat,/graph', 1)
ON DUPLICATE KEY UPDATE `permissions` = VALUES(`permissions`);
