package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.SystemSettingsResponse;
import cn.sdh.backend.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService settingsService;

    @GetMapping
    public Result<SystemSettingsResponse> getAll() {
        SystemSettingsResponse settings = settingsService.getAll();
        return Result.success(settings);
    }

    @GetMapping("/{key}")
    public Result<Map<String, Object>> getByKey(@PathVariable String key) {
        Map<String, Object> setting = settingsService.getByKey(key);
        return Result.success(setting);
    }

    @PostMapping("/update")
    public Result<Void> updateAll(@RequestBody Map<String, Map<String, Object>> settings) {
        settingsService.updateAll(settings);
        return Result.success();
    }

    @PostMapping("/update/{key}")
    public Result<Void> updateByKey(@PathVariable String key, @RequestBody Map<String, Object> value) {
        settingsService.updateByKey(key, value);
        return Result.success();
    }
}