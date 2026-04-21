-- 评估QA表增强字段
ALTER TABLE `evaluation_qa`
    ADD COLUMN `is_negative` tinyint(1) NULL DEFAULT 0 COMMENT '是否负样本' AFTER `doc_hit_rank`,
    ADD COLUMN `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'generated' COMMENT '来源类型: generated/imported/builtin' AFTER `is_negative`,
    ADD COLUMN `external_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '外部数据集ID' AFTER `source_type`;

-- 评估任务表增强字段
ALTER TABLE `evaluation_task`
    ADD COLUMN `negative_count` int NULL DEFAULT 0 COMMENT '负样本数量' AFTER `top_k_hits`,
    ADD COLUMN `negative_hit_rate` decimal(5, 4) NULL COMMENT '负样本命中率(错误命中比例，应越低越好)' AFTER `negative_count`,
    ADD COLUMN `dataset_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'generated' COMMENT '数据集类型: generated/imported/builtin' AFTER `negative_hit_rate`;

-- 添加索引
ALTER TABLE `evaluation_qa`
    ADD INDEX `idx_source_type`(`source_type` ASC) USING BTREE,
    ADD INDEX `idx_is_negative`(`is_negative` ASC) USING BTREE;

ALTER TABLE `evaluation_task`
    ADD INDEX `idx_dataset_type`(`dataset_type` ASC) USING BTREE;