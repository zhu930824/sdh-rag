CREATE TABLE knowledge_source_api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    key_prefix VARCHAR(24) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    rate_limit_per_minute INT NOT NULL DEFAULT 60,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 revoked, 1 active',
    expires_at DATETIME NULL,
    last_used_at DATETIME NULL,
    created_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoke_time DATETIME NULL,
    UNIQUE KEY uk_source_api_key_prefix (key_prefix),
    KEY idx_source_api_key_source (source_id, status),
    KEY idx_source_api_key_knowledge (knowledge_id, status)
);

CREATE TABLE knowledge_source_api_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    task_id BIGINT NULL,
    result_status VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAILED',
    response_code INT NOT NULL,
    error_message VARCHAR(1000) NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    user_agent VARCHAR(255) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_api_audit_request (request_id),
    KEY idx_source_api_audit_source_time (source_id, create_time),
    KEY idx_source_api_audit_key_time (api_key_id, create_time)
);
