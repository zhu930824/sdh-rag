package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.KnowledgeBaseRequest;
import cn.sdh.backend.dto.LinkDocumentRequest;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识库控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Value("${upload.path:upload}")
    private String uploadPath;

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<Void> createKnowledgeBase(@RequestBody KnowledgeBaseRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setUserId(userId);

        knowledgeBaseService.createKnowledgeBase(knowledgeBase);
        return Result.success("创建成功", null);
    }

    /**
     * 获取知识库列表
     */
    @GetMapping("/list")
    public Result<List<KnowledgeBase>> getKnowledgeBaseList() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        List<KnowledgeBase> list = knowledgeBaseService.getKnowledgeBaseList(userId);
        return Result.success(list);
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getKnowledgeBase(@PathVariable Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(id);
        return Result.success(knowledgeBase);
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public Result<Void> updateKnowledgeBase(@PathVariable Long id, @RequestBody KnowledgeBaseRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBaseById(id);
        if (knowledgeBase == null) {
            return Result.paramError("知识库不存在");
        }

        if (!knowledgeBase.getUserId().equals(userId)) {
            return Result.error("无权修改该知识库");
        }

        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBaseService.updateKnowledgeBase(knowledgeBase);

        return Result.success("更新成功", null);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeBaseService.deleteKnowledgeBase(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 获取知识库下的文档列表
     */
    @GetMapping("/{id}/documents")
    public Result<Page<KnowledgeDocument>> getDocuments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<KnowledgeDocument> result = knowledgeBaseService.getDocumentsByKnowledgeId(id, page, size);
        return Result.success(result);
    }

    /**
     * 上传文档到知识库
     */
    @PostMapping("/{id}/documents")
    public Result<Void> uploadDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "title", required = false) String title) throws IOException {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        // 验证文件
        if (file.isEmpty()) {
            return Result.paramError("文件不能为空");
        }

        // 获取文件信息
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        long fileSize = file.getSize();

        // 保存文件
        String filePath = saveFile(file);

        // 创建文档记录
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title != null ? title : originalFilename);
        document.setFileType(fileType);
        document.setFilePath(filePath);
        document.setFileSize(fileSize);
        document.setCategoryId(categoryId);
        document.setUserId(userId);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());

        knowledgeBaseService.uploadDocumentToKnowledgeBase(id, document);

        return Result.success("上传成功", null);
    }

    /**
     * 关联已有文档到知识库
     */
    @PostMapping("/{id}/documents/link")
    public Result<Void> linkDocuments(@PathVariable Long id, @RequestBody LinkDocumentRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeBaseService.linkDocumentsToKnowledgeBase(id, request.getDocumentIds());
        return Result.success("关联成功", null);
    }

    /**
     * 移除文档与知识库的关联
     */
    @DeleteMapping("/{id}/documents/{documentId}")
    public Result<Void> unlinkDocument(@PathVariable Long id, @PathVariable Long documentId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeBaseService.unlinkDocumentFromKnowledgeBase(id, documentId);
        return Result.success("移除成功", null);
    }

    /**
     * 获取可关联的文档列表
     */
    @GetMapping("/documents/available")
    public Result<Page<KnowledgeDocument>> getAvailableDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long excludeKnowledgeId) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeDocument> result = knowledgeBaseService.getAllDocumentsForLinking(
                userId, page, size, excludeKnowledgeId);
        return Result.success(result);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 保存文件到本地
     */
    private String saveFile(MultipartFile file) throws IOException {
        // 创建上传目录
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + "." + extension;

        // 保存文件
        File destFile = new File(uploadDir, newFilename);
        file.transferTo(destFile);

        return newFilename;
    }
}
