package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行记录实体
 */
@Data
@TableName("workflow_execution")
public class WorkflowExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工作流ID
     */
    private Long workflowId;

    /**
     * 执行用户ID
     */
    private Long userId;

    /**
     * 执行唯一标识(UUID)
     */
    private String executionId;

    /**
     * 状态: pending, running, success, failed, cancelled
     */
    private String status;

    /**
     * 输入数据JSON
     */
    private String inputData;

    /**
     * 输出数据JSON
     */
    private String outputData;

    /**
     * 节点执行结果JSON
     */
    private String nodeResults;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时(毫秒)
     */
    private Integer duration;

    /**
     * 总Token消耗
     */
    private Integer totalTokens;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    // 状态常量
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";
}
