-- 评估任务表
CREATE TABLE IF NOT EXISTS `evaluation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识库ID',
  `task_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `qa_count` int NULL DEFAULT 0 COMMENT 'QA数量',
  `hit_rate` decimal(5, 4) NULL DEFAULT NULL COMMENT '命中率',
  `mrr` decimal(5, 4) NULL DEFAULT NULL COMMENT '平均倒数排名',
  `avg_recall` decimal(5, 4) NULL DEFAULT NULL COMMENT '平均召回率',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态: 0-待运行, 1-运行中, 2-完成, 3-失败',
  `config_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '配置快照JSON',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_knowledge_id`(`knowledge_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评估任务表' ROW_FORMAT = Dynamic;

-- 评估QA表
CREATE TABLE IF NOT EXISTS `evaluation_qa` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'QA ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题',
  `expected_answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '期望答案',
  `source_chunk_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源分块ID',
  `source_chunk_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源分块内容',
  `retrieved_chunk_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '检索到的分块ID列表JSON',
  `hit` tinyint(1) NULL DEFAULT 0 COMMENT '是否命中',
  `hit_rank` int NULL DEFAULT NULL COMMENT '命中排名',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE,
  INDEX `idx_hit`(`hit` ASC) USING BTREE,
  CONSTRAINT `fk_qa_task` FOREIGN KEY (`task_id`) REFERENCES `evaluation_task` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评估QA表' ROW_FORMAT = Dynamic;