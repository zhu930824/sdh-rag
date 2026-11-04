CREATE TABLE agent_execution_attempt (
    run_id BIGINT NOT NULL,
    execution_epoch BIGINT NOT NULL,
    run_key VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    configuration_sha256 CHAR(64) NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id, execution_epoch),
    KEY idx_agent_attempt_owner (user_id, run_key, execution_epoch)
);
