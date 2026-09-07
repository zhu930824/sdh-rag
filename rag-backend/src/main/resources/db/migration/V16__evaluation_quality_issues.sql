ALTER TABLE evaluation_qa
    ADD COLUMN issue_status VARCHAR(16) NOT NULL DEFAULT 'open' COMMENT 'open/resolved/ignored' AFTER external_id,
    ADD COLUMN issue_note VARCHAR(500) NULL COMMENT '处理说明' AFTER issue_status,
    ADD COLUMN resolved_by BIGINT NULL COMMENT '处理用户' AFTER issue_note,
    ADD COLUMN resolved_time DATETIME NULL COMMENT '处理时间' AFTER resolved_by,
    ADD INDEX idx_evaluation_issue_status (issue_status);
