package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.AgentTool;
import cn.sdh.backend.service.AgentToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent-tools")
public class AgentToolController {
    private final AgentToolService toolService;

    @GetMapping
    public Result<List<AgentTool>> list() { return Result.success(toolService.list()); }

    @PostMapping
    public Result<AgentTool> create(@RequestBody AgentToolService.ToolCommand command) {
        return Result.success("业务工具已创建", toolService.create(command));
    }

    @PostMapping("/{id}")
    public Result<AgentTool> update(@PathVariable Long id, @RequestBody AgentToolService.ToolCommand command) {
        return Result.success("业务工具已更新", toolService.update(id, command));
    }

    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        toolService.disable(id);
        return Result.success("业务工具已停用，历史审计予以保留", null);
    }
}
