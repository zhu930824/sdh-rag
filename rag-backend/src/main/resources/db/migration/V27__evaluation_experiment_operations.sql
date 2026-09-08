ALTER TABLE test_dataset_item
    ADD COLUMN business_line VARCHAR(50) NULL COMMENT '业务线' AFTER difficulty,
    ADD COLUMN user_role VARCHAR(50) NULL COMMENT '用户角色' AFTER business_line;

ALTER TABLE evaluation_qa
    ADD COLUMN business_line VARCHAR(50) NULL COMMENT '业务线' AFTER difficulty,
    ADD COLUMN user_role VARCHAR(50) NULL COMMENT '用户角色' AFTER business_line,
    ADD COLUMN human_review_status VARCHAR(20) NULL COMMENT 'PENDING/COMPLETED/SKIPPED' AFTER failure_suggestion,
    ADD COLUMN human_faithfulness DECIMAL(6,4) NULL AFTER human_review_status,
    ADD COLUMN human_answer_relevancy DECIMAL(6,4) NULL AFTER human_faithfulness,
    ADD COLUMN human_completeness DECIMAL(6,4) NULL AFTER human_answer_relevancy,
    ADD COLUMN human_fluency DECIMAL(6,4) NULL AFTER human_completeness,
    ADD COLUMN human_overall_score DECIMAL(6,4) NULL AFTER human_fluency,
    ADD COLUMN human_review_note VARCHAR(1000) NULL AFTER human_overall_score,
    ADD COLUMN human_reviewer_id BIGINT NULL AFTER human_review_note,
    ADD COLUMN human_review_time DATETIME NULL AFTER human_reviewer_id,
    ADD INDEX idx_evaluation_human_review (human_review_status, task_id),
    ADD INDEX idx_evaluation_business_slice (business_line, user_role);

CREATE TABLE evaluation_experiment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    knowledge_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED',
    schedule_enabled TINYINT(1) NOT NULL DEFAULT 0,
    cron_expression VARCHAR(80) NULL,
    next_run_time DATETIME NULL,
    judge_budget INT NOT NULL DEFAULT 0 COMMENT '单次运行允许的最大评审调用数，0为不限制',
    max_retries INT NOT NULL DEFAULT 1,
    alert_on_regression TINYINT(1) NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    last_run_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_evaluation_experiment_owner (user_id, create_time),
    INDEX idx_evaluation_experiment_due (status, schedule_enabled, next_run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE evaluation_experiment_variant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    experiment_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'RETRIEVAL',
    top_k INT NOT NULL DEFAULT 5,
    judge_model VARCHAR(100) NULL,
    baseline_task_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evaluation_variant_experiment (experiment_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE evaluation_experiment_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    experiment_id BIGINT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL COMMENT 'MANUAL/SCHEDULED',
    status VARCHAR(20) NOT NULL COMMENT 'LAUNCHING/RUNNING/COMPLETED/FAILED',
    error_message VARCHAR(1000) NULL,
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evaluation_run_experiment (experiment_id, create_time),
    INDEX idx_evaluation_run_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE evaluation_experiment_run_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evaluation_run_task_run (run_id, variant_id, attempt),
    UNIQUE KEY uk_evaluation_run_task (run_id, variant_id, attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
