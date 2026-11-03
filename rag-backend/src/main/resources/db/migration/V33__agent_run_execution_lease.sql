ALTER TABLE agent_run ADD COLUMN execution_epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_run ADD COLUMN lease_owner VARCHAR(64) NULL;
ALTER TABLE agent_run ADD COLUMN lease_until DATETIME NULL;
ALTER TABLE agent_run ADD COLUMN heartbeat_at DATETIME NULL;
CREATE INDEX idx_agent_run_lease ON agent_run(status, lease_until);
