ALTER TABLE evaluation_task ADD COLUMN current_execution_id CHAR(32) NULL;
CREATE TABLE evaluation_execution_attempt (
    id CHAR(32) PRIMARY KEY,
    task_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    publication_sha256 CHAR(64) NOT NULL,
    configuration_sha256 CHAR(64) NOT NULL,
    input_sha256 CHAR(64) NULL,
    input_json LONGTEXT NULL,
    initial_snapshot_json LONGTEXT NOT NULL,
    final_snapshot_json LONGTEXT NULL,
    final_snapshot_sha256 CHAR(64) NULL,
    result_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    UNIQUE KEY uk_evaluation_attempt (task_id, attempt_number),
    KEY ix_evaluation_attempt_recovery (status,job_id,task_id),
    CONSTRAINT fk_evaluation_execution_task FOREIGN KEY (task_id) REFERENCES evaluation_task(id) ON DELETE CASCADE
);
CREATE TABLE evaluation_execution_result (
    execution_id CHAR(32) NOT NULL,
    ordinal INT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    PRIMARY KEY(execution_id,ordinal),
    CONSTRAINT fk_evaluation_execution_result FOREIGN KEY (execution_id) REFERENCES evaluation_execution_attempt(id) ON DELETE CASCADE
);
