package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.ChannelConfig;
import cn.sdh.backend.service.ChannelConfigService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelConfigService channelConfigService;

    @GetMapping("/list")
    public Result<IPage<ChannelConfig>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(channelConfigService.getPage(page, pageSize));
    }

    @GetMapping("/active")
    public Result<?> activeList() {
        return Result.success(channelConfigService.getActiveChannels());
    }

    @GetMapping("/{id}")
    public Result<ChannelConfig> getById(@PathVariable Long id) {
        ChannelConfig config = channelConfigService.getById(id);
        if (config == null) {
            return Result.notFound("渠道不存在");
        }
        return Result.success(config);
    }

    @GetMapping("/type/{type}")
    public Result<ChannelConfig> getByType(@PathVariable String type) {
        ChannelConfig config = channelConfigService.getByType(type);
        if (config == null) {
            return Result.notFound("渠道不存在");
        }
        return Result.success(config);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody ChannelConfig config) {
        config.setStatus(1);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        channelConfigService.save(config);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ChannelConfig config) {
        ChannelConfig existing = channelConfigService.getById(id);
        if (existing == null) {
            return Result.notFound("渠道不存在");
        }
        config.setId(id);
        config.setUpdateTime(LocalDateTime.now());
        channelConfigService.updateById(config);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelConfigService.removeById(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        channelConfigService.toggleStatus(id);
        return Result.success();
    }
}
