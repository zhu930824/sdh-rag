-- Immutable scenario versions and observed terminal-run archives; deliberately independent of mutable RAG QA datasets.
CREATE TABLE agent_evaluation_dataset (
    id VARCHAR(32) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    version INT NOT NULL,
    parent_id VARCHAR(32) NULL,
    sha256 CHAR(64) NOT NULL,
    scenario_count INT NOT NULL,
    scenarios_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_eval_parent (parent_id),
    KEY idx_agent_eval_dataset_owner (user_id, created_at)
);

CREATE TABLE agent_evaluation_record (
    id VARCHAR(32) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dataset_id VARCHAR(32) NOT NULL,
    scenario_id VARCHAR(64) NOT NULL,
    run_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    dataset_sha256 CHAR(64) NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    scenario_json MEDIUMTEXT NOT NULL,
    run_snapshot_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_eval_record (dataset_id, scenario_id, run_key),
    KEY idx_agent_eval_record_owner (user_id, dataset_id, created_at)
);
