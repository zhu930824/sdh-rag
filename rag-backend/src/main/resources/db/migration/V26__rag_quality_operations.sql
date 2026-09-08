ALTER TABLE test_dataset
    ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER file_name,
    ADD COLUMN parent_dataset_id BIGINT NULL COMMENT '复制来源数据集' AFTER version,
    ADD COLUMN change_note VARCHAR(500) NULL AFTER parent_dataset_id,
    ADD INDEX idx_test_dataset_parent (parent_dataset_id);

ALTER TABLE test_dataset_item
    ADD COLUMN source_feedback_id BIGINT NULL COMMENT '来源问答反馈' AFTER external_id,
    ADD UNIQUE KEY uk_dataset_feedback (dataset_id, source_feedback_id);

ALTER TABLE evaluation_task
    ADD COLUMN dataset_id BIGINT NULL AFTER dataset_type,
    ADD COLUMN dataset_version INT NULL AFTER dataset_id,
    ADD INDEX idx_evaluation_dataset_version (dataset_id, dataset_version);

ALTER TABLE evaluation_qa
    ADD COLUMN failure_category VARCHAR(40) NULL AFTER judge_reason,
    ADD COLUMN failure_suggestion VARCHAR(1000) NULL AFTER failure_category,
    ADD INDEX idx_evaluation_failure_category (failure_category);
