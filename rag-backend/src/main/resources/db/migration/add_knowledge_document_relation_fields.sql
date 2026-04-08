-- 为 knowledge_document_relation 表添加缺少的字段

ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `process_status` INT DEFAULT 0 COMMENT '处理状态：0-待处理，1-处理中，2-成功，3-失败',
ADD COLUMN IF NOT EXISTS `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
ADD COLUMN IF NOT EXISTS `process_time` DATETIME COMMENT '处理时间';

-- 添加索引
ALTER TABLE `knowledge_document_relation`
ADD INDEX IF NOT EXISTS `idx_process_status` (`process_status`);
