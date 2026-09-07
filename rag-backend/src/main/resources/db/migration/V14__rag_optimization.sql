-- V14: RAG 企业级检索优化
-- 1. 新增去重表
CREATE TABLE IF NOT EXISTS document_hash (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    knowledge_id BIGINT NOT NULL COMMENT '知识库ID',
    content_md5 VARCHAR(32) NOT NULL COMMENT '全文MD5哈希',
    minhash_signature TEXT COMMENT 'MinHash签名(JSON数组)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_knowledge_md5 (knowledge_id, content_md5),
    INDEX idx_document_id (document_id)
) COMMENT '文档去重哈希表';

-- 2. knowledge_document 新增元数据字段
ALTER TABLE knowledge_document
    ADD COLUMN department VARCHAR(100) COMMENT '部门',
    ADD COLUMN doc_type VARCHAR(50) COMMENT '文档类型: policy/manual/contract/report/faq',
    ADD COLUMN doc_date DATE COMMENT '文档日期',
    ADD COLUMN freshness_score DECIMAL(3,2) DEFAULT 1.0 COMMENT '新鲜度评分0-1',
    ADD COLUMN content_md5 VARCHAR(32) COMMENT '内容MD5',
    ADD COLUMN quality_status TINYINT DEFAULT 0 COMMENT '0=正常 1=低质 2=疑似重复';

-- 3. knowledge_base 新增检索配置字段
ALTER TABLE knowledge_base
    ADD COLUMN enable_summary_index TINYINT(1) DEFAULT 1 COMMENT '启用摘要索引',
    ADD COLUMN summary_coarse_top_k INT DEFAULT 50 COMMENT '摘要粗筛返回数',
    ADD COLUMN enable_cold_archive TINYINT(1) DEFAULT 1 COMMENT '启用冷热分离',
    ADD COLUMN hot_min_results INT DEFAULT 3 COMMENT '热库最低结果数',
    ADD COLUMN mmr_lambda DECIMAL(3,2) DEFAULT 0.70 COMMENT 'MMR相关性权重',
    ADD COLUMN recency_alpha DECIMAL(3,2) DEFAULT 0.30 COMMENT '时间衰减系数',
    ADD COLUMN coarse_top_k INT DEFAULT 50 COMMENT '粗筛返回文档数',
    ADD COLUMN fine_top_k INT DEFAULT 20 COMMENT '精排返回chunk数';
