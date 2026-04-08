package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.CategoryRequest;
import cn.sdh.backend.dto.DocumentWithTagsResponse;
import cn.sdh.backend.entity.DocumentCategory;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.Tag;
import cn.sdh.backend.service.KnowledgeService;
import cn.sdh.backend.service.MinioService;
import cn.sdh.backend.service.TagService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final KnowledgeService knowledgeService;
    private final MinioService minioService;
    private final TagService tagService;

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "title", required = false) String title) {

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

        // 上传到 MinIO
        String directory = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filePath = minioService.uploadFile(file, directory);

        // 创建文档记录
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title != null && !title.isEmpty() ? title : originalFilename);
        document.setFileType(fileType);
        document.setFilePath(filePath);
        document.setFileSize(fileSize);
        document.setCategoryId(categoryId);
        document.setUserId(userId);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());

        knowledgeService.uploadDocument(document);

        return Result.success("上传成功", document);
    }

    /**
     * 获取文档预览URL
     */
    @GetMapping("/{id}/preview")
    public Result<String> getDocumentPreviewUrl(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        KnowledgeDocument document = knowledgeService.getDocumentById(id);
        if (document == null) {
            return Result.notFound("文档不存在");
        }

        String previewUrl = minioService.getPreviewUrl(document.getFilePath());
        return Result.success(previewUrl);
    }

    /**
     * 获取文档列表（分页，包含标签）
     */
    @GetMapping("/list")
    public Result<Page<DocumentWithTagsResponse>> getDocumentList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeDocument> documentPage = knowledgeService.getDocumentList(page, size, categoryId, userId);

        // 转换为包含标签的响应
        Page<DocumentWithTagsResponse> responsePage = new Page<>(documentPage.getCurrent(), documentPage.getSize(), documentPage.getTotal());

        List<DocumentWithTagsResponse> responseList = new ArrayList<>();
        for (KnowledgeDocument doc : documentPage.getRecords()) {
            DocumentWithTagsResponse response = new DocumentWithTagsResponse();
            BeanUtils.copyProperties(doc, response);

            // 获取文档标签
            List<Tag> tags = tagService.getDocumentTags(doc.getId());
            List<DocumentWithTagsResponse.TagInfo> tagInfos = tags.stream().map(tag -> {
                DocumentWithTagsResponse.TagInfo tagInfo = new DocumentWithTagsResponse.TagInfo();
                tagInfo.setId(tag.getId());
                tagInfo.setName(tag.getName());
                tagInfo.setColor(tag.getColor());
                return tagInfo;
            }).collect(Collectors.toList());
            response.setTags(tagInfos);

            responseList.add(response);
        }
        responsePage.setRecords(responseList);

        return Result.success(responsePage);
    }

    /**
     * 搜索文档
     */
    @GetMapping("/search")
    public Result<Page<DocumentWithTagsResponse>> searchDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeDocument> documentPage = knowledgeService.searchDocuments(keyword, page, size, userId);

        // 转换为包含标签的响应
        Page<DocumentWithTagsResponse> responsePage = new Page<>(documentPage.getCurrent(), documentPage.getSize(), documentPage.getTotal());

        List<DocumentWithTagsResponse> responseList = new ArrayList<>();
        for (KnowledgeDocument doc : documentPage.getRecords()) {
            DocumentWithTagsResponse response = new DocumentWithTagsResponse();
            BeanUtils.copyProperties(doc, response);

            // 获取文档标签
            List<Tag> tags = tagService.getDocumentTags(doc.getId());
            List<DocumentWithTagsResponse.TagInfo> tagInfos = tags.stream().map(tag -> {
                DocumentWithTagsResponse.TagInfo tagInfo = new DocumentWithTagsResponse.TagInfo();
                tagInfo.setId(tag.getId());
                tagInfo.setName(tag.getName());
                tagInfo.setColor(tag.getColor());
                return tagInfo;
            }).collect(Collectors.toList());
            response.setTags(tagInfos);

            responseList.add(response);
        }
        responsePage.setRecords(responseList);

        return Result.success(responsePage);
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

        // 获取文档信息以删除 MinIO 中的文件
        KnowledgeDocument document = knowledgeService.getDocumentById(id);
        if (document != null && document.getFilePath() != null) {
            try {
                minioService.deleteFile(document.getFilePath());
            } catch (Exception e) {
                log.warn("删除MinIO文件失败: {}", document.getFilePath(), e);
            }
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
     * 更新分类
     */
    @PutMapping("/category/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        DocumentCategory category = new DocumentCategory();
        category.setId(id);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId());
        category.setSort(request.getSort());

        boolean success = knowledgeService.updateCategory(category);
        if (!success) {
            return Result.error("更新分类失败");
        }
        return Result.success("更新成功", null);
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        boolean success = knowledgeService.deleteCategory(id);
        if (!success) {
            return Result.error("删除分类失败，请检查是否存在子分类或关联文档");
        }
        return Result.success("删除成功", null);
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
}
