package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("api_call_log")
public class ApiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long modelId;

    private String apiType;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal cost;

    private Long duration;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;
}