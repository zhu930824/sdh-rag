ALTER TABLE knowledge_document_relation ADD COLUMN published_generation VARCHAR(64) NULL;
ALTER TABLE knowledge_document_relation ADD COLUMN index_write_generation VARCHAR(64) NULL;
ALTER TABLE knowledge_document_relation ADD COLUMN index_source_sha256 CHAR(64) NULL;

CREATE TABLE knowledge_index_generation (
    generation_key VARCHAR(64) PRIMARY KEY,
    relation_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    index_revision INT NOT NULL,
    source_sha256 CHAR(64) NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    legacy_vectors BOOLEAN NOT NULL DEFAULT FALSE,
    summary_ready BOOLEAN NOT NULL DEFAULT FALSE,
    state VARCHAR(24) NOT NULL,
    published_at DATETIME(6) NULL,
    KEY idx_index_generation_revision (relation_id, index_revision),
    KEY idx_index_generation_knowledge (knowledge_id, state)
);

-- Only an authoritative completed relation can seed an old-format published head.
INSERT INTO knowledge_index_generation
    (generation_key, relation_id, knowledge_id, document_id, index_revision, chunk_count, legacy_vectors, state, published_at)
SELECT CONCAT('legacy:', id, ':', index_revision), id, knowledge_id, document_id, index_revision,
    COALESCE(chunk_count, 0), TRUE, 'PUBLISHED', COALESCE(process_time, create_time)
FROM knowledge_document_relation WHERE process_status=2;

UPDATE knowledge_document_relation SET published_generation=CONCAT('legacy:', id, ':', index_revision)
WHERE process_status=2;

CREATE TABLE knowledge_index_read_snapshot (
    snapshot_sha256 CHAR(64) PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    manifest_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_index_read_snapshot_knowledge (knowledge_id, created_at)
);
