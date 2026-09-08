package cn.sdh.backend.demo;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.service.AgentToolHttpClient;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 内置演示工具直调分发器:agent_tool.endpoint = internal://demo/<method> 时,
 * 不经过 HTTP/SSRF 层(AgentToolHttpClient 拒绝回环地址,自调本机 HTTP 不可行),
 * 治理(审批/幂等/审计/截断)仍完整复用 AgentActionService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalToolDispatcher {

    private static final String SCHEME = "internal://";

    private final DemoBusinessService demoService;

    public static boolean isInternal(String endpoint) {
        return endpoint != null && endpoint.trim().startsWith(SCHEME);
    }

    public AgentToolHttpClient.ToolResponse execute(AgentTool tool, Map<String, Object> parameters, Long actor) {
        String target = tool.getEndpoint().trim().substring(SCHEME.length());
        Object result;
        switch (target) {
            case "demo/queryOrders" -> result = demoService.queryOrders(parameters);
            case "demo/queryInventory" -> result = demoService.queryInventory(parameters);
            case "demo/createServiceTicket" -> result = demoService.createServiceTicket(parameters, actor);
            case "demo/updateTicketStatus" -> result = demoService.updateTicketStatus(parameters, actor);
            case "demo/queryTickets" -> result = demoService.queryTickets(parameters);
            default -> throw new BusinessException(400, "未知的内置演示业务方法: " + target);
        }
        return new AgentToolHttpClient.ToolResponse(200, JSON.toJSONString(result));
    }
}
