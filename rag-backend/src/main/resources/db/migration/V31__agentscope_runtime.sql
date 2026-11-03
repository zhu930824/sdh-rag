ALTER TABLE agent_run ADD COLUMN engine VARCHAR(32) NOT NULL DEFAULT 'legacy';
ALTER TABLE agent_run ADD COLUMN engine_version VARCHAR(32) NULL;
ALTER TABLE agent_run ADD COLUMN memory_enabled TINYINT NOT NULL DEFAULT 1;

ALTER TABLE agent_action ADD COLUMN tool_call_id VARCHAR(128) NULL;
ALTER TABLE agent_action ADD COLUMN tool_definition_hash CHAR(64) NULL;

CREATE TABLE agent_runtime_state (
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    state_key VARCHAR(100) NOT NULL,
    state_version BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, session_id, state_key)
) COMMENT='Versioned AgentScope checkpoints; session_id is the platform run key';
