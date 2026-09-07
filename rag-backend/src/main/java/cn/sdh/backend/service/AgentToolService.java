package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.AgentToolMapper;
import cn.sdh.backend.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AgentToolService {
    private final AgentToolMapper tools;
    private final UserMapper users;
    private final AgentToolHttpClient httpClient;

    public record ToolCommand(String name, String code, String description, String endpoint, String httpMethod,
                              String credentialRef, String authType, String authHeader, String riskLevel,
                              String approvalMode, Long approverId, Integer timeoutSeconds,
                              Boolean allowPrivateNetwork, Boolean allowInsecureHttp, Integer status) {}

    public List<AgentTool> list() {
        return tools.selectList(new LambdaQueryWrapper<AgentTool>()
                .eq(AgentTool::getCreatedBy, requireActor()).orderByDesc(AgentTool::getCreateTime));
    }

    public AgentTool create(ToolCommand command) {
        Long actor = requireActor();
        AgentTool tool = apply(new AgentTool(), command, actor);
        tool.setCreatedBy(actor);
        tool.setCreateTime(LocalDateTime.now());
        tool.setUpdateTime(LocalDateTime.now());
        ensureCodeAvailable(tool.getCode(), actor, null);
        tools.insert(tool);
        return tool;
    }

    public AgentTool update(Long id, ToolCommand command) {
        AgentTool tool = requireOwner(id, requireActor());
        apply(tool, command, tool.getCreatedBy());
        ensureCodeAvailable(tool.getCode(), tool.getCreatedBy(), id);
        tool.setUpdateTime(LocalDateTime.now());
        tools.updateById(tool);
        return tool;
    }

    public void disable(Long id) {
        AgentTool tool = requireOwner(id, requireActor());
        tool.setStatus(0);
        tool.setUpdateTime(LocalDateTime.now());
        tools.updateById(tool);
    }

    public AgentTool requireUsable(Long id, Long actor) {
        AgentTool tool = requireOwner(id, actor);
        if (!Integer.valueOf(1).equals(tool.getStatus())) throw new BusinessException(400, "业务工具已停用");
        return tool;
    }

    public AgentTool requireOwner(Long id, Long actor) {
        AgentTool tool = tools.selectById(id);
        if (tool == null || actor == null || !actor.equals(tool.getCreatedBy())) {
            throw new BusinessException(404, "业务工具不存在");
        }
        return tool;
    }

    private AgentTool apply(AgentTool tool, ToolCommand command, Long actor) {
        if (command == null || blank(command.name()) || blank(command.code()) || blank(command.endpoint())) {
            throw new BusinessException(400, "工具名称、编码和 endpoint 不能为空");
        }
        String code = command.code().trim();
        if (!code.matches("[A-Za-z][A-Za-z0-9_-]{1,79}")) throw new BusinessException(400, "工具编码格式不正确");
        tool.setName(limit(command.name().trim(), 120, "工具名称"));
        tool.setCode(code);
        tool.setDescription(limitNullable(command.description(), 500, "工具描述"));
        tool.setEndpoint(limit(command.endpoint().trim(), 2000, "endpoint"));
        tool.setHttpMethod(command.httpMethod());
        tool.setCredentialRef(limitNullable(command.credentialRef(), 200, "凭据引用"));
        tool.setAuthType(command.authType());
        tool.setAuthHeader(limitNullable(command.authHeader(), 64, "认证请求头"));
        tool.setRiskLevel(enumValue(command.riskLevel(), "MEDIUM", List.of("LOW", "MEDIUM", "HIGH"), "风险等级"));
        tool.setApprovalMode(enumValue(command.approvalMode(), "HIGH_RISK", List.of("NEVER", "HIGH_RISK", "ALWAYS"), "审批模式"));
        tool.setApproverId(command.approverId() == null ? actor : command.approverId());
        User approver = users.selectById(tool.getApproverId());
        if (approver == null || !Integer.valueOf(1).equals(approver.getStatus())) throw new BusinessException(400, "审批人不存在或已停用");
        tool.setTimeoutSeconds(command.timeoutSeconds());
        tool.setAllowPrivateNetwork(Boolean.TRUE.equals(command.allowPrivateNetwork()) ? 1 : 0);
        tool.setAllowInsecureHttp(Boolean.TRUE.equals(command.allowInsecureHttp()) ? 1 : 0);
        tool.setStatus(command.status() == null || command.status() != 0 ? 1 : 0);
        httpClient.validateDefinition(tool);
        return tool;
    }

    private void ensureCodeAvailable(String code, Long actor, Long excludedId) {
        LambdaQueryWrapper<AgentTool> query = new LambdaQueryWrapper<AgentTool>()
                .eq(AgentTool::getCreatedBy, actor).eq(AgentTool::getCode, code);
        if (excludedId != null) query.ne(AgentTool::getId, excludedId);
        if (tools.selectCount(query) > 0) throw new BusinessException(409, "工具编码已存在");
    }

    private Long requireActor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new BusinessException(401, "请先登录");
        return actor;
    }

    private String enumValue(String value, String defaultValue, List<String> allowed, String label) {
        String normalized = blank(value) ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BusinessException(400, label + "不正确");
        return normalized;
    }

    private String limit(String value, int max, String label) {
        if (value.length() > max) throw new BusinessException(400, label + "不能超过 " + max + " 个字符");
        return value;
    }

    private String limitNullable(String value, int max, String label) {
        if (blank(value)) return null;
        return limit(value.trim(), max, label);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
