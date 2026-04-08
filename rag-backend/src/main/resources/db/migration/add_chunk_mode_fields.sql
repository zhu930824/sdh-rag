-- 为 knowledge_document_relation 表添加切分模式相关字段

ALTER TABLE `knowledge_document_relation`
ADD COLUMN IF NOT EXISTS `chunk_mode` VARCHAR(20) DEFAULT 'default' COMMENT '切分策略模式：default-默认，smart-智能切分，custom-自定义',
ADD COLUMN IF NOT EXISTS `smart_config` TEXT COMMENT '智能切分配置（JSON格式）',
ADD COLUMN IF NOT EXISTS `error_message` TEXT COMMENT '处理错误信息';

-- 更新已有记录的默认值
UPDATE `knowledge_document_relation` SET `chunk_mode` = 'default' WHERE `chunk_mode` IS NULL;
