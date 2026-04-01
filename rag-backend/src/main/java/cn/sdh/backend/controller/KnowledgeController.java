package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.CategoryRequest;
import cn.sdh.backend.entity.DocumentCategory;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.service.KnowledgeService;
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
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Value("${upload.path:upload}")
    private String uploadPath;

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public Result<Void> uploadDocument(
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

        knowledgeService.uploadDocument(document);

        return Result.success("上传成功", null);
    }

    /**
     * 获取文档列表（分页）
     */
    @GetMapping("/list")
    public Result<Page<KnowledgeDocument>> getDocumentList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId) {
        
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeDocument> result = knowledgeService.getDocumentList(page, size, categoryId, userId);
        return Result.success(result);
    }

    /**
     * 搜索文档
     */
    @GetMapping("/search")
    public Result<Page<KnowledgeDocument>> searchDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeDocument> result = knowledgeService.searchDocuments(keyword, page, size, userId);
        return Result.success(result);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeService.deleteDocument(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 获取分类列表
     */
    @GetMapping("/category/list")
    public Result<List<DocumentCategory>> getCategories() {
        List<DocumentCategory> categories = knowledgeService.getAllCategories();
        return Result.success(categories);
    }

    /**
     * 创建分类
     */
    @PostMapping("/category")
    public Result<Void> createCategory(@RequestBody CategoryRequest request) {
        DocumentCategory category = new DocumentCategory();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId());
        category.setSort(request.getSort() != null ? request.getSort() : 0);

        knowledgeService.createCategory(category);
        return Result.success("创建成功", null);
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
