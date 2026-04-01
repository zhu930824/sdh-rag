-- 用户表添加邮箱和角色字段
ALTER TABLE `user` 
ADD COLUMN `email` VARCHAR(100) COMMENT '邮箱' AFTER `avatar`,
ADD COLUMN `role` VARCHAR(50) DEFAULT '普通用户' COMMENT '角色' AFTER `email`;

-- 更新现有用户的角色
UPDATE `user` SET `role` = '管理员', `email` = 'admin@example.com' WHERE `username` = 'admin';
