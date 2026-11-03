ALTER TABLE knowledge_document_relation
    ADD COLUMN index_revision INT NOT NULL DEFAULT 0 COMMENT '索引任务版本，防止旧任务覆盖新配置' AFTER error_message,
    ADD COLUMN job_id BIGINT NULL COMMENT '当前索引任务ID' AFTER index_revision,
    ADD INDEX idx_relation_job_id (job_id),
    ADD INDEX idx_relation_pending_job (process_status, job_id);
