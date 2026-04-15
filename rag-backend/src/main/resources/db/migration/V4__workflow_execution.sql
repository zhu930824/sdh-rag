-- ============================================
-- 工作流执行记录表
-- Flyway Migration V4
-- ============================================

SET NAMES utf8mb4;

-- 工作流执行记录表
CREATE TABLE IF NOT EXISTS `workflow_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行记录ID',
  `workflow_id` bigint NOT NULL COMMENT '工作流ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '执行用户ID',
  `execution_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '执行唯一标识(UUID)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '状态: pending-待执行, running-执行中, success-成功, failed-失败, cancelled-已取消',
  `input_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输入数据JSON',
  `output_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输出数据JSON',
  `node_results` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '节点执行结果JSON',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `duration` int NULL DEFAULT 0 COMMENT '执行耗时(毫秒)',
  `total_tokens` int NULL DEFAULT 0 COMMENT '总Token消耗',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_workflow_id`(`workflow_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_execution_id`(`execution_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_start_time`(`start_time` ASC) USING BTREE,
  CONSTRAINT `fk_execution_workflow` FOREIGN KEY (`workflow_id`) REFERENCES `workflow` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流执行记录表' ROW_FORMAT = Dynamic;