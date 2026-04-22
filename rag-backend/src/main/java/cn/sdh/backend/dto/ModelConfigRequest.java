package cn.sdh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelConfigRequest {
    private Long id;
    
    @NotBlank(message = "模型名称不能为空")
    private String name;
    
    @NotBlank(message = "提供商不能为空")
    private String provider;
    
    @NotBlank(message = "模型类型不能为空")
    private String modelType;
    
    @NotBlank(message = "模型ID不能为空")
    private String modelId;
    
    private String apiKey;
    private String baseUrl;
    private Double temperature = 0.7;
    private Integer maxTokens = 2000;
    private Integer status = 1;
    private Integer isDefault = 0;
    private Integer sort = 0;
    private Integer isLocal = 0;
    private Integer isBuiltIn = 0;
    private Integer isMultiModel = 0;
    private String icon;

    private Integer embeddingDimension;
}