CREATE TABLE knowledge_base_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission VARCHAR(16) NOT NULL COMMENT 'reader/editor',
    granted_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_member (knowledge_id, user_id),
    KEY idx_member_user (user_id, knowledge_id)
);
