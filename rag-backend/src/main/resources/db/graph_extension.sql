-- =====================================================
-- 知识图谱扩展数据库脚本
-- Neo4j 图数据库的 MySQL 同步表结构
-- =====================================================

USE sdh_rag;

-- 图谱配置表
CREATE TABLE IF NOT EXISTS `graph_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值JSON',
    `description` VARCHAR(200) COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图谱配置表';

-- 实体类型配置表
CREATE TABLE IF NOT EXISTS `entity_type_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '类型ID',
    `type_code` VARCHAR(50) NOT NULL COMMENT '类型编码',
    `type_name` VARCHAR(100) NOT NULL COMMENT '类型名称',
    `color` VARCHAR(20) COMMENT '显示颜色',
    `icon` VARCHAR(100) COMMENT '图标',
    `description` VARCHAR(200) COMMENT '描述',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体类型配置表';

-- 关系类型配置表
CREATE TABLE IF NOT EXISTS `relation_type_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `type_code` VARCHAR(50) NOT NULL COMMENT '关系编码',
    `type_name` VARCHAR(100) NOT NULL COMMENT '关系名称',
    `source_types` VARCHAR(500) COMMENT '源节点类型（逗号分隔）',
    `target_types` VARCHAR(500) COMMENT '目标节点类型（逗号分隔）',
    `color` VARCHAR(20) COMMENT '显示颜色',
    `line_style` VARCHAR(20) DEFAULT 'solid' COMMENT '线条样式: solid/dashed/dotted',
    `description` VARCHAR(200) COMMENT '描述',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关系类型配置表';

-- 图谱构建日志表
CREATE TABLE IF NOT EXISTS `graph_build_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `build_type` VARCHAR(20) DEFAULT 'auto' COMMENT '构建类型: auto/manual/rebuild',
    `entity_count` INT DEFAULT 0 COMMENT '实体数量',
    `relation_count` INT DEFAULT 0 COMMENT '关系数量',
    `concept_count` INT DEFAULT 0 COMMENT '概念数量',
    `keyword_count` INT DEFAULT 0 COMMENT '关键词数量',
    `duration` BIGINT COMMENT '耗时(毫秒)',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-失败，1-成功',
    `error_msg` TEXT COMMENT '错误信息',
    `user_id` BIGINT COMMENT '操作用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图谱构建日志表';

-- =====================================================
-- 默认数据插入
-- =====================================================

-- 插入图谱配置
INSERT INTO `graph_config` (`config_key`, `config_value`, `description`) VALUES
('display', '{"maxNodes":200,"maxEdges":500,"defaultDepth":2}', '图谱显示配置'),
('layout', '{"type":"force","gravity":1,"edgeLength":150}', '图谱布局配置'),
('extraction', '{"minConfidence":0.6,"maxEntities":100}', '实体提取配置');

-- 插入实体类型配置
INSERT INTO `entity_type_config` (`type_code`, `type_name`, `color`, `icon`, `description`, `sort`, `status`) VALUES
('PERSON', '人物', '#52c41a', 'UserOutlined', '人物实体', 1, 1),
('ORG', '组织机构', '#1890ff', 'HomeOutlined', '组织机构实体', 2, 1),
('LOCATION', '地点', '#fa8c16', 'EnvironmentOutlined', '地理位置实体', 3, 1),
('DATE', '日期', '#722ed1', 'CalendarOutlined', '日期时间实体', 4, 1),
('MISC', '其他', '#8c8c8c', 'QuestionCircleOutlined', '其他类型实体', 5, 1),
('DOCUMENT', '文档', '#13c2c2', 'FileOutlined', '文档节点', 10, 1),
('CONCEPT', '概念', '#eb2f96', 'BulbOutlined', '概念节点', 11, 1),
('KEYWORD', '关键词', '#faad14', 'TagOutlined', '关键词节点', 12, 1);

-- 插入关系类型配置
INSERT INTO `relation_type_config` (`type_code`, `type_name`, `source_types`, `target_types`, `color`, `line_style`, `description`, `sort`, `status`) VALUES
('CONTAINS', '包含', 'DOCUMENT', 'ENTITY,KEYWORD', '#1890ff', 'solid', '文档包含实体/关键词', 1, 1),
('REFERENCES', '引用', 'DOCUMENT', 'CONCEPT', '#52c41a', 'solid', '文档引用概念', 2, 1),
('RELATED_TO', '相关', 'ENTITY', 'ENTITY', '#fa8c16', 'dashed', '实体之间的关联关系', 3, 1),
('WORKS_FOR', '工作于', 'PERSON', 'ORG', '#722ed1', 'solid', '人物在组织工作', 4, 1),
('LOCATED_IN', '位于', 'ENTITY', 'LOCATION', '#13c2c2', 'solid', '实体位于某地点', 5, 1),
('INSTANCE_OF', '实例', 'ENTITY', 'CONCEPT', '#eb2f96', 'dashed', '实体是概念的实例', 6, 1),
('PARENT_OF', '父子', 'CONCEPT', 'CONCEPT', '#faad14', 'solid', '概念的层级关系', 7, 1);
