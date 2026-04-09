package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.Tag;
import cn.sdh.backend.service.TagService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/list")
    public Result<IPage<Tag>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return Result.success(tagService.getPage(page, pageSize, keyword, category));
    }

    @GetMapping("/all")
    public Result<List<Tag>> allTags() {
        return Result.success(tagService.getAllTags());
    }

    @GetMapping("/{id}")
    public Result<Tag> getById(@PathVariable Long id) {
        Tag tag = tagService.getById(id);
        if (tag == null) {
            return Result.notFound("标签不存在");
        }
        return Result.success(tag);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody Tag tag) {
        tag.setStatus(1);
        tag.setCreateTime(LocalDateTime.now());
        tagService.save(tag);
        return Result.success();
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Tag tag) {
        Tag existing = tagService.getById(id);
        if (existing == null) {
            return Result.notFound("标签不存在");
        }
        tag.setId(id);
        tagService.updateById(tag);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.success();
    }

    @PostMapping("/document/{documentId}")
    public Result<Void> addDocumentTag(
            @PathVariable Long documentId,
            @RequestParam Long tagId,
            @RequestParam(defaultValue = "manual") String source) {
        tagService.addDocumentTag(documentId, tagId, source, null);
        return Result.success();
    }

    @PostMapping("/document/{documentId}/{tagId}/remove")
    public Result<Void> removeDocumentTag(@PathVariable Long documentId, @PathVariable Long tagId) {
        tagService.removeDocumentTag(documentId, tagId);
        return Result.success();
    }

    @GetMapping("/document/{documentId}")
    public Result<List<Tag>> getDocumentTags(@PathVariable Long documentId) {
        return Result.success(tagService.getDocumentTags(documentId));
    }
}
