package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("channel_config")
public class ChannelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String channelType;

    private String config;

    private Long assistantId;

    private String callbackUrl;

    private String secretKey;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}