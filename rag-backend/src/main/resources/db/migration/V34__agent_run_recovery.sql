ALTER TABLE agent_run ADD COLUMN recovery_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_run ADD COLUMN recovery_job_id BIGINT NULL;
CREATE INDEX idx_agent_run_recovery_job ON agent_run(status, recovery_job_id);
