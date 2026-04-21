-- 添加 HyDE 配置字段
ALTER TABLE knowledge_base ADD COLUMN enable_hyde TINYINT(1) DEFAULT 0 COMMENT '是否启用HyDE检索增强';
ALTER TABLE knowledge_base ADD COLUMN hyde_model VARCHAR(100) DEFAULT NULL COMMENT 'HyDE生成文档使用的模型';