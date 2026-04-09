package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.ModelConfigRequest;
import cn.sdh.backend.dto.ModelConfigResponse;
import cn.sdh.backend.entity.ModelConfig;
import cn.sdh.backend.service.ModelConfigService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/list")
    public Result<IPage<ModelConfigResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        
        if (pageSize > 100) pageSize = 100;
        
        IPage<ModelConfig> result = modelConfigService.getPage(page, pageSize, keyword);
        IPage<ModelConfigResponse> responsePage = result.convert(ModelConfigResponse::fromEntity);
        return Result.success(responsePage);
    }

    @GetMapping("/{id}")
    public Result<ModelConfigResponse> getById(@PathVariable Long id) {
        ModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            return Result.notFound("模型配置不存在");
        }
        return Result.success(ModelConfigResponse.fromEntity(config));
    }

    @GetMapping("/default")
    public Result<ModelConfigResponse> getDefault() {
        ModelConfig config = modelConfigService.getDefault();
        return Result.success(ModelConfigResponse.fromEntity(config));
    }

    @PostMapping("/add")
    public Result<Void> save(@Valid @RequestBody ModelConfigRequest request) {
        ModelConfig config = new ModelConfig();
        config.setName(request.getName());
        config.setProvider(request.getProvider());
        config.setModelType(request.getModelType());
        config.setModelId(request.getModelId());
        config.setApiKey(request.getApiKey());
        config.setBaseUrl(request.getBaseUrl());
        config.setTemperature(request.getTemperature());
        config.setMaxTokens(request.getMaxTokens());
        config.setStatus(request.getStatus());
        config.setIsDefault(request.getIsDefault());
        config.setSort(request.getSort());
        config.setIsLocal(request.getIsLocal());
        config.setIsBuiltIn(request.getIsBuiltIn());
        config.setIcon(request.getIcon());
        modelConfigService.save(config);
        return Result.success();
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModelConfigRequest request) {
        ModelConfig existing = modelConfigService.getById(id);
        if (existing == null) {
            return Result.notFound("模型配置不存在");
        }

        ModelConfig config = new ModelConfig();
        config.setId(id);
        config.setName(request.getName());
        config.setProvider(request.getProvider());
        config.setModelType(request.getModelType());
        config.setModelId(request.getModelId());
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            config.setApiKey(request.getApiKey());
        } else {
            config.setApiKey(existing.getApiKey());
        }
        config.setBaseUrl(request.getBaseUrl());
        config.setTemperature(request.getTemperature());
        config.setMaxTokens(request.getMaxTokens());
        config.setStatus(request.getStatus());
        config.setIsDefault(existing.getIsDefault());
        config.setSort(request.getSort());
        config.setIsLocal(request.getIsLocal());
        config.setIsBuiltIn(request.getIsBuiltIn());
        config.setIcon(request.getIcon());
        modelConfigService.update(config);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            return Result.notFound("模型配置不存在");
        }
        modelConfigService.deleteById(id);
        return Result.success();
    }

    @PostMapping("/default/{id}")
    public Result<Void> setDefault(@PathVariable Long id) {
        ModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            return Result.notFound("模型配置不存在");
        }
        modelConfigService.setDefault(id);
        return Result.success();
    }

    @GetMapping("/active")
    public Result<List<ModelConfigResponse>> getActiveList() {
        List<ModelConfig> configs = modelConfigService.getActiveList();
        List<ModelConfigResponse> responses = configs.stream()
                .map(ModelConfigResponse::fromEntity)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    @GetMapping("/active/chat")
    public Result<List<ModelConfigResponse>> getActiveChatModels() {
        List<ModelConfig> configs = modelConfigService.getActiveChatModels();
        List<ModelConfigResponse> responses = configs.stream()
                .map(ModelConfigResponse::fromEntity)
                .collect(Collectors.toList());
        return Result.success(responses);
    }


}