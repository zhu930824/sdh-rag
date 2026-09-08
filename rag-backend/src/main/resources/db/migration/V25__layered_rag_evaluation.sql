ALTER TABLE evaluation_task
    ADD COLUMN evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'RETRIEVAL' COMMENT 'RETRIEVAL/GENERATION/FULL' AFTER dataset_type,
    ADD COLUMN top_k INT NOT NULL DEFAULT 5 AFTER evaluation_mode,
    ADD COLUMN labeled_count INT NOT NULL DEFAULT 0 AFTER top_k,
    ADD COLUMN judged_count INT NOT NULL DEFAULT 0 AFTER labeled_count,
    ADD COLUMN recall_at_k DECIMAL(6,4) NULL AFTER mrr,
    ADD COLUMN precision_at_k DECIMAL(6,4) NULL AFTER recall_at_k,
    ADD COLUMN ndcg_at_k DECIMAL(6,4) NULL AFTER precision_at_k,
    ADD COLUMN map_score DECIMAL(6,4) NULL AFTER ndcg_at_k,
    ADD COLUMN context_precision DECIMAL(6,4) NULL AFTER map_score,
    ADD COLUMN context_recall DECIMAL(6,4) NULL AFTER context_precision,
    ADD COLUMN faithfulness DECIMAL(6,4) NULL AFTER context_recall,
    ADD COLUMN answer_relevancy DECIMAL(6,4) NULL AFTER faithfulness,
    ADD COLUMN completeness DECIMAL(6,4) NULL AFTER answer_relevancy,
    ADD COLUMN fluency DECIMAL(6,4) NULL AFTER completeness,
    ADD COLUMN overall_score DECIMAL(6,4) NULL AFTER fluency,
    ADD COLUMN judge_model VARCHAR(100) NULL AFTER overall_score,
    ADD COLUMN baseline_task_id BIGINT NULL AFTER judge_model,
    ADD COLUMN regression_status VARCHAR(20) NULL COMMENT 'PASSED/FAILED/NOT_COMPARED' AFTER baseline_task_id;

ALTER TABLE evaluation_qa
    ADD COLUMN expected_document_ids TEXT NULL COMMENT '相关文档ID JSON数组' AFTER source_document_id,
    ADD COLUMN relevance_judgments TEXT NULL COMMENT '文档ID到0-3相关性等级的JSON对象' AFTER expected_document_ids,
    ADD COLUMN retrieved_document_ids TEXT NULL AFTER retrieved_chunk_ids,
    ADD COLUMN retrieved_context MEDIUMTEXT NULL AFTER retrieved_document_ids,
    ADD COLUMN actual_answer MEDIUMTEXT NULL AFTER retrieved_context,
    ADD COLUMN recall_at_k DECIMAL(6,4) NULL AFTER doc_hit_rank,
    ADD COLUMN precision_at_k DECIMAL(6,4) NULL AFTER recall_at_k,
    ADD COLUMN reciprocal_rank DECIMAL(6,4) NULL AFTER precision_at_k,
    ADD COLUMN ndcg_at_k DECIMAL(6,4) NULL AFTER reciprocal_rank,
    ADD COLUMN average_precision DECIMAL(6,4) NULL AFTER ndcg_at_k,
    ADD COLUMN context_precision DECIMAL(6,4) NULL AFTER average_precision,
    ADD COLUMN context_recall DECIMAL(6,4) NULL AFTER context_precision,
    ADD COLUMN faithfulness DECIMAL(6,4) NULL AFTER context_recall,
    ADD COLUMN answer_relevancy DECIMAL(6,4) NULL AFTER faithfulness,
    ADD COLUMN completeness DECIMAL(6,4) NULL AFTER answer_relevancy,
    ADD COLUMN fluency DECIMAL(6,4) NULL AFTER completeness,
    ADD COLUMN overall_score DECIMAL(6,4) NULL AFTER fluency,
    ADD COLUMN judge_reason VARCHAR(1000) NULL AFTER overall_score,
    ADD COLUMN category VARCHAR(30) NULL AFTER external_id,
    ADD COLUMN difficulty VARCHAR(10) NULL AFTER category;

ALTER TABLE test_dataset_item
    ADD COLUMN expected_document_ids TEXT NULL COMMENT '相关文档ID JSON数组' AFTER source_document_id,
    ADD COLUMN relevance_judgments TEXT NULL COMMENT '文档ID到0-3相关性等级JSON' AFTER expected_document_ids,
    ADD COLUMN category VARCHAR(30) NULL AFTER external_id,
    ADD COLUMN difficulty VARCHAR(10) NULL AFTER category;

INSERT INTO test_dataset (name, description, item_count, negative_count, dataset_type, file_name)
SELECT 'RAG 分层评测 MVP 数据集', '按照检索、生成、端到端三层方法设计，包含语义检索、多跳、忠实度、引用和拒答场景', 24, 4, 'builtin', 'rag_evaluation_mvp.json'
WHERE NOT EXISTS (SELECT 1 FROM test_dataset WHERE file_name = 'rag_evaluation_mvp.json');
