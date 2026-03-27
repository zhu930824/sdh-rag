-- =====================================================
-- 知识库管理系统 - 功能扩展数据库脚本
-- 执行前请确保已完成init.sql的基础表创建
-- =====================================================

USE sdh_rag;

-- =====================================================
-- 1. 知识图谱可视化
-- =====================================================

-- 知识图谱节点表（文档、实体等）
CREATE TABLE IF NOT EXISTS `graph_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点ID',
    `node_type` VARCHAR(50) NOT NULL COMMENT '节点类型: document/entity/concept/keyword',
    `name` VARCHAR(255) NOT NULL COMMENT '节点名称',
    `description` TEXT COMMENT '节点描述',
    `document_id` BIGINT COMMENT '关联文档ID',
    `properties` TEXT COMMENT '节点属性JSON',
    `weight` INT DEFAULT 1 COMMENT '节点权重(引用次数)',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_node_type` (`node_type`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_name` (`name`),
    KEY `idx_weight` (`weight`),
    CONSTRAINT `fk_graph_node_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱节点表';

-- 知识图谱关系表
CREATE TABLE IF NOT EXISTS `graph_edge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `source_id` BIGINT NOT NULL COMMENT '源节点ID',
    `target_id` BIGINT NOT NULL COMMENT '目标节点ID',
    `relation_type` VARCHAR(50) NOT NULL COMMENT '关系类型: references/contains/related_to/parent_of',
    `weight` DECIMAL(3,2) DEFAULT 1.0 COMMENT '关系权重(相关度)',
    `properties` TEXT COMMENT '关系属性JSON',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_source_id` (`source_id`),
    KEY `idx_target_id` (`target_id`),
    KEY `idx_relation_type` (`relation_type`),
    CONSTRAINT `fk_edge_source` FOREIGN KEY (`source_id`) REFERENCES `graph_node`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_edge_target` FOREIGN KEY (`target_id`) REFERENCES `graph_node`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱关系表';

-- =====================================================
-- 2. 问答评价系统
-- =====================================================

-- 问答评价表
CREATE TABLE IF NOT EXISTS `qa_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `chat_history_id` BIGINT NOT NULL COMMENT '聊天历史ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `rating` TINYINT NOT NULL COMMENT '评分: 1-点赞, 0-点踩',
    `feedback_type` VARCHAR(20) COMMENT '反馈类型: helpful/incorrect/incomplete/irrelevant',
    `comment` TEXT COMMENT '用户反馈内容',
    `correct_answer` TEXT COMMENT '用户提供的正确答案',
    `status` TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理, 1-已处理',
    `handled_by` BIGINT COMMENT '处理人ID',
    `handle_time` DATETIME COMMENT '处理时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_user` (`chat_history_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_rating` (`rating`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_feedback_chat` FOREIGN KEY (`chat_history_id`) REFERENCES `chat_history`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答评价表';

-- =====================================================
-- 3. 数据统计分析
-- =====================================================

-- 统计数据快照表（每日汇总）
CREATE TABLE IF NOT EXISTS `stats_daily` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '统计ID',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `stat_type` VARCHAR(50) NOT NULL COMMENT '统计类型: chat/document/user/api_cost',
    `metric_name` VARCHAR(100) NOT NULL COMMENT '指标名称',
    `metric_value` BIGINT DEFAULT 0 COMMENT '指标值',
    `extra_data` TEXT COMMENT '额外数据JSON',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_type_metric` (`stat_date`, `stat_type`, `metric_name`),
    KEY `idx_stat_date` (`stat_date`),
    KEY `idx_stat_type` (`stat_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计数据快照表';

-- API调用日志表
CREATE TABLE IF NOT EXISTS `api_call_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT COMMENT '用户ID',
    `model_id` BIGINT COMMENT '模型配置ID',
    `api_type` VARCHAR(20) NOT NULL COMMENT 'API类型: chat/embedding/rerank',
    `input_tokens` INT DEFAULT 0 COMMENT '输入Token数',
    `output_tokens` INT DEFAULT 0 COMMENT '输出Token数',
    `total_tokens` INT DEFAULT 0 COMMENT '总Token数',
    `cost` DECIMAL(10,6) DEFAULT 0 COMMENT '调用费用(元)',
    `duration` BIGINT COMMENT '耗时(毫秒)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-失败, 1-成功',
    `error_msg` TEXT COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_model_id` (`model_id`),
    KEY `idx_api_type` (`api_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API调用日志表';

-- =====================================================
-- 4. AI助手市场
-- =====================================================

-- AI助手模板表
CREATE TABLE IF NOT EXISTS `ai_assistant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '助手ID',
    `name` VARCHAR(100) NOT NULL COMMENT '助手名称',
    `description` VARCHAR(500) COMMENT '助手描述',
    `category` VARCHAR(50) COMMENT '分类: customer_service/hr/it_support/legal/finance/general',
    `icon` VARCHAR(255) COMMENT '图标URL',
    `system_prompt` TEXT COMMENT '系统提示词',
    `welcome_message` VARCHAR(500) COMMENT '欢迎语',
    `suggested_questions` TEXT COMMENT '推荐问题JSON',
    `knowledge_ids` VARCHAR(500) COMMENT '关联知识库ID',
    `model_config_id` BIGINT COMMENT '模型配置ID',
    `temperature` DECIMAL(3,2) DEFAULT 0.7 COMMENT '温度参数',
    `max_tokens` INT DEFAULT 2000 COMMENT '最大Token',
    `is_public` TINYINT DEFAULT 1 COMMENT '是否公开: 0-私有, 1-公开',
    `creator_id` BIGINT COMMENT '创建者ID',
    `use_count` INT DEFAULT 0 COMMENT '使用次数',
    `rating_avg` DECIMAL(3,2) DEFAULT 0 COMMENT '平均评分',
    `rating_count` INT DEFAULT 0 COMMENT '评分人数',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_use_count` (`use_count`),
    CONSTRAINT `fk_assistant_creator` FOREIGN KEY (`creator_id`) REFERENCES `user`(`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_assistant_model` FOREIGN KEY (`model_config_id`) REFERENCES `model_config`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI助手模板表';

-- 助手评分表
CREATE TABLE IF NOT EXISTS `assistant_rating` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评分ID',
    `assistant_id` BIGINT NOT NULL COMMENT '助手ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `rating` TINYINT NOT NULL COMMENT '评分: 1-5',
    `comment` VARCHAR(500) COMMENT '评论',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assistant_user` (`assistant_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_rating_assistant` FOREIGN KEY (`assistant_id`) REFERENCES `ai_assistant`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rating_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='助手评分表';

-- =====================================================
-- 5. 多渠道接入
-- =====================================================

-- 渠道配置表
CREATE TABLE IF NOT EXISTS `channel_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '渠道ID',
    `name` VARCHAR(100) NOT NULL COMMENT '渠道名称',
    `channel_type` VARCHAR(20) NOT NULL COMMENT '渠道类型: wechat_work/dingtalk/wechat_mp/api/web',
    `config` TEXT COMMENT '渠道配置JSON(加密存储)',
    `assistant_id` BIGINT COMMENT '关联助手ID',
    `callback_url` VARCHAR(255) COMMENT '回调地址',
    `secret_key` VARCHAR(100) COMMENT '密钥',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_channel_type` (`channel_type`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_channel_assistant` FOREIGN KEY (`assistant_id`) REFERENCES `ai_assistant`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道配置表';

-- =====================================================
-- 6. 智能文档预处理
-- =====================================================

-- 文档处理任务表
CREATE TABLE IF NOT EXISTS `document_process_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `task_type` VARCHAR(20) NOT NULL COMMENT '任务类型: ocr/table_extract/structure/entity_extract',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-处理中, 2-成功, 3-失败',
    `progress` INT DEFAULT 0 COMMENT '进度(0-100)',
    `result` TEXT COMMENT '处理结果JSON',
    `error_msg` TEXT COMMENT '错误信息',
    `processor` VARCHAR(50) COMMENT '处理器',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_task_type` (`task_type`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_task_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档处理任务表';

-- =====================================================
-- 7. 协同审核流程
-- =====================================================

-- 审核流程定义表
CREATE TABLE IF NOT EXISTS `approval_flow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程ID',
    `name` VARCHAR(100) NOT NULL COMMENT '流程名称',
    `flow_type` VARCHAR(20) NOT NULL COMMENT '流程类型: document_publish/content_audit/sensitive_check',
    `description` VARCHAR(500) COMMENT '描述',
    `flow_config` TEXT COMMENT '流程配置JSON(节点、条件等)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_type` (`flow_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核流程定义表';

-- 审核任务表
CREATE TABLE IF NOT EXISTS `approval_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `flow_id` BIGINT NOT NULL COMMENT '流程ID',
    `business_type` VARCHAR(20) NOT NULL COMMENT '业务类型: document/sensitive_word',
    `business_id` BIGINT NOT NULL COMMENT '业务ID',
    `applicant_id` BIGINT NOT NULL COMMENT '申请人ID',
    `current_node` INT DEFAULT 1 COMMENT '当前节点',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待审核, 1-通过, 2-拒绝, 3-已撤回',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_id` (`flow_id`),
    KEY `idx_business` (`business_type`, `business_id`),
    KEY `idx_applicant_id` (`applicant_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_task_flow` FOREIGN KEY (`flow_id`) REFERENCES `approval_flow`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核任务表';

-- 审核记录表
CREATE TABLE IF NOT EXISTS `approval_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `approver_id` BIGINT NOT NULL COMMENT '审核人ID',
    `node_order` INT COMMENT '节点顺序',
    `action` TINYINT NOT NULL COMMENT '操作: 1-通过, 2-拒绝, 3-转交',
    `comment` VARCHAR(500) COMMENT '审核意见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_approver_id` (`approver_id`),
    CONSTRAINT `fk_record_task` FOREIGN KEY (`task_id`) REFERENCES `approval_task`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_record_approver` FOREIGN KEY (`approver_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表';

-- =====================================================
-- 8. 语音问答
-- =====================================================

-- 语音记录表
CREATE TABLE IF NOT EXISTS `voice_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(100) COMMENT '会话ID',
    `voice_url` VARCHAR(255) COMMENT '语音文件URL',
    `voice_duration` INT COMMENT '语音时长(毫秒)',
    `text_content` TEXT COMMENT '识别文本',
    `answer_text` TEXT COMMENT '回答文本',
    `answer_voice_url` VARCHAR(255) COMMENT '回答语音URL',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-失败, 1-成功',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语音记录表';

-- =====================================================
-- 9. 公告通知系统
-- =====================================================

-- 公告表
CREATE TABLE IF NOT EXISTS `announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` TEXT NOT NULL COMMENT '内容',
    `type` VARCHAR(20) DEFAULT 'notice' COMMENT '类型: notice/update/warning',
    `priority` INT DEFAULT 0 COMMENT '优先级(越大越靠前)',
    `target_type` VARCHAR(20) DEFAULT 'all' COMMENT '目标类型: all/role/user',
    `target_ids` VARCHAR(500) COMMENT '目标ID列表(角色ID或用户ID)',
    `publish_time` DATETIME COMMENT '发布时间',
    `expire_time` DATETIME COMMENT '过期时间',
    `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶',
    `publisher_id` BIGINT COMMENT '发布人ID',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-草稿, 1-已发布, 2-已下线',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_publish_time` (`publish_time`),
    CONSTRAINT `fk_announcement_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 公告阅读记录表
CREATE TABLE IF NOT EXISTS `announcement_read` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `announcement_id` BIGINT NOT NULL COMMENT '公告ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_read_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcement`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_read_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告阅读记录表';

-- 消息通知表
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `type` VARCHAR(20) DEFAULT 'system' COMMENT '类型: system/approval/feedback/assistant',
    `related_type` VARCHAR(20) COMMENT '关联类型',
    `related_id` BIGINT COMMENT '关联ID',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读',
    `read_time` DATETIME COMMENT '阅读时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_is_read` (`is_read`),
    CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- =====================================================
-- 10. 积分与激励系统
-- =====================================================

-- 积分记录表
CREATE TABLE IF NOT EXISTS `points_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points` INT NOT NULL COMMENT '积分变动(正数增加,负数减少)',
    `balance` INT NOT NULL COMMENT '变动后余额',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: upload/feedback/login/share/redeem',
    `description` VARCHAR(200) COMMENT '描述',
    `related_type` VARCHAR(20) COMMENT '关联类型',
    `related_id` BIGINT COMMENT '关联ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_create_time` (`create_time`),
    CONSTRAINT `fk_points_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分记录表';

-- 积分规则表
CREATE TABLE IF NOT EXISTS `points_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    `type` VARCHAR(20) NOT NULL COMMENT '类型',
    `name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `points` INT NOT NULL COMMENT '积分值',
    `daily_limit` INT DEFAULT 0 COMMENT '每日上限(0不限制)',
    `description` VARCHAR(200) COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分规则表';

-- 积分兑换商品表
CREATE TABLE IF NOT EXISTS `points_goods` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
    `description` VARCHAR(500) COMMENT '描述',
    `image` VARCHAR(255) COMMENT '图片URL',
    `points` INT NOT NULL COMMENT '所需积分',
    `stock` INT DEFAULT 0 COMMENT '库存',
    `type` VARCHAR(20) DEFAULT 'virtual' COMMENT '类型: virtual/physical',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换商品表';

-- 积分兑换记录表
CREATE TABLE IF NOT EXISTS `points_exchange` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '兑换ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `goods_id` BIGINT NOT NULL COMMENT '商品ID',
    `points` INT NOT NULL COMMENT '消耗积分',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已发货, 2-已完成, 3-已取消',
    `address` VARCHAR(500) COMMENT '收货地址(实物)',
    `remark` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_goods_id` (`goods_id`),
    CONSTRAINT `fk_exchange_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_exchange_goods` FOREIGN KEY (`goods_id`) REFERENCES `points_goods`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换记录表';

-- 用户扩展信息表(包含积分余额)
CREATE TABLE IF NOT EXISTS `user_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points_balance` INT DEFAULT 0 COMMENT '积分余额',
    `total_points` INT DEFAULT 0 COMMENT '累计积分',
    `level` INT DEFAULT 1 COMMENT '用户等级',
    `experience` INT DEFAULT 0 COMMENT '经验值',
    `badge_ids` VARCHAR(500) COMMENT '徽章ID列表',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户扩展信息表';

-- =====================================================
-- 默认数据插入
-- =====================================================

-- 插入积分规则
INSERT INTO `points_rule` (`type`, `name`, `points`, `daily_limit`, `description`, `status`) VALUES
('upload', '上传文档', 10, 100, '每上传一篇文档获得10积分', 1),
('feedback', '提交反馈', 5, 20, '每次提交有效反馈获得5积分', 1),
('login', '每日签到', 2, 2, '每日登录签到获得2积分', 1),
('share', '分享知识', 3, 10, '分享知识获得3积分', 1),
('answer_helpful', '回答被采纳', 20, 0, '回答被用户点赞获得20积分', 1);

-- 插入默认AI助手模板
INSERT INTO `ai_assistant` (`name`, `description`, `category`, `system_prompt`, `welcome_message`, `suggested_questions`, `is_public`, `creator_id`, `status`, `sort`) VALUES
('智能客服助手', '专业的客户服务助手，可处理常见客户咨询、投诉建议等问题', 'customer_service', 
'你是一名专业的客服助手，请用礼貌、专业的语气回答用户问题。对于无法回答的问题，请引导用户联系人工客服。',
'您好！我是智能客服助手，请问有什么可以帮您？',
'["如何修改密码？", "如何联系客服？", "退款流程是什么？"]', 
1, NULL, 1, 1),

('HR招聘助手', '专业的HR招聘助手，可回答面试、入职、薪酬等人事相关问题', 'hr',
'你是一名专业的HR招聘助手，请用专业、亲和的语气回答求职者的问题。',
'您好！我是HR招聘助手，很高兴为您解答招聘相关问题。',
'["公司有哪些福利？", "面试流程是怎样的？", "如何投递简历？"]',
1, NULL, 1, 2),

('IT技术支持', '专业的IT技术支持助手，可解答技术问题、故障排查等', 'it_support',
'你是一名IT技术支持工程师，请用专业的语言帮助用户解决技术问题。',
'您好！我是IT技术支持，请问您遇到了什么技术问题？',
'["电脑无法开机怎么办？", "如何重置密码？", "VPN连接失败如何解决？"]',
1, NULL, 1, 3),

('法律顾问助手', '专业的法律顾问助手，可解答常见法律问题', 'legal',
'你是一名法律顾问助手，请根据法律法规提供参考建议，注意提醒用户重大决策需咨询专业律师。',
'您好！我是法律顾问助手，可为您解答常见法律问题。',
'["劳动合同纠纷如何处理？", "如何申请劳动仲裁？", "试用期可以辞退吗？"]',
1, NULL, 1, 4);

-- 插入默认审核流程
INSERT INTO `approval_flow` (`name`, `flow_type`, `description`, `flow_config`, `status`) VALUES
('文档发布审核', 'document_publish', '文档发布前需经过内容审核', 
'{"nodes":[{"name":"提交","type":"submit"},{"name":"内容审核","type":"approve","assignee":"role:admin"},{"name":"发布","type":"end"}]}', 
1),
('敏感词添加审核', 'sensitive_check', '敏感词添加需管理员审核',
'{"nodes":[{"name":"提交","type":"submit"},{"name":"管理员审核","type":"approve","assignee":"role:admin"},{"name":"生效","type":"end"}]}',
1);

-- 新增菜单权限
INSERT INTO `menu_permission` (`id`, `name`, `path`, `component`, `icon`, `parent_id`, `sort`, `visible`) VALUES
(13, '知识图谱', '/graph', 'graph/index', 'ApartmentOutlined', NULL, 13, 1),
(14, '助手市场', '/assistant', 'assistant/index', 'RobotOutlined', NULL, 14, 1),
(15, '数据统计', '/stats', 'stats/index', 'BarChartOutlined', NULL, 15, 1),
(16, '公告管理', '/announcement', 'announcement/index', 'NotificationOutlined', NULL, 16, 1),
(17, '审核中心', '/approval', 'approval/index', 'AuditOutlined', NULL, 17, 1),
(18, '积分商城', '/points', 'points/index', 'GiftOutlined', NULL, 18, 1),
(19, '渠道管理', '/channel', 'channel/index', 'ApiOutlined', NULL, 19, 1);

-- 更新角色菜单权限
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 13), (1, 14), (1, 15), (1, 16), (1, 17), (1, 18), (1, 19);

-- 插入示例公告
INSERT INTO `announcement` (`title`, `content`, `type`, `priority`, `publish_time`, `status`, `publisher_id`) VALUES
('欢迎使用智能知识库系统', '欢迎使用智能知识库系统！本系统提供知识管理、智能问答、工作流编排等功能，助力企业知识沉淀与智能应用。', 'notice', 1, NOW(), 1, 1),
('系统更新通知', '系统已升级至v2.0版本，新增知识图谱、助手市场、积分系统等功能模块。', 'update', 0, NOW(), 1, 1);

-- 插入示例积分商品
INSERT INTO `points_goods` (`name`, `description`, `points`, `stock`, `type`, `status`, `sort`) VALUES
('10元话费充值卡', '可充值到任意手机号', 1000, 100, 'virtual', 1, 1),
('精美笔记本', '定制logo笔记本一本', 500, 50, 'physical', 1, 2),
('会员月卡', '获得一个月高级会员权益', 2000, 999, 'virtual', 1, 3);

-- =====================================================
-- 11. 文档版本管理
-- =====================================================

-- 文档版本表
CREATE TABLE IF NOT EXISTS `document_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `version_number` INT NOT NULL COMMENT '版本号',
    `content` LONGTEXT COMMENT '版本内容',
    `change_summary` VARCHAR(500) COMMENT '变更摘要',
    `user_id` BIGINT NOT NULL COMMENT '操作用户ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-草稿, 1-发布',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_version_number` (`version_number`),
    CONSTRAINT `fk_version_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_version_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档版本表';

-- =====================================================
-- 12. 智能标注系统
-- =====================================================

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `color` VARCHAR(20) COMMENT '标签颜色',
    `category` VARCHAR(50) COMMENT '标签分类',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- 文档标签关联表
CREATE TABLE IF NOT EXISTS `document_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `source` VARCHAR(20) DEFAULT 'manual' COMMENT '来源: manual-手动, auto-自动',
    `confidence` DECIMAL(5,4) COMMENT '置信度(自动标注)',
    `user_id` BIGINT COMMENT '操作用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_tag` (`document_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`),
    CONSTRAINT `fk_doctag_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_doctag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档标签关联表';

-- 实体标注表
CREATE TABLE IF NOT EXISTS `entity_annotation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标注ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `entity_type` VARCHAR(50) NOT NULL COMMENT '实体类型: person/org/location/date/keyword',
    `entity_value` VARCHAR(255) NOT NULL COMMENT '实体值',
    `start_position` INT COMMENT '起始位置',
    `end_position` INT COMMENT '结束位置',
    `confidence` DECIMAL(5,4) COMMENT '置信度',
    `source` VARCHAR(20) DEFAULT 'auto' COMMENT '来源: manual/auto',
    `user_id` BIGINT COMMENT '操作用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_entity_type` (`entity_type`),
    CONSTRAINT `fk_annotation_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体标注表';

-- 新增菜单权限
INSERT INTO `menu_permission` (`id`, `name`, `path`, `component`, `icon`, `parent_id`, `sort`, `visible`) VALUES
(20, '文档预处理', '/process-task', 'process-task/index', 'CloudUploadOutlined', NULL, 20, 1),
(21, '标签管理', '/tag', 'tag/index', 'TagsOutlined', NULL, 21, 1);

-- 更新角色菜单权限
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 20), (1, 21);

-- 插入默认标签
INSERT INTO `tag` (`name`, `color`, `category`, `sort`, `status`) VALUES
('技术文档', 'blue', '文档类型', 1, 1),
('产品文档', 'green', '文档类型', 2, 1),
('操作手册', 'orange', '文档类型', 3, 1),
('API文档', 'purple', '文档类型', 4, 1),
('FAQ', 'cyan', '文档类型', 5, 1);

-- =====================================================
-- 13. Prompt模板管理
-- =====================================================

CREATE TABLE IF NOT EXISTS `prompt_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `name` VARCHAR(100) NOT NULL COMMENT '模板名称',
    `code` VARCHAR(50) NOT NULL COMMENT '模板编码(唯一标识)',
    `category` VARCHAR(50) DEFAULT 'general' COMMENT '模板分类: general/qa/summary/translate/chat',
    `content` TEXT NOT NULL COMMENT '模板内容(支持变量: {context}, {question}, {history}等)',
    `variables` TEXT COMMENT '变量说明(JSON格式)',
    `description` VARCHAR(500) COMMENT '模板描述',
    `is_system` TINYINT DEFAULT 0 COMMENT '是否系统模板: 0-否, 1-是(不可删除)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `use_count` INT DEFAULT 0 COMMENT '使用次数',
    `user_id` BIGINT COMMENT '创建者ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_category` (`category`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt模板表';

-- 插入系统默认模板
INSERT INTO `prompt_template` (`name`, `code`, `category`, `content`, `variables`, `description`, `is_system`, `status`) VALUES
('标准RAG问答', 'rag_qa', 'qa', '你是一个智能助手。请根据以下上下文回答用户问题。\n\n上下文：\n{context}\n\n问题：{question}\n\n请给出准确、简洁的回答。如果上下文中没有相关信息，请明确说明。', '[{"name":"context","desc":"检索到的相关上下文"},{"name":"question","desc":"用户问题"}]', '标准的RAG问答模板', 1, 1),
('对话模式', 'chat', 'chat', '你是一个友好的AI助手。请根据对话历史和上下文与用户进行自然对话。\n\n对话历史：\n{history}\n\n当前上下文：\n{context}\n\n用户消息：{question}\n\n请给出自然、友好的回复。', '[{"name":"history","desc":"对话历史"},{"name":"context","desc":"相关上下文"},{"name":"question","desc":"用户消息"}]', '带历史记录的对话模板', 1, 1),
('文档摘要', 'summary', 'summary', '请对以下内容进行摘要：\n\n{content}\n\n要求：\n1. 提取核心要点\n2. 保持简洁明了\n3. 保留关键信息', '[{"name":"content","desc":"待摘要的内容"}]', '文档摘要生成模板', 1, 1),
('翻译助手', 'translate', 'translate', '请将以下内容翻译成{target_language}：\n\n{content}\n\n翻译要求：\n1. 准确传达原意\n2. 语言流畅自然\n3. 保持专业术语的准确性', '[{"name":"target_language","desc":"目标语言"},{"name":"content","desc":"待翻译内容"}]', '翻译模板', 1, 1);

-- =====================================================
-- 14. API限流与配额管理
-- =====================================================

CREATE TABLE IF NOT EXISTS `api_quota` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配额ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `quota_type` VARCHAR(50) NOT NULL COMMENT '配额类型: chat/embedding/document',
    `daily_limit` INT DEFAULT 100 COMMENT '每日限制次数',
    `monthly_limit` INT DEFAULT 3000 COMMENT '每月限制次数',
    `daily_used` INT DEFAULT 0 COMMENT '今日已使用',
    `monthly_used` INT DEFAULT 0 COMMENT '本月已使用',
    `reset_date` DATE COMMENT '重置日期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_type` (`user_id`, `quota_type`),
    KEY `idx_reset_date` (`reset_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API配额表';

CREATE TABLE IF NOT EXISTS `rate_limit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `api_type` VARCHAR(50) NOT NULL COMMENT 'API类型',
    `endpoint` VARCHAR(255) COMMENT 'API端点',
    `ip_address` VARCHAR(50) COMMENT 'IP地址',
    `request_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
    `response_time` INT COMMENT '响应时间(毫秒)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-限流拒绝, 1-正常',
    KEY `idx_user_time` (`user_id`, `request_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API限流日志表';

-- =====================================================
-- 15. 聊天会话管理
-- =====================================================

CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话标识',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) COMMENT '会话标题',
    `model_id` BIGINT COMMENT '使用的模型ID',
    `prompt_template_id` BIGINT COMMENT '使用的Prompt模板ID',
    `message_count` INT DEFAULT 0 COMMENT '消息数量',
    `total_tokens` INT DEFAULT 0 COMMENT '总Token消耗',
    `is_starred` TINYINT DEFAULT 0 COMMENT '是否收藏',
    `is_archived` TINYINT DEFAULT 0 COMMENT '是否归档',
    `last_message_time` DATETIME COMMENT '最后消息时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_last_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE IF NOT EXISTS `session_share` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分享ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话标识',
    `user_id` BIGINT NOT NULL COMMENT '分享者ID',
    `share_code` VARCHAR(50) NOT NULL COMMENT '分享码',
    `password` VARCHAR(50) COMMENT '访问密码',
    `expire_time` DATETIME COMMENT '过期时间',
    `view_count` INT DEFAULT 0 COMMENT '浏览次数',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-已关闭, 1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_code` (`share_code`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话分享表';

-- =====================================================
-- 16. 定时任务管理
-- =====================================================

CREATE TABLE IF NOT EXISTS `scheduled_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `name` VARCHAR(100) NOT NULL COMMENT '任务名称',
    `task_type` VARCHAR(50) NOT NULL COMMENT '任务类型: sync_document/reindex/cleanup/report',
    `cron_expression` VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    `params` TEXT COMMENT '任务参数(JSON格式)',
    `description` VARCHAR(500) COMMENT '任务描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-暂停, 1-运行',
    `last_execute_time` DATETIME COMMENT '上次执行时间',
    `next_execute_time` DATETIME COMMENT '下次执行时间',
    `last_result` TEXT COMMENT '上次执行结果',
    `success_count` INT DEFAULT 0 COMMENT '成功次数',
    `fail_count` INT DEFAULT 0 COMMENT '失败次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_type` (`task_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';

CREATE TABLE IF NOT EXISTS `task_execute_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `status` TINYINT COMMENT '状态: 0-失败, 1-成功',
    `result` TEXT COMMENT '执行结果',
    `error_msg` TEXT COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行日志表';

-- =====================================================
-- 17. 文档对比
-- =====================================================

CREATE TABLE IF NOT EXISTS `document_comparison` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '对比ID',
    `document_id_1` BIGINT NOT NULL COMMENT '文档1ID',
    `document_id_2` BIGINT NOT NULL COMMENT '文档2ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `comparison_type` VARCHAR(50) DEFAULT 'text' COMMENT '对比类型: text/semantic/structure',
    `similarity_score` DECIMAL(5,4) COMMENT '相似度分数(0-1)',
    `diff_result` LONGTEXT COMMENT '对比结果(JSON格式)',
    `summary` TEXT COMMENT '对比摘要',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_ids` (`document_id_1`, `document_id_2`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档对比表';

-- =====================================================
-- 18. 知识库健康检查
-- =====================================================

CREATE TABLE IF NOT EXISTS `knowledge_health` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '检查ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '知识库ID',
    `check_type` VARCHAR(50) NOT NULL COMMENT '检查类型: document_count/embedding_quality/index_status',
    `score` DECIMAL(5,2) COMMENT '健康分数(0-100)',
    `details` TEXT COMMENT '检查详情(JSON格式)',
    `suggestions` TEXT COMMENT '优化建议(JSON格式)',
    `check_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '检查时间',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_time` (`knowledge_id`, `check_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库健康检查表';

-- =====================================================
-- 19. Webhook回调
-- =====================================================

CREATE TABLE IF NOT EXISTS `webhook_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Webhook ID',
    `name` VARCHAR(100) NOT NULL COMMENT '名称',
    `url` VARCHAR(500) NOT NULL COMMENT '回调URL',
    `secret` VARCHAR(255) COMMENT '签名密钥',
    `events` VARCHAR(500) NOT NULL COMMENT '订阅事件(JSON数组)',
    `headers` TEXT COMMENT '自定义请求头(JSON格式)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `last_trigger_time` DATETIME COMMENT '最后触发时间',
    `fail_count` INT DEFAULT 0 COMMENT '连续失败次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook配置表';

CREATE TABLE IF NOT EXISTS `webhook_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `webhook_id` BIGINT NOT NULL COMMENT 'Webhook ID',
    `event` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `payload` TEXT COMMENT '请求体',
    `response` TEXT COMMENT '响应内容',
    `status_code` INT COMMENT 'HTTP状态码',
    `success` TINYINT COMMENT '是否成功',
    `duration` INT COMMENT '耗时(毫秒)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_webhook_id` (`webhook_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook日志表';

-- 新增菜单权限
INSERT INTO `menu_permission` (`id`, `name`, `path`, `component`, `icon`, `parent_id`, `sort`, `visible`) VALUES
(22, 'Prompt模板', '/prompt', 'prompt/index', 'EditOutlined', NULL, 22, 1),
(23, '会话管理', '/session', 'session/index', 'MessageOutlined', NULL, 23, 1),
(24, '定时任务', '/scheduled-task', 'scheduled-task/index', 'ClockCircleOutlined', NULL, 24, 1),
(25, 'Webhook管理', '/webhook', 'webhook/index', 'ApiOutlined', NULL, 25, 1);

-- 更新角色菜单权限
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 22), (1, 23), (1, 24), (1, 25);

-- 为所有用户初始化默认配额
INSERT INTO `api_quota` (`user_id`, `quota_type`, `daily_limit`, `monthly_limit`, `reset_date`)
SELECT id, 'chat', 100, 3000, CURDATE() FROM `user` WHERE status = 1;