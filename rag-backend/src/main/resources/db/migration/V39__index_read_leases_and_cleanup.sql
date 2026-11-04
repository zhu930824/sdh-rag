CREATE TABLE knowledge_index_read_guard (
    knowledge_id BIGINT PRIMARY KEY
);

CREATE TABLE knowledge_index_read_lease (
    lease_id CHAR(36) PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    KEY idx_index_lease_expiry (knowledge_id, expires_at),
    KEY idx_index_lease_snapshot (snapshot_sha256, expires_at)
);

-- The immutable generation set is shared by every reader of one publication version.
ALTER TABLE knowledge_index_read_snapshot ADD COLUMN generations_registered BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE knowledge_index_read_snapshot_generation (
    snapshot_sha256 CHAR(64) NOT NULL,
    generation_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (snapshot_sha256, generation_key),
    KEY idx_index_snapshot_generation (generation_key),
    CONSTRAINT fk_index_snapshot_generation FOREIGN KEY (snapshot_sha256)
        REFERENCES knowledge_index_read_snapshot (snapshot_sha256) ON DELETE CASCADE
);

ALTER TABLE knowledge_index_generation ADD COLUMN retired_at DATETIME(6) NULL;
ALTER TABLE knowledge_index_generation ADD COLUMN cleanup_owner VARCHAR(64) NULL;
ALTER TABLE knowledge_index_generation ADD COLUMN cleanup_until DATETIME(6) NULL;
ALTER TABLE knowledge_index_generation ADD COLUMN next_cleanup_at DATETIME(6) NULL;
ALTER TABLE knowledge_index_generation ADD COLUMN last_cleanup_at DATETIME(6) NULL;
ALTER TABLE knowledge_index_generation ADD COLUMN cleanup_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_index_generation ADD COLUMN cleanup_error VARCHAR(1000) NULL;
CREATE INDEX idx_index_cleanup_due ON knowledge_index_generation (next_cleanup_at, knowledge_id);
CREATE INDEX idx_relation_published_generation ON knowledge_document_relation (published_generation);
CREATE INDEX idx_relation_index_write_generation ON knowledge_document_relation (index_write_generation);
