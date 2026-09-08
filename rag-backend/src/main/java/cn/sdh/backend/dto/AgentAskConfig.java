package cn.sdh.backend.dto;

import lombok.Data;

/**
 * Agent 模式请求配置(随 AskRequest 传入)
 */
@Data
public class AgentAskConfig {

    /** 是否启用 Agent 模式(LLM 自主决策调用受控业务工具) */
    private Boolean enabled = false;

    /** 最大决策步数(默认 8,上限 20) */
    private Integer maxSteps;
}
