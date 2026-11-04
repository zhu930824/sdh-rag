ALTER TABLE knowledge_draft ADD COLUMN published_generation VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_draft_published_document ON knowledge_draft (published_document_id);
CREATE INDEX idx_draft_publication_recovery ON knowledge_draft (status, updated_at);
-- Preserve already released historical heads without inventing a new gate receipt.
UPDATE knowledge_draft SET published_generation=(SELECT r.published_generation FROM knowledge_document_relation r
    WHERE r.document_id=knowledge_draft.published_document_id AND r.knowledge_id=knowledge_draft.knowledge_id)
WHERE status='PUBLISHED';

CREATE TABLE knowledge_index_publication_gate (
    generation_key VARCHAR(64) PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    policy_sha256 CHAR(64) NOT NULL,
    policy_json LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    basis_sha256 CHAR(64) NULL,
    candidate_sha256 CHAR(64) NULL,
    result_json LONGTEXT NULL,
    checked_at DATETIME(6) NULL,
    CONSTRAINT fk_index_gate_generation FOREIGN KEY (generation_key) REFERENCES knowledge_index_generation(generation_key),
    CONSTRAINT fk_index_gate_draft FOREIGN KEY (draft_id) REFERENCES knowledge_draft(id)
);

-- Old pending/failed draft vectors must not remain online while their new gate runs.
UPDATE knowledge_document_relation SET published_generation=NULL,index_write_generation=NULL,
    index_source_sha256=NULL,job_id=NULL,index_revision=index_revision+1,process_status=0
WHERE document_id IN (SELECT published_document_id FROM knowledge_draft WHERE status='PUBLISHING');
UPDATE knowledge_document_relation SET published_generation=NULL,index_write_generation=NULL,
    index_source_sha256=NULL,job_id=NULL,index_revision=index_revision+1,process_status=3
WHERE document_id IN (SELECT published_document_id FROM knowledge_draft WHERE status IN ('GATE_FAILED','PUBLISH_FAILED','ROLLED_BACK'));
