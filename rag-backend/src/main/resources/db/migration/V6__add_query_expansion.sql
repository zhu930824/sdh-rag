-- 添加查询扩展配置字段
ALTER TABLE knowledge_base
ADD COLUMN enable_query_expansion TINYINT(1) DEFAULT 0 COMMENT '是否启用查询扩展',
ADD COLUMN query_expansion_count INT DEFAULT 3 COMMENT '查询扩展数量';
