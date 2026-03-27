package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long flowId;

    private String businessType;

    private Long businessId;

    private Long applicantId;

    private Integer currentNode;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}