package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.EmbedConfig;
import cn.sdh.backend.service.EmbedConfigService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/embed")
@RequiredArgsConstructor
public class EmbedConfigController {

    private final EmbedConfigService embedConfigService;

    @GetMapping("/list")
    public Result<IPage<EmbedConfig>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        
        if (pageSize > 100) pageSize = 100;
        
        IPage<EmbedConfig> result = embedConfigService.getPage(page, pageSize, keyword);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<EmbedConfig> getById(@PathVariable Long id) {
        EmbedConfig config = embedConfigService.getById(id);
        if (config == null) {
            return Result.notFound("嵌入配置不存在");
        }
        return Result.success(config);
    }

    @GetMapping("/active")
    public Result<EmbedConfig> getActive() {
        EmbedConfig config = embedConfigService.getActive();
        return Result.success(config);
    }

    @PostMapping
    public Result<Void> save(@Valid @RequestBody EmbedConfig config) {
        embedConfigService.save(config);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmbedConfig config) {
        EmbedConfig existing = embedConfigService.getById(id);
        if (existing == null) {
            return Result.notFound("嵌入配置不存在");
        }
        config.setId(id);
        embedConfigService.update(config);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        EmbedConfig existing = embedConfigService.getById(id);
        if (existing == null) {
            return Result.notFound("嵌入配置不存在");
        }
        embedConfigService.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        EmbedConfig existing = embedConfigService.getById(id);
        if (existing == null) {
            return Result.notFound("嵌入配置不存在");
        }
        embedConfigService.toggleStatus(id);
        return Result.success();
    }
}