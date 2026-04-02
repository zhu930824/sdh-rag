package cn.sdh.backend.workflow.dto;

import lombok.Data;
import java.util.Map;

/**
 * 执行事件（用于 SSE 推送）
 */
@Data
public class ExecutionEvent {

    /**
     * 事件类型: WORKFLOW_START, NODE_START, NODE_SUCCESS, NODE_ERROR, NODE_PROGRESS, WORKFLOW_COMPLETE
     */
    private String eventType;

    /**
     * 节点 ID
     */
    private String nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 状态
     */
    private String status;

    /**
     * 消息
     */
    private String message;

    /**
     * 附加数据
     */
    private Map<String, Object> data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 耗时（毫秒）
     */
    private Integer duration;

    public static ExecutionEvent workflowStart(String executionId) {
        ExecutionEvent event = new ExecutionEvent();
        event.setEventType("WORKFLOW_START");
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static ExecutionEvent nodeStart(String nodeId, String nodeName) {
        ExecutionEvent event = new ExecutionEvent();
        event.setEventType("NODE_START");
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setStatus("running");
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static ExecutionEvent nodeSuccess(String nodeId, String nodeName, Map<String, Object> data, int duration) {
        ExecutionEvent event = new ExecutionEvent();
        event.setEventType("NODE_SUCCESS");
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setStatus("success");
        event.setData(data);
        event.setDuration(duration);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static ExecutionEvent nodeProgress(String nodeId, String nodeName, String message, Map<String, Object> data) {
        ExecutionEvent event = new ExecutionEvent();
        event.setEventType("NODE_PROGRESS");
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setStatus("running");
        event.setMessage(message);
        event.setData(data);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static ExecutionEvent nodeError(String nodeId, String nodeName, String errorMessage) {
        ExecutionEvent event = new ExecutionEvent();
        event.setEventType("NODE_ERROR");
        event.setNodeId(nodeId);
        event.setNodeName(nodeName);
        event.setStatus("failed");
        event.setMessage(errorMessage);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static ExecutionEvent workflowComplete(String status, Map<String, Object> output, int duration) {
        ExecutionEvent event = new ExecutionEvent();
        event.setEventType("WORKFLOW_COMPLETE");
        event.setStatus(status);
        event.setData(output);
        event.setDuration(duration);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}
