-- 为 model_config 表添加 is_multi_model 字段
ALTER TABLE `model_config` ADD COLUMN `is_multi_model` tinyint NULL DEFAULT 0 COMMENT '是否为多模态模型：0-否，1-是' AFTER `is_built_in`;

-- 更新现有的 qwen3 模型为多模态模型
UPDATE `model_config` SET `is_multi_model` = 1 WHERE `model_id` = 'qwen3';
