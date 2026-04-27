-- V13 添加记忆系统相关表

SET NAMES utf8mb4;

-- 摘要记忆表
CREATE TABLE IF NOT EXISTS `memory_abstract` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '摘要ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `session_id` VARCHAR(100) COMMENT '会话ID',
  `episode_id` VARCHAR(100) COMMENT '情景块ID',
  `topic_tag` VARCHAR(200) COMMENT '话题标签',
  `summary` TEXT COMMENT 'LLM生成的摘要',
  `key_points` TEXT COMMENT 'JSON格式的关键结论列表',
  `importance` INT DEFAULT 5 COMMENT '重要性评分 1-10',
  `access_count` INT DEFAULT 0 COMMENT '访问次数',
  `last_access_at` DATETIME COMMENT '最后访问时间',
  `needs_summary` TINYINT DEFAULT 0 COMMENT '是否需要补摘要',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_episode_id` (`episode_id`),
  INDEX `idx_topic_tag` (`topic_tag`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '记忆摘要表';

-- 为 chat_history 表添加语义分块字段
ALTER TABLE `chat_history` 
  ADD COLUMN IF NOT EXISTS `topic_tag` VARCHAR(200) COMMENT '话题标签' AFTER `sources`,
  ADD COLUMN IF NOT EXISTS `episode_id` VARCHAR(100) COMMENT '情景块ID' AFTER `topic_tag`;

-- 添加索引
CREATE INDEX IF NOT EXISTS `idx_chat_history_episode` ON `chat_history` (`episode_id`);
