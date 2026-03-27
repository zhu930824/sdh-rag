package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String type;

    private String content;

    private String ip;

    private Integer status;

    private String browser;

    private String os;

    private String requestUrl;

    private String requestMethod;

    private String requestParams;

    private String responseData;

    private String errorMsg;

    private Long duration;

    private LocalDateTime createTime;
}