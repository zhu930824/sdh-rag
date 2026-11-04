-- Frozen observation of the retrieved candidates, not a pinned corpus publication.
ALTER TABLE agent_run ADD COLUMN retrieval_snapshot_json MEDIUMTEXT NULL;
