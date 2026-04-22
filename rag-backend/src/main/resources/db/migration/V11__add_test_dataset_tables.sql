-- 测试数据集表
CREATE TABLE IF NOT EXISTS `test_dataset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '数据集描述',
  `item_count` int NULL DEFAULT 0 COMMENT '问题数量',
  `negative_count` int NULL DEFAULT 0 COMMENT '负样本数量',
  `dataset_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'custom' COMMENT '数据集类型: builtin-内置/custom-自定义',
  `file_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '内置数据集文件名',
  `user_id` bigint NULL COMMENT '创建用户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_dataset_type`(`dataset_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试数据集表' ROW_FORMAT = Dynamic;

-- 测试数据集问题项表
CREATE TABLE IF NOT EXISTS `test_dataset_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '问题项ID',
  `dataset_id` bigint NOT NULL COMMENT '数据集ID',
  `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题内容',
  `expected_answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '期望答案',
  `source_chunk_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源分块ID',
  `source_document_id` bigint NULL COMMENT '来源文档ID',
  `is_negative` tinyint(1) NULL DEFAULT 0 COMMENT '是否负样本',
  `external_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '外部ID',
  `sort_order` int NULL DEFAULT 0 COMMENT '排序顺序',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dataset_id`(`dataset_id` ASC) USING BTREE,
  INDEX `idx_is_negative`(`is_negative` ASC) USING BTREE,
  CONSTRAINT `fk_item_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `test_dataset` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试数据集问题项表' ROW_FORMAT = Dynamic;

-- 初始化内置数据集
INSERT INTO `test_dataset` (`name`, `description`, `item_count`, `negative_count`, `dataset_type`, `file_name`)
VALUES ('RAG常见场景测试集', '涵盖精确匹配、语义匹配、多跳推理、负样本和边界场景的综合性测试集', 25, 6, 'builtin', 'rag_scenarios.json');
