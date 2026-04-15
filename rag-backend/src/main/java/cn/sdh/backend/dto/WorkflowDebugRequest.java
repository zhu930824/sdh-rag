package cn.sdh.backend.dto;

import lombok.Data;
import java.util.Map;

/**
 * 工作流节点调试请求
 */
@Data
public class WorkflowDebugRequest {

    /**
     * 要调试的节点ID
     */
    private String nodeId;

    /**
     * 输入数据
     */
    private Map<String, Object> inputs;
}