-- 为 knowledge_document_relation 表添加自定义切分方式相关字段

ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `split_type` VARCHAR(20) DEFAULT 'length' COMMENT '自定义切分方式：length-按长度，page-按页，heading-按标题，regex-按正则',
ADD COLUMN IF NOT EXISTS `pages_per_chunk` INT COMMENT '每块页数（按页切分时使用）',
ADD COLUMN IF NOT EXISTS `heading_levels` VARCHAR(100) COMMENT '标题层级（按标题切分时使用，JSON数组格式）',
ADD COLUMN IF NOT EXISTS `regex_pattern` VARCHAR(500) COMMENT '正则表达式（按正则切分时使用）';

-- 更新已有记录的默认值
UPDATE `knowledge_document_relation` SET `split_type` = 'length' WHERE `split_type` IS NULL;
