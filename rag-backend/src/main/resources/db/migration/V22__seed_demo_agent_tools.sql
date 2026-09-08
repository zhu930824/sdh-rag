-- ============================================
-- 内置演示业务工具种子(V21 演示闭环配套)
-- Flyway Migration V22
-- is_system=1 全员可见可调用;created_by=1(admin) 保有变更权
-- 需要审批的工具有效的前提:用户 id=1 存在且启用的管理员作为审批人
-- ============================================

SET NAMES utf8mb4;

INSERT INTO agent_tool (
    name, code, description, endpoint, http_method, credential_ref, auth_type, auth_header,
    risk_level, approval_mode, approver_id, timeout_seconds,
    allow_private_network, allow_insecure_http, status, created_by,
    input_schema, is_system, create_time, update_time
) VALUES
(
    '订单查询', 'demo_query_order',
    '按客户姓名查询该客户的订单列表(订单号/商品/金额/状态/日期)。用于核实客户订单信息,创建售后工单前必须先查订单确认订单号归属。',
    'internal://demo/queryOrders', 'POST', NULL, 'none', NULL,
    'LOW', 'NEVER', 1, 30, 0, 0, 1, 1,
    '{"type":"object","properties":{"customer":{"type":"string","description":"客户姓名,如:张三"}},"required":["customer"]}',
    1, NOW(), NOW()
),
(
    '库存查询', 'demo_query_inventory',
    '按商品名称查询当前库存数量(SKU 级)。回答库存问题时使用本工具返回的真实数量。',
    'internal://demo/queryInventory', 'POST', NULL, 'none', NULL,
    'LOW', 'NEVER', 1, 30, 0, 0, 1, 1,
    '{"type":"object","properties":{"product":{"type":"string","description":"商品名称,如:智能手表"}},"required":["product"]}',
    1, NOW(), NOW()
),
(
    '创建售后工单', 'demo_create_service_ticket',
    '为客户创建售后工单(需要审批)。参数:customer 客户姓名、orderNo 订单号(必须先用订单查询核实)、title 问题标题、detail 问题描述。创建成功后返回工单号与状态。',
    'internal://demo/createServiceTicket', 'POST', NULL, 'none', NULL,
    'HIGH', 'HIGH_RISK', 1, 30, 0, 0, 1, 1,
    '{"type":"object","properties":{"customer":{"type":"string","description":"客户姓名"},"orderNo":{"type":"string","description":"订单号,须先经订单查询核实"},"title":{"type":"string","description":"问题标题"},"detail":{"type":"string","description":"问题详细描述"}},"required":["customer","orderNo","title"]}',
    1, NOW(), NOW()
),
(
    '更新售后工单状态', 'demo_update_ticket_status',
    '推进售后工单状态(PROCESS 处理中 → RESOLVE 已解决 → CLOSE 已关闭)。需要审批。',
    'internal://demo/updateTicketStatus', 'POST', NULL, 'none', NULL,
    'MEDIUM', 'HIGH_RISK', 1, 30, 0, 0, 1, 1,
    '{"type":"object","properties":{"ticketNo":{"type":"string","description":"工单号,如:ST20260701-0001"},"action":{"type":"string","enum":["PROCESS","RESOLVE","CLOSE"],"description":"目标动作"}},"required":["ticketNo","action"]}',
    1, NOW(), NOW()
);
