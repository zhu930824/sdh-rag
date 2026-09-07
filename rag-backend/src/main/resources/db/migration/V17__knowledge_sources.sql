CREATE TABLE knowledge_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    config_json TEXT NULL,
    credential_ref VARCHAR(200) NULL,
    sync_cursor VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 active',
    last_sync_time DATETIME NULL,
    last_sync_status VARCHAR(20) NULL,
    created_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_source_name (knowledge_id, name),
    KEY idx_source_knowledge (knowledge_id, status)
);

CREATE TABLE knowledge_source_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    document_id BIGINT NULL,
    source_url VARCHAR(1000) NULL,
    source_updated_at DATETIME NULL,
    content_hash VARCHAR(64) NULL,
    version_no INT NOT NULL DEFAULT 1,
    item_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
    last_seen_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_external (source_id, external_id),
    KEY idx_source_item_document (document_id),
    KEY idx_source_item_status (source_id, item_status)
);

CREATE TABLE knowledge_sync_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'API_PUSH',
    task_status VARCHAR(20) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED',
    cursor_before VARCHAR(500) NULL,
    cursor_after VARCHAR(500) NULL,
    scanned_count INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    unchanged_count INT NOT NULL DEFAULT 0,
    deleted_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_by BIGINT NOT NULL,
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time DATETIME NULL,
    KEY idx_sync_source_time (source_id, start_time),
    KEY idx_sync_knowledge_time (knowledge_id, start_time)
);
