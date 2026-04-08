-- 用户表添加偏好设置相关字段
ALTER TABLE `user`
ADD COLUMN `phone` VARCHAR(20) COMMENT '手机号' AFTER `email`,
ADD COLUMN `signature` VARCHAR(100) COMMENT '个性签名' AFTER `phone`,
ADD COLUMN `default_model_id` BIGINT COMMENT '默认模型ID' AFTER `role`,
ADD COLUMN `theme` VARCHAR(20) DEFAULT 'light' COMMENT '主题偏好' AFTER `default_model_id`,
ADD COLUMN `language` VARCHAR(20) DEFAULT 'zh-CN' COMMENT '语言偏好' AFTER `theme`,
ADD COLUMN `email_notification` TINYINT(1) DEFAULT 0 COMMENT '是否开启邮件通知' AFTER `language`,
ADD COLUMN `sound_notification` TINYINT(1) DEFAULT 1 COMMENT '是否开启声音提醒' AFTER `email_notification`,
ADD COLUMN `reply_language` VARCHAR(20) COMMENT '回复语言偏好' AFTER `sound_notification`,
ADD COLUMN `user_level` INT DEFAULT 1 COMMENT '用户等级' AFTER `reply_language`,
ADD COLUMN `experience` INT DEFAULT 0 COMMENT '经验值' AFTER `user_level`;

-- 创建文件上传目录配置表（如果需要存储配置）
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(255) COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 插入默认配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('file.upload.path', './uploads', '文件上传路径'),
('file.upload.maxSize', '10485760', '文件上传最大大小（字节）'),
('file.upload.allowedTypes', 'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,txt,md', '允许上传的文件类型');
