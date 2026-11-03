package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.service.PlatformAdminService;
import cn.sdh.backend.service.ReliableExecutionMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reliable-execution")
@RequiredArgsConstructor
public class ReliableExecutionController {
    private final PlatformAdminService adminService;
    private final ReliableExecutionMetricsService metricsService;

    @GetMapping("/metrics")
    public Result<ReliableExecutionMetricsService.Snapshot> metrics() {
        adminService.requireAdmin();
        return Result.success(metricsService.current());
    }
}
