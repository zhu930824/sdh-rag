ALTER TABLE knowledge_source
    ADD COLUMN sync_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '0 manual, 1 scheduled pull' AFTER status,
    ADD COLUMN sync_interval_minutes INT NOT NULL DEFAULT 60 AFTER sync_enabled,
    ADD COLUMN next_sync_time DATETIME NULL AFTER sync_interval_minutes,
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0 AFTER last_sync_status,
    ADD COLUMN last_error VARCHAR(1000) NULL AFTER consecutive_failures,
    ADD KEY idx_source_schedule (sync_enabled, status, next_sync_time);

ALTER TABLE knowledge_sync_task
    MODIFY COLUMN trigger_type VARCHAR(20) NOT NULL DEFAULT 'API_PUSH'
        COMMENT 'API_PUSH/MANUAL_PULL/SCHEDULED_PULL';
