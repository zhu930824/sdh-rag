-- 为 model_config 表添加 embedding_dimension 字段
ALTER TABLE `model_config` ADD COLUMN `embedding_dimension` int NULL DEFAULT NULL COMMENT '向量维度(仅向量模型有效)' AFTER `is_multi_model`;
