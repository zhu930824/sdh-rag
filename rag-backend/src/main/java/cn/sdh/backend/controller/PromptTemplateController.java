package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.PromptTemplate;
import cn.sdh.backend.service.PromptTemplateService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompt-template")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping("/list")
    public Result<IPage<PromptTemplate>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.success(promptTemplateService.getPage(page, pageSize, category, keyword));
    }

    @GetMapping("/active")
    public Result<List<PromptTemplate>> activeList() {
        return Result.success(promptTemplateService.getActiveTemplates());
    }

    @GetMapping("/category/{category}")
    public Result<List<PromptTemplate>> getByCategory(@PathVariable String category) {
        return Result.success(promptTemplateService.getByCategory(category));
    }

    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        return Result.success(promptTemplateService.getCategories());
    }

    @GetMapping("/{id}")
    public Result<PromptTemplate> getById(@PathVariable Long id) {
        PromptTemplate template = promptTemplateService.getById(id);
        if (template == null) {
            return Result.notFound("模板不存在");
        }
        return Result.success(template);
    }

    @GetMapping("/code/{code}")
    public Result<PromptTemplate> getByCode(@PathVariable String code) {
        PromptTemplate template = promptTemplateService.getByCode(code);
        if (template == null) {
            return Result.notFound("模板不存在");
        }
        return Result.success(template);
    }

    @PostMapping
    public Result<PromptTemplate> create(@Valid @RequestBody PromptTemplate template) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        try {
            PromptTemplate created = promptTemplateService.createTemplate(template, userId);
            return Result.success(created);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PromptTemplate template) {
        template.setId(id);
        try {
            promptTemplateService.updateTemplate(template);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            promptTemplateService.deleteTemplate(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/render")
    public Result<Map<String, String>> renderTemplate(@RequestBody Map<String, Object> request) {
        String code = (String) request.get("code");
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.get("variables");

        try {
            String rendered = promptTemplateService.renderTemplate(code, variables);
            Map<String, String> result = new HashMap<>();
            result.put("content", rendered);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/validate")
    public Result<Void> validateTemplate(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        try {
            promptTemplateService.validateTemplate(content);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
