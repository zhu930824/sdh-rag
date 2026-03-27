package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.DocumentVersion;
import cn.sdh.backend.service.DocumentVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-version")
@RequiredArgsConstructor
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;

    @GetMapping("/list/{documentId}")
    public Result<List<DocumentVersion>> listByDocument(@PathVariable Long documentId) {
        return Result.success(documentVersionService.getVersionsByDocumentId(documentId));
    }

    @GetMapping("/{id}")
    public Result<DocumentVersion> getById(@PathVariable Long id) {
        DocumentVersion version = documentVersionService.getById(id);
        if (version == null) {
            return Result.notFound("版本不存在");
        }
        return Result.success(version);
    }

    @GetMapping("/latest/{documentId}")
    public Result<DocumentVersion> getLatest(@PathVariable Long documentId) {
        DocumentVersion version = documentVersionService.getLatestVersion(documentId);
        if (version == null) {
            return Result.notFound("暂无版本记录");
        }
        return Result.success(version);
    }

    @PostMapping("/{documentId}")
    public Result<DocumentVersion> createVersion(@PathVariable Long documentId, @RequestParam(required = false) String changeSummary) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        try {
            DocumentVersion version = documentVersionService.createVersion(documentId, changeSummary, userId);
            return Result.success(version);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/rollback/{versionId}")
    public Result<Void> rollback(@PathVariable Long versionId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        try {
            documentVersionService.rollbackToVersion(versionId, userId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
