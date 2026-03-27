package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.AiAssistant;
import cn.sdh.backend.entity.AssistantRating;
import cn.sdh.backend.service.AiAssistantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService assistantService;

    @GetMapping("/list")
    public Result<IPage<AiAssistant>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return Result.success(assistantService.getPage(page, pageSize, keyword, category));
    }

    @GetMapping("/public")
    public Result<IPage<AiAssistant>> publicList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category) {
        return Result.success(assistantService.getPublicAssistants(page, pageSize, category));
    }

    @GetMapping("/hot")
    public Result<List<AiAssistant>> hotList(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(assistantService.getHotAssistants(limit));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(assistantService.getCategories());
    }

    @GetMapping("/{id}")
    public Result<AiAssistant> getDetail(@PathVariable Long id) {
        AiAssistant assistant = assistantService.getDetail(id);
        if (assistant == null) {
            return Result.notFound("助手不存在");
        }
        return Result.success(assistant);
    }

    @PostMapping
    public Result<AiAssistant> create(@Valid @RequestBody AiAssistant assistant) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        assistant.setCreatorId(userId);
        return Result.success(assistantService.create(assistant));
    }

    @PutMapping("/{id}")
    public Result<AiAssistant> update(@PathVariable Long id, @Valid @RequestBody AiAssistant assistant) {
        Long userId = UserContext.getCurrentUserId();
        AiAssistant existing = assistantService.getDetail(id);
        if (existing == null) {
            return Result.notFound("助手不存在");
        }
        if (!userId.equals(existing.getCreatorId())) {
            return Result.forbidden("无权修改此助手");
        }
        return Result.success(assistantService.update(id, assistant));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        AiAssistant existing = assistantService.getDetail(id);
        if (existing == null) {
            return Result.notFound("助手不存在");
        }
        if (!userId.equals(existing.getCreatorId())) {
            return Result.forbidden("无权删除此助手");
        }
        assistantService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/use")
    public Result<Void> incrementUse(@PathVariable Long id) {
        assistantService.incrementUseCount(id);
        return Result.success();
    }
}