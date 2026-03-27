package cn.sdh.backend.dto;

import cn.sdh.backend.entity.ModelConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelConfigResponse {
    private Long id;
    private String name;
    private String provider;
    private String modelType;
    private String modelId;
    private Boolean hasApiKey;
    private String apiKeyMasked;
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
    
    public static ModelConfigResponse fromEntity(ModelConfig entity) {
        if (entity == null) return null;
        
        ModelConfigResponse response = new ModelConfigResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setProvider(entity.getProvider());
        response.setModelType(entity.getModelType());
        response.setModelId(entity.getModelId());
        response.setHasApiKey(entity.getApiKey() != null && !entity.getApiKey().isEmpty());
        response.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        response.setBaseUrl(entity.getBaseUrl());
        response.setTemperature(entity.getTemperature());
        response.setMaxTokens(entity.getMaxTokens());
        response.setStatus(entity.getStatus());
        response.setIsDefault(entity.getIsDefault());
        response.setSort(entity.getSort());
        response.setIsLocal(entity.getIsLocal());
        response.setIsBuiltIn(entity.getIsBuiltIn());
        response.setIcon(entity.getIcon());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }
    
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return null;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}