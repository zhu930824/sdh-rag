ALTER TABLE agent_action ADD COLUMN execution_version INT NOT NULL DEFAULT 1;
ALTER TABLE agent_action ADD COLUMN execution_job_id BIGINT NULL;
ALTER TABLE agent_action ADD COLUMN started_at DATETIME NULL;
CREATE INDEX idx_agent_action_execution_job ON agent_action(execution_job_id);
CREATE INDEX idx_agent_action_recovery ON agent_action(status, update_time);
