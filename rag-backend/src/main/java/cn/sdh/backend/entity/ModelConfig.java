package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_config")
public class ModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String provider;

    private String modelType;

    private String modelId;

    private String apiKey;

    private String baseUrl;

    private Double temperature;

    private Integer maxTokens;

    private Integer status;

    private Integer isDefault;

    private Integer sort;

    private Integer isLocal;

    private Integer isBuiltIn;

    private String icon;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}