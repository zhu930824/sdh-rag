-- 创建数据库
CREATE DATABASE IF NOT EXISTS sdh_rag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sdh_rag;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 文档分类表
CREATE TABLE IF NOT EXISTS `document_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(500) COMMENT '分类描述',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档分类表';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
    `content` TEXT COMMENT '文档内容',
    `file_type` VARCHAR(20) COMMENT '文件类型',
    `file_path` VARCHAR(500) COMMENT '文件路径',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    `category_id` BIGINT COMMENT '分类ID',
    `user_id` BIGINT NOT NULL COMMENT '上传用户ID',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-处理中，1-成功，2-失败',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_title` (`title`),
    CONSTRAINT `fk_document_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_document_category` FOREIGN KEY (`category_id`) REFERENCES `document_category`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- 聊天历史表
CREATE TABLE IF NOT EXISTS `chat_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
    `question` TEXT NOT NULL COMMENT '问题',
    `answer` TEXT COMMENT '回答',
    `sources` TEXT COMMENT '引用来源（JSON格式）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`),
    CONSTRAINT `fk_chat_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天历史表';

-- 敏感词表
CREATE TABLE IF NOT EXISTS `sensitive_word` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '敏感词ID',
    `word` VARCHAR(100) NOT NULL COMMENT '敏感词',
    `category` VARCHAR(50) NOT NULL COMMENT '分类: politics-政治, porn-色情, violence-暴力, ad-广告',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT COMMENT '用户ID',
    `username` VARCHAR(50) COMMENT '用户名',
    `type` VARCHAR(20) NOT NULL COMMENT '操作类型: login-登录, logout-登出, create-新增, update-修改, delete-删除, query-查询, export-导出, import-导入',
    `content` VARCHAR(500) COMMENT '操作内容',
    `ip` VARCHAR(50) COMMENT 'IP地址',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-失败，1-成功',
    `browser` VARCHAR(100) COMMENT '浏览器',
    `os` VARCHAR(100) COMMENT '操作系统',
    `request_url` VARCHAR(500) COMMENT '请求URL',
    `request_method` VARCHAR(10) COMMENT '请求方法',
    `request_params` TEXT COMMENT '请求参数JSON',
    `response_data` TEXT COMMENT '响应数据JSON',
    `error_msg` TEXT COMMENT '错误信息',
    `duration` BIGINT COMMENT '执行时长(毫秒)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 热点词记录表
CREATE TABLE IF NOT EXISTS `hotword_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `word` VARCHAR(100) NOT NULL COMMENT '关键词',
    `count` INT DEFAULT 1 COMMENT '查询次数',
    `query_date` DATE NOT NULL COMMENT '查询日期',
    `user_id` BIGINT COMMENT '用户ID',
    `session_id` VARCHAR(100) COMMENT '会话ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word_date` (`word`, `query_date`),
    KEY `idx_query_date` (`query_date`),
    KEY `idx_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点词记录表';

-- 系统设置表
CREATE TABLE IF NOT EXISTS `system_settings` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '设置ID',
    `setting_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `setting_value` TEXT COMMENT '配置值JSON',
    `description` VARCHAR(200) COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统设置表';

-- 大模型配置表
CREATE TABLE IF NOT EXISTS `model_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `provider` VARCHAR(50) NOT NULL COMMENT '提供商: openai/anthropic/alibaba/moonshot',
    `model_type` VARCHAR(50) NOT NULL COMMENT '模型类型: chat/embedding/reranker',
    `model_id` VARCHAR(100) NOT NULL COMMENT '模型ID',
    `api_key` VARCHAR(500) COMMENT 'API Key',
    `base_url` VARCHAR(255) COMMENT '自定义接口地址',
    `temperature` DECIMAL(3,1) DEFAULT 0.7 COMMENT '默认温度',
    `max_tokens` INT DEFAULT 2000 COMMENT '最大Token数',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否为默认模型',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `is_local` TINYINT DEFAULT 0 COMMENT '是否为本地模型',
    `is_built_in` TINYINT DEFAULT 0 COMMENT '是否为内置模型',
    `icon` VARCHAR(255) COMMENT '模型图标URL',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_model` (`provider`, `model_id`),
    KEY `idx_provider` (`provider`),
    KEY `idx_status` (`status`),
    KEY `idx_model_type` (`model_type`),
    KEY `idx_is_default` (`is_default`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型配置表';

-- 工作流表
CREATE TABLE IF NOT EXISTS `workflow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作流ID',
    `name` VARCHAR(100) NOT NULL COMMENT '工作流名称',
    `description` VARCHAR(500) COMMENT '描述',
    `flow_data` TEXT COMMENT '流程配置JSON',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `user_id` BIGINT NOT NULL COMMENT '创建用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    CONSTRAINT `fk_workflow_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流表';

-- 工作流节点表
CREATE TABLE IF NOT EXISTS `workflow_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点ID',
    `workflow_id` BIGINT NOT NULL COMMENT '工作流ID',
    `node_type` VARCHAR(50) NOT NULL COMMENT '节点类型: input/retrieval/llm/output/condition',
    `node_name` VARCHAR(100) NOT NULL COMMENT '节点名称',
    `node_config` TEXT COMMENT '节点配置JSON',
    `position_x` INT DEFAULT 0 COMMENT 'X坐标',
    `position_y` INT DEFAULT 0 COMMENT 'Y坐标',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_workflow_id` (`workflow_id`),
    CONSTRAINT `fk_node_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflow`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点表';

-- 嵌入组件配置表
CREATE TABLE IF NOT EXISTS `embed_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `name` VARCHAR(100) NOT NULL COMMENT '配置名称',
    `theme` VARCHAR(20) DEFAULT 'light' COMMENT '主题: light/dark',
    `position` VARCHAR(20) DEFAULT 'right-bottom' COMMENT '位置: right-bottom/left-bottom/right-top/left-top',
    `width` INT DEFAULT 400 COMMENT '宽度',
    `height` INT DEFAULT 600 COMMENT '高度',
    `primary_color` VARCHAR(20) DEFAULT '#1890ff' COMMENT '主题色',
    `title` VARCHAR(100) DEFAULT '智能助手' COMMENT '标题',
    `welcome_msg` VARCHAR(255) DEFAULT '您好，有什么可以帮您？' COMMENT '欢迎语',
    `knowledge_ids` TEXT COMMENT '关联知识库ID列表',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='嵌入组件配置表';

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `description` VARCHAR(200) COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`),
    CONSTRAINT `fk_userrole_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_userrole_role` FOREIGN KEY (`role_id`) REFERENCES `role`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 菜单权限表
CREATE TABLE IF NOT EXISTS `menu_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
    `path` VARCHAR(100) COMMENT '路由路径',
    `component` VARCHAR(100) COMMENT '组件路径',
    `icon` VARCHAR(50) COMMENT '图标',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父菜单ID',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `visible` TINYINT DEFAULT 1 COMMENT '是否可见',
    `permission` VARCHAR(100) COMMENT '权限标识',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_path` (`path`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_visible` (`visible`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS `role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`),
    CONSTRAINT `fk_rolemenu_role` FOREIGN KEY (`role_id`) REFERENCES `role`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rolemenu_menu` FOREIGN KEY (`menu_id`) REFERENCES `menu_permission`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- ==================== 默认数据 ====================

-- 插入默认用户（密码：123456，使用BCrypt加密）
INSERT INTO `user` (`username`, `password`, `nickname`, `status`) VALUES
('admin', '$2a$10$sP2HwA2xtb7r44xmI.5ukO2fw7C6n72WOWyqkbIb/SYQpQP0w7wly', '管理员', 1);

-- 插入默认分类
INSERT INTO `document_category` (`name`, `description`, `parent_id`, `sort`) VALUES
('默认分类', '默认文档分类', NULL, 0),
('技术文档', '技术相关文档', NULL, 1),
('产品文档', '产品相关文档', NULL, 2);

-- 插入默认系统设置
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `description`) VALUES
('basic', '{"systemName":"智能知识库","defaultTheme":"auto"}', '基础设置'),
('model', '{"defaultModel":"gpt-3.5-turbo","temperature":0.7,"maxTokens":2000}', '模型配置'),
('notification', '{"enableNotification":true,"enableEmail":false,"notificationEmail":""}', '通知设置');

-- 插入默认敏感词
INSERT INTO `sensitive_word` (`word`, `category`, `status`) VALUES
('政治敏感词', 'politics', 1),
('色情敏感词', 'porn', 1),
('广告敏感词', 'ad', 1);

-- 插入默认大模型配置
INSERT INTO `model_config` (`name`, `provider`, `model_type`, `model_id`, `status`, `is_default`, `sort`, `is_local`, `is_built_in`) VALUES
('系统默认', 'local', 'chat', 'doubao-1-5-pro-32k-250115', 1, 1, 1, 0, 1),
('bge-small-zh (内置)', 'local', 'embedding', 'bge-small-zh', 1, 0, 2, 1, 1),
('bge-reranker-v2-m3 (内置)', 'local', 'reranker', 'bge-reranker-v2-m3', 1, 0, 3, 1, 1),
('embedding-2', 'openai', 'embedding', 'embedding-2', 1, 0, 4, 0, 0),
('text-embedding-v2', 'alibaba', 'embedding', 'text-embedding-v2', 1, 0, 5, 0, 0),
('nomic-embed-text', 'local', 'embedding', 'nomic-embed-text', 1, 0, 6, 1, 1),
('BAAI/bge-reranker-v2-m3', 'local', 'reranker', 'BAAI/bge-reranker-v2-m3', 1, 0, 7, 0, 1);

-- 插入默认角色
INSERT INTO `role` (`name`, `code`, `description`, `status`) VALUES
('超级管理员', 'admin', '拥有所有权限', 1),
('普通用户', 'user', '普通用户权限', 1),
('访客', 'guest', '只读权限', 1);

-- 插入默认菜单
INSERT INTO `menu_permission` (`id`, `name`, `path`, `component`, `icon`, `parent_id`, `sort`, `visible`) VALUES
(1, '首页', '/dashboard', 'dashboard/index', 'HomeOutlined', NULL, 1, 1),
(2, '知识库', '/knowledge', 'knowledge/index', 'FolderOutlined', NULL, 2, 1),
(3, '智能问答', '/chat', 'chat/index', 'MessageOutlined', NULL, 3, 1),
(4, '用户管理', '/user', 'user/index', 'UserOutlined', NULL, 4, 1),
(5, '敏感词管理', '/sensitive', 'sensitive/index', 'WarningOutlined', NULL, 5, 1),
(6, '日志管理', '/log', 'log/index', 'FileTextOutlined', NULL, 6, 1),
(7, '热点词分析', '/hotwords', 'hotwords/index', 'LineChartOutlined', NULL, 7, 1),
(8, '系统设置', '/settings', 'settings/index', 'SettingOutlined', NULL, 8, 1),
(9, '大模型管理', '/model', 'model/index', 'RobotOutlined', NULL, 9, 1),
(10, '工作流编排', '/workflow', 'workflow/index', 'BranchesOutlined', NULL, 10, 1),
(11, '自然语言查询', '/nlp-query', 'nlp-query/index', 'DatabaseOutlined', NULL, 11, 1),
(12, '嵌入配置', '/embed', 'embed/index', 'LayoutOutlined', NULL, 12, 1);

-- 为admin用户分配超级管理员角色
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 为超级管理员角色分配所有菜单权限
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9), (1, 10), (1, 11), (1, 12);

-- 为普通用户角色分配部分菜单权限（排除用户管理、系统设置等敏感菜单）
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(2, 1), (2, 2), (2, 3), (2, 5), (2, 7), (2, 9), (2, 10), (2, 11), (2, 12);

-- 为访客角色分配只读权限
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(3, 1), (3, 2), (3, 3), (3, 7);

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
    `name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(500) COMMENT '知识库描述',
    `user_id` BIGINT NOT NULL COMMENT '创建用户ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_knowledge_base_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- 知识库文档关联表（多对多）
CREATE TABLE IF NOT EXISTS `knowledge_document_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '知识库ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_document` (`knowledge_id`, `document_id`),
    KEY `idx_knowledge_id` (`knowledge_id`),
    KEY `idx_document_id` (`document_id`),
    CONSTRAINT `fk_relation_knowledge` FOREIGN KEY (`knowledge_id`) REFERENCES `knowledge_base`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_relation_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档关联表';

-- 插入嵌入组件默认配置
INSERT INTO `embed_config` (`name`, `theme`, `position`, `width`, `height`, `title`, `status`) VALUES
('默认配置', 'light', 'right-bottom', 400, 600, '智能助手', 1);