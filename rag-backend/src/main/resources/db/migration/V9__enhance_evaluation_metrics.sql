-- 评估QA表增加文档级命中字段
ALTER TABLE `evaluation_qa`
    ADD COLUMN `source_document_id` bigint NULL COMMENT '来源文档ID' AFTER `source_chunk_id`,
    ADD COLUMN `doc_hit` tinyint(1) NULL DEFAULT 0 COMMENT '文档级命中' AFTER `hit`,
    ADD COLUMN `doc_hit_rank` int NULL COMMENT '文档级命中排名' AFTER `hit_rank`;

-- 评估任务表增加更丰富的指标字段
ALTER TABLE `evaluation_task`
    ADD COLUMN `doc_hit_rate` decimal(5, 4) NULL COMMENT '文档级命中率' AFTER `hit_rate`,
    ADD COLUMN `avg_hit_rank` decimal(6, 2) NULL COMMENT '平均命中排名' AFTER `mrr`,
    ADD COLUMN `top_k_hits` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Top-K命中分布JSON' AFTER `avg_hit_rank`;

-- 删除旧的 avg_recall 字段（不再需要）
ALTER TABLE `evaluation_task`
    DROP COLUMN `avg_recall`;
