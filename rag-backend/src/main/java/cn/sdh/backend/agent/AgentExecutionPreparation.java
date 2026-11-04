package cn.sdh.backend.agent;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.ModelConfigService;
import cn.sdh.backend.service.factory.ChatModelFactory;
import cn.sdh.backend.service.factory.ModelProvider;
import cn.sdh.backend.service.impl.ChatServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import java.util.List;

/** The same model policy and system context are used for initial execution, approval resume and recovery. */
@Service
@RequiredArgsConstructor
public class AgentExecutionPreparation {
    private final ModelConfigService configs;
    private final ChatModelFactory models;
    private final AgentCapability capability;
    private final ChatServiceImpl chat;
    public enum Purpose { INITIAL, RESUME }
    public record ModelBinding(ModelConfig config, ModelProvider provider, ChatModel model) {
        public ModelBinding { config = copy(config); }
        @Override public ModelConfig config() { return copy(config); }
        @Override public String toString() { return "ModelBinding[configId=" + config.getId() + ", provider=" + provider + "]"; }
        private static ModelConfig copy(ModelConfig original) {
            var copy = new ModelConfig(); org.springframework.beans.BeanUtils.copyProperties(original, copy); return copy;
        }
    }

    public ModelBinding model(Long configId, Purpose purpose) {
        boolean resume = purpose == Purpose.RESUME;
        ModelConfig config = configId == null ? configs.getDefault() : configs.getById(configId);
        if (config == null) throw new BusinessException(400, resume ? "运行关联的模型配置已不存在,无法续跑" : "模型配置错误,请检查模型设置");
        if (Integer.valueOf(0).equals(config.getStatus())) throw new BusinessException(400, "所选模型配置已停用");
        ModelConfig selected = new ModelConfig(); org.springframework.beans.BeanUtils.copyProperties(config, selected);
        ModelProvider provider = ModelProvider.fromString(config.getProvider());
        if (!capability.supports(provider)) {
            throw new BusinessException(400, "当前模型供应商(" + config.getProvider() + ")" + (resume
                    ? "已不在 Agent 工具调用白名单,无法续跑"
                    : "暂不支持 Agent 工具调用:请在模型管理中选择白名单供应商(默认 DashScope/OpenAI)或关闭 Agent 模式"));
        }
        ChatModel model = models.createConfiguredModel(selected);
        if (model == null) throw new BusinessException(400, resume ? "无法创建聊天模型,无法续跑" : "无法创建聊天模型,请检查模型配置");
        return new ModelBinding(selected, provider, model);
    }

    public String system(String evidence, List<AgentTool> tools, Long userId, String sessionId, String question, boolean memoryEnabled) {
        String base = buildSystemPrompt(evidence, tools);
        return memoryEnabled ? chat.memoryPrompt(base, userId, sessionId, question, true) : base;
    }

    private String buildSystemPrompt(String evidence, List<AgentTool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个\"知识+行动\"型企业业务助手。你可以调用受控业务工具完成任务,并按需参考检索资料。");
        prompt.append("\n\n【可用工具】").append(tools.isEmpty()
                ? "当前没有可用业务工具,仅能回答问题。"
                : "(仅在确有必要时调用;不需要就不要调用)");
        for (AgentTool tool : tools) {
            String desc = tool.getDescription() == null || tool.getDescription().isBlank()
                    ? tool.getName() : tool.getDescription();
            prompt.append("\n- ").append(tool.getCode()).append(": ").append(desc);
        }
        prompt.append("\n\n【执行规则】");
        prompt.append("\n1. 需要真实业务数据(订单/库存/工单等)时先调用工具获取,严禁编造事实或猜测数据。");
        prompt.append("\n2. 工具返回 JSON 含 __agent_pause__ 表示操作已挂起等待审批、执行或核对:立即停止调用任何工具、不得虚构执行结果,如实说明工具返回的等待原因。");
        prompt.append("\n3. 工具返回含 __agent_rejected__:如实告知用户操作已被拒绝及原因,不要重试。");
        prompt.append("\n4. 工具返回含 __agent_error__:如实告知执行失败原因;确有必要可换参数组合重试一次。");
        prompt.append("\n5. 同一工具的相同参数只调用一次;重复请求只会得到既有结果或\"已在审批中\"。");
        prompt.append("\n6. 检索资料与工具结果矛盾时,以工具返回的真实数据为准。");
        prompt.append("\n\n【回答要求】用中文回答,简洁、有条理;涉及金额/订单号/库存/状态等,必须以工具返回为准并注明依据。");
        if (evidence != null && !evidence.isBlank()) {
            prompt.append("\n\n【参考资料】\n").append(evidence);
            prompt.append("\n\n【依据规则】回答优先基于参考资料;资料未覆盖且无工具可查时如实说明不知道。");
        }
        return prompt.toString();
    }

}
