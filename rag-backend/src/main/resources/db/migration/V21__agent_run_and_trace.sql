-- ============================================
-- Agent 运行档案 + 执行轨迹落库
-- Flyway Migration V21
-- ============================================

SET NAMES utf8mb4;

-- 1. Agent 运行档案（一次 Agent 任务的执行档案；resume 时以 messages_json 重建 LLM 上下文）
CREATE TABLE agent_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_key VARCHAR(64) NOT NULL COMMENT '对外唯一标识(UUID,去连字符)',
    session_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    history_id BIGINT NULL COMMENT 'chat_history 行 ID',
    knowledge_id BIGINT NULL COMMENT '所用知识库',
    model_id BIGINT NULL COMMENT 'model_config.id',
    question TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RUNNING'
        COMMENT 'RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/REJECTED/CANCELLED',
    max_steps INT NOT NULL DEFAULT 8,
    step_count INT NOT NULL DEFAULT 0,
    pending_action_id BIGINT NULL COMMENT 'WAITING_APPROVAL 时的代表 action',
    messages_json MEDIUMTEXT NULL COMMENT '消息快照(决策续跑重建 LLM 上下文)',
    evidence_text MEDIUMTEXT NULL COMMENT '检索证据截断快照',
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    UNIQUE KEY uk_agent_run_key (run_key),
    KEY idx_agent_run_user (user_id, created_at),
    KEY idx_agent_run_session (session_id),
    KEY idx_agent_run_status (status, created_at)
) COMMENT='Agent 运行档案';

-- 2. Agent 步骤轨迹
CREATE TABLE agent_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    seq INT NOT NULL COMMENT '全局递增,跨类型',
    step_type VARCHAR(16) NOT NULL COMMENT 'llm/tool/retrieval',
    tool_code VARCHAR(80) NULL,
    action_id BIGINT NULL,
    input_json MEDIUMTEXT NULL,
    output_json MEDIUMTEXT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED/PENDING_APPROVAL/REJECTED',
    latency_ms INT NULL,
    tokens INT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_agent_step_seq (run_id, seq),
    KEY idx_agent_step_run (run_id)
) COMMENT='Agent 步骤轨迹';

-- 3. agent_tool 扩展（LLM 可见性所需）
ALTER TABLE agent_tool
    ADD COLUMN input_schema MEDIUMTEXT NULL COMMENT '参数 JSON Schema(LLM 工具声明;缺失时按宽松 schema 声明)',
    ADD COLUMN is_system TINYINT NOT NULL DEFAULT 0 COMMENT '1=内置系统工具,全员可见可调用(仅创建者可改)',
    MODIFY COLUMN description VARCHAR(1000) NULL;

-- 4. agent_action 增加定位列（审批 -> Agent run / workflow execution 续跑路由）
ALTER TABLE agent_action
    ADD COLUMN run_id BIGINT NULL COMMENT 'Agent run id(agent_run.id)',
    ADD COLUMN step_id BIGINT NULL COMMENT 'Agent step id(agent_step.id)',
    ADD COLUMN workflow_execution_id BIGINT NULL COMMENT '工作流执行记录 id(workflow_execution.id)',
    ADD KEY idx_agent_action_run (run_id, status);

-- 5. chat_history 全字段（来源/所用知识库/模型/Agent 痕迹落库与回放）
ALTER TABLE chat_history
    ADD COLUMN knowledge_id BIGINT NULL,
    ADD COLUMN model_id BIGINT NULL,
    ADD COLUMN router_info TEXT NULL COMMENT '意图路由信息 JSON',
    ADD COLUMN agent_run_key VARCHAR(64) NULL,
    ADD COLUMN agent_trace TEXT NULL COMMENT 'Agent 轨迹摘要 JSON(服务端截断)',
    ADD COLUMN update_time DATETIME NULL;

-- 6. workflow_execution 续跑游标（node_results/input_data/output_data 现有列承担续跑种子）
ALTER TABLE workflow_execution
    ADD COLUMN paused_state TEXT NULL COMMENT '暂停游标 JSON({nextNodeId, executed[]})';

-- 7. 演示售后工单（内置演示业务闭环）
CREATE TABLE demo_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(32) NOT NULL COMMENT '工单号',
    customer VARCHAR(100) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    detail TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/RESOLVED/CLOSED',
    created_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_demo_ticket_no (ticket_no),
    KEY idx_demo_ticket_customer (customer, status)
) COMMENT='演示售后工单';
