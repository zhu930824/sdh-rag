package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.SensitiveWordRequest;
import cn.sdh.backend.entity.SensitiveWord;
import cn.sdh.backend.service.SensitiveWordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensitive")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @GetMapping("/list")
    public Result<IPage<SensitiveWord>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        
        if (pageSize > 100) pageSize = 100;
        
        IPage<SensitiveWord> result = sensitiveWordService.getPage(page, pageSize, keyword, category);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<SensitiveWord> getById(@PathVariable Long id) {
        SensitiveWord word = sensitiveWordService.getById(id);
        if (word == null) {
            return Result.notFound("敏感词不存在");
        }
        return Result.success(word);
    }

    @PostMapping
    public Result<Void> save(@Valid @RequestBody SensitiveWordRequest request) {
        SensitiveWord word = new SensitiveWord();
        word.setWord(request.getWord());
        word.setCategory(request.getCategory());
        word.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        sensitiveWordService.save(word);
        return Result.success();
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SensitiveWordRequest request) {
        SensitiveWord existing = sensitiveWordService.getById(id);
        if (existing == null) {
            return Result.notFound("敏感词不存在");
        }

        existing.setWord(request.getWord());
        existing.setCategory(request.getCategory());
        existing.setStatus(request.getStatus());
        sensitiveWordService.save(existing);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SensitiveWord word = sensitiveWordService.getById(id);
        if (word == null) {
            return Result.notFound("敏感词不存在");
        }
        sensitiveWordService.deleteById(id);
        return Result.success();
    }

    @PostMapping("/batch-delete")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的敏感词");
        }
        sensitiveWordService.deleteBatch(ids);
        return Result.success();
    }

    @PostMapping("/status/{id}")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        SensitiveWord word = sensitiveWordService.getById(id);
        if (word == null) {
            return Result.notFound("敏感词不存在");
        }
        sensitiveWordService.toggleStatus(id);
        return Result.success();
    }

    @PostMapping("/check")
    public Result<List<String>> check(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isEmpty()) {
            return Result.success(List.of());
        }
        List<String> words = sensitiveWordService.findSensitiveWords(text);
        return Result.success(words);
    }
}