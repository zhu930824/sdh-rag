-- 为 knowledge_document_relation 表添加切分模式和切分方式相关字段
-- 执行此脚本前请先备份数据

-- 添加切分模式字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `chunk_mode` VARCHAR(20) DEFAULT 'default' COMMENT '切分策略模式：default-默认，smart-智能切分，custom-自定义';

-- 添加智能切分配置字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `smart_config` TEXT COMMENT '智能切分配置（JSON格式）';

-- 添加错误信息字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `error_message` TEXT COMMENT '处理错误信息';

-- 添加自定义切分方式字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `split_type` VARCHAR(20) DEFAULT 'length' COMMENT '自定义切分方式：length-按长度，page-按页，heading-按标题，regex-按正则';

-- 添加按页切分参数字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `pages_per_chunk` INT COMMENT '每块页数（按页切分时使用）';

-- 添加按标题切分参数字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `heading_levels` VARCHAR(100) COMMENT '标题层级（按标题切分时使用，JSON数组格式）';

-- 添加按正则切分参数字段
ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `regex_pattern` VARCHAR(500) COMMENT '正则表达式（按正则切分时使用）';

-- 更新已有记录的默认值
UPDATE `knowledge_document_relation` SET `chunk_mode` = 'default' WHERE `chunk_mode` IS NULL;
UPDATE `knowledge_document_relation` SET `split_type` = 'length' WHERE `split_type` IS NULL;
