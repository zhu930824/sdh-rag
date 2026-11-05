package cn.sdh.backend.service;

import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.mapper.ModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/**
 * 模型工厂抽象类
 * 提供模型创建的公共方法和配置
 *
 * @param <M> 模型类型
 */
@Slf4j
public abstract class ModelFactory<M> {

    protected final ModelConfigMapper modelConfigMapper;

    @Value("${spring.ai.dashscope.api-key:}")
    protected String dashscopeApiKey;

    @Value("${spring.ai.openai.api-key:}")
    protected String openaiApiKey;

    // 模型缓存
    protected final Map<String, M> modelCache = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(16,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,M> eldest) { return size()>128; }
    });

    protected ModelFactory(ModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    /**
     * 根据模型名称获取模型
     * @param modelName 模型名称
     * @return 模型实例
     */
    public M getModel(String modelName) {
        if(ModelExecutionScope.active()) return ModelExecutionScope.resolve(this,modelName,false);
        if (modelName == null || modelName.isEmpty()) {
            modelName = getDefaultModelName();
        }

        final String finalModelName = modelName;
        String cacheKey = getCacheKey(modelName);
        ModelConfig found=findModelConfigByName(finalModelName,getModelType());
        ModelConfig config=found==null?null:ModelExecutionScope.copy(found);
        String version=cn.sdh.backend.utils.ContentFingerprint.sha256(com.alibaba.fastjson2.JSON.toJSONString(config==null?new String[]{dashscopeApiKey,openaiApiKey}:config));
        return modelCache.computeIfAbsent(cacheKey+"::"+version,key -> config==null?createDefaultModel(finalModelName):createModel(config));
    }

    /**
     * 根据模型配置ID获取模型
     * @param modelId 模型配置ID
     * @return 模型实例
     */
    public M getModelById(String modelId) {
        if(ModelExecutionScope.active()) return ModelExecutionScope.resolve(this,modelId,true);
        String cacheKey = getCacheKeyById(modelId);
        ModelConfig found=modelConfigMapper.selectOne(new LambdaQueryWrapper<>(ModelConfig.class).eq(ModelConfig::getModelId, modelId).eq(ModelConfig::getModelType,getModelType()).eq(ModelConfig::getStatus,1));
        if(found==null || !getModelType().equals(found.getModelType()) || !Integer.valueOf(1).equals(found.getStatus())) return null;
        ModelConfig config=ModelExecutionScope.copy(found);
        String version=cn.sdh.backend.utils.ContentFingerprint.sha256(com.alibaba.fastjson2.JSON.toJSONString(config));
        return modelCache.computeIfAbsent(cacheKey+"::"+version,key -> createModel(config));
    }
    protected ModelConfig executionConfiguration(ModelConfig config) {
        var provider=cn.sdh.backend.service.factory.ModelProvider.fromString(config.getProvider());
        if(provider==null || config.getModelId()==null || config.getModelId().isBlank()) throw new cn.sdh.backend.common.exception.EvaluationIntegrityException();
        if(config.getBaseUrl()==null || config.getBaseUrl().isBlank()) config.setBaseUrl(provider.getDefaultBaseUrl());
        return config;
    }
    protected M createExecutionModel(ModelConfig config) { return createModel(config); }
    protected java.util.TreeMap<String,Object> executionDescriptor(ModelConfig config,String selector) {
        var values=new java.util.TreeMap<String,Object>();
        values.put("schemaVersion",1); values.put("selector",selector); values.put("configurationId",config.getId());
        values.put("modelType",getModelType()); values.put("provider",config.getProvider()); values.put("modelId",config.getModelId());
        values.put("endpointSha256",cn.sdh.backend.utils.ContentFingerprint.sha256(config.getBaseUrl()));
        values.put("temperatureConfigured",config.getTemperature()); values.put("maxTokensConfigured",config.getMaxTokens());
        values.put("embeddingDimensionConfigured",config.getEmbeddingDimension()); values.put("multimodalConfigured",config.getIsMultiModel());
        values.put("configurationUpdatedAt",config.getUpdateTime()); values.put("factoryClass",getClass().getName());
        values.put("factorySha256",cn.sdh.backend.utils.ContentFingerprint.classSha256(getClass()));
        values.put("credentialsPersisted",false); values.put("sdkDefaults",true);
        values.put("configurationSha256",cn.sdh.backend.utils.ContentFingerprint.sha256(com.alibaba.fastjson2.JSON.toJSONString(values)));
        return values;
    }

    /**
     * 根据配置创建模型
     * @param config 模型配置
     * @return 模型实例
     */
    protected abstract M createModel(ModelConfig config);

    /**
     * 创建默认模型
     * @param modelName 模型名称
     * @return 模型实例
     */
    protected abstract M createDefaultModel(String modelName);

    /**
     * 获取默认模型名称
     * @return 默认模型名称
     */
    protected abstract String getDefaultModelName();

    /**
     * 获取模型类型标识
     * @return 模型类型 (chat/embedding/reranker)
     */
    protected abstract String getModelType();

    /**
     * 获取缓存键
     */
    protected String getCacheKey(String modelName) {
        return getModelType() + ":" + modelName;
    }

    /**
     * 获取ID缓存键
     */
    protected String getCacheKeyById(String modelId) {
        return getModelType() + ":id:" + modelId;
    }

    /**
     * 根据模型名称查找配置
     */
    protected ModelConfig findModelConfigByName(String modelName, String modelType) {
        // 优先匹配 modelId 字段
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getModelId, modelName)
                .eq(ModelConfig::getModelType, modelType)
                .eq(ModelConfig::getStatus, 1)
                .last("LIMIT 1");
        ModelConfig config = modelConfigMapper.selectOne(wrapper);
        if (config != null) {
            return config;
        }

        // 其次匹配 name 字段
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getName, modelName)
                .eq(ModelConfig::getModelType, modelType)
                .eq(ModelConfig::getStatus, 1)
                .last("LIMIT 1");
        return modelConfigMapper.selectOne(wrapper);
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        modelCache.clear();
        log.info("{} 模型缓存已清除", getModelType());
    }

    /**
     * 清除指定模型的缓存
     */
    public void clearCache(String modelName) {
        String cacheKey = getCacheKey(modelName);
        synchronized(modelCache) { modelCache.keySet().removeIf(key -> key.startsWith(cacheKey+"::")); }
        log.info("已清除模型缓存: {}", cacheKey);
    }
}
