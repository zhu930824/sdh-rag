CREATE TABLE platform_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_key VARCHAR(160) NOT NULL COMMENT '业务幂等键',
    job_type VARCHAR(60) NOT NULL,
    resource_type VARCHAR(40) NULL,
    resource_id BIGINT NULL,
    payload_json LONGTEXT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/FAILED/DEAD_LETTER/CANCEL_REQUESTED/CANCELLED',
    priority INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(120) NULL,
    lease_until DATETIME NULL,
    heartbeat_at DATETIME NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    result_summary VARCHAR(1000) NULL,
    error_message VARCHAR(2000) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_job_key (job_key),
    KEY idx_platform_job_dispatch (status, available_at, priority),
    KEY idx_platform_job_lease (status, lease_until),
    KEY idx_platform_job_creator (created_by, create_time),
    KEY idx_platform_job_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一可靠任务';

ALTER TABLE evaluation_task
    ADD COLUMN job_id BIGINT NULL AFTER id,
    ADD KEY idx_evaluation_job (job_id),
    MODIFY COLUMN status TINYINT NULL DEFAULT 0 COMMENT '0待运行/1运行中/2完成/3失败/4已取消';
