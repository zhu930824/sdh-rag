package cn.sdh.backend.workflow.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行响应
 */
@Data
public class ExecutionResponse {

    /**
     * 执行 ID
     */
    private Long executionId;

    /**
     * 执行状态: SUCCESS, FAILED
     */
    private String status;

    /**
     * 节点执行结果列表
     */
    private List<NodeResult> nodeResults;

    /**
     * 输出数据
     */
    private Map<String, Object> outputData;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private Integer duration;

    /**
     * 节点执行结果
     */
    @Data
    public static class NodeResult {
        /**
         * 节点 ID
         */
        private String nodeId;

        /**
         * 节点名称
         */
        private String nodeName;

        /**
         * 节点类型
         */
        private String nodeType;

        /**
         * 执行状态: SUCCESS, FAILED, SKIPPED
         */
        private String status;

        /**
         * 输入数据
         */
        private String input;

        /**
         * 输出数据
         */
        private String output;

        /**
         * 错误信息
         */
        private String error;

        /**
         * 执行耗时（毫秒）
         */
        private Integer duration;
    }
}
