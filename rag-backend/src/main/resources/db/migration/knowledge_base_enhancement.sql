-- 知识库增强迁移脚本
-- 执行时间: 2026-04-07

-- 1. 创建 knowledge_chunk 表（如果不存在）
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分块ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `knowledge_id` BIGINT COMMENT '知识库ID（独立存储方案）',
    `chunk_index` INT NOT NULL COMMENT '分块索引',
    `content` TEXT COMMENT '分块内容',
    `chunk_size` INT COMMENT '分块大小',
    `vector_id` VARCHAR(100) COMMENT 'Elasticsearch向量ID',
    `knowledge_ids` TEXT COMMENT '关联的知识库ID列表（JSON数组）',
    `metadata` TEXT COMMENT '元数据（JSON格式）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_knowledge_id` (`knowledge_id`),
    KEY `idx_doc_knowledge` (`document_id`, `knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库分块表';

-- 2. 创建知识库标签关联表
CREATE TABLE IF NOT EXISTS `knowledge_base_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `knowledge_base_id` BIGINT NOT NULL COMMENT '知识库ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_tag` (`knowledge_base_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库标签关联表';

-- 3. 确保 knowledge_base 表有必要字段
ALTER TABLE `knowledge_base`
ADD COLUMN IF NOT EXISTS `chunk_size` INT DEFAULT 500 COMMENT '分块大小（字符数）',
ADD COLUMN IF NOT EXISTS `chunk_overlap` INT DEFAULT 50 COMMENT '分块重叠大小',
ADD COLUMN IF NOT EXISTS `embedding_model` VARCHAR(100) DEFAULT 'text-embedding-ada-002' COMMENT '嵌入模型',
ADD COLUMN IF NOT EXISTS `is_public` TINYINT DEFAULT 0 COMMENT '是否公开：0-否，1-是',
ADD COLUMN IF NOT EXISTS `icon` VARCHAR(100) COMMENT '图标',
ADD COLUMN IF NOT EXISTS `color` VARCHAR(20) COMMENT '颜色';

-- 4. 为 knowledge_document_relation 添加配置字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `chunk_size` INT COMMENT '切分时使用的分块大小',
ADD COLUMN IF NOT EXISTS `chunk_overlap` INT COMMENT '切分时使用的重叠大小',
ADD COLUMN IF NOT EXISTS `embedding_model` VARCHAR(100) COMMENT '切分时使用的嵌入模型';