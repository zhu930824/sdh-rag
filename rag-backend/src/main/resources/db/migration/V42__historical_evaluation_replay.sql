CREATE TABLE knowledge_index_snapshot_hold (
    hold_id CHAR(32) PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    KEY ix_snapshot_hold (snapshot_sha256,expires_at),
    CONSTRAINT fk_snapshot_hold_manifest FOREIGN KEY (snapshot_sha256) REFERENCES knowledge_index_read_snapshot(snapshot_sha256)
);
CREATE TABLE evaluation_replay_request (
    task_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    source_execution_id CHAR(32) NOT NULL,
    publication_sha256 CHAR(64) NOT NULL,
    hold_id CHAR(32) NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    configuration_sha256 CHAR(64) NOT NULL,
    input_json LONGTEXT NOT NULL,
    input_sha256 CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_replay_request (user_id,request_key),
    CONSTRAINT fk_replay_task FOREIGN KEY (task_id) REFERENCES evaluation_task(id) ON DELETE CASCADE
);
