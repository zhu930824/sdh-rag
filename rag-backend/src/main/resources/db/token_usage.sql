-- Token 使用记录表
CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    model_id BIGINT DEFAULT NULL COMMENT '模型配置ID',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    provider VARCHAR(64) DEFAULT NULL COMMENT '模型提供者',
    prompt_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输入token数',
    completion_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输出token数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总token数',
    duration_ms BIGINT DEFAULT NULL COMMENT '请求耗时(毫秒)',
    status VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '请求状态: success/failed',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    request_id VARCHAR(64) DEFAULT NULL COMMENT '请求追踪ID',
    knowledge_id BIGINT DEFAULT NULL COMMENT '知识库ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_model_id (model_id),
    INDEX idx_create_time (create_time),
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token使用记录表';
