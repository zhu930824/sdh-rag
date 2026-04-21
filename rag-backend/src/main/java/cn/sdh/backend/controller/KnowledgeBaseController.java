package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.*;
import cn.sdh.backend.dto.KnowledgeBaseListVO;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.entity.Tag;
import cn.sdh.backend.service.KnowledgeBaseService;
import cn.sdh.backend.service.KnowledgeBaseTagService;
import cn.sdh.backend.service.TagService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseTagService knowledgeBaseTagService;
    private final TagService tagService;

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
        knowledgeBase.setIcon(request.getIcon());
        knowledgeBase.setColor(request.getColor());
        knowledgeBase.setIsPublic(request.getIsPublic());

        knowledgeBaseService.createKnowledgeBase(knowledgeBase);
        return Result.success("创建成功", null);
    }

    /**
     * 获取知识库列表（分页）
     */
    @GetMapping("/list")
    public Result<Page<KnowledgeBaseListVO>> getKnowledgeBaseList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeBaseListVO> result = knowledgeBaseService.getKnowledgeBasePageWithStats(userId, page, pageSize, keyword, status);
        return Result.success(result);
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
    @PostMapping("/update/{id}")
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
    @PostMapping("/delete/{id}")
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
     * 获取知识库下的文档详情列表（包含处理状态和配置信息）
     */
    @GetMapping("/{id}/documents/detail")
    public Result<Page<KnowledgeDocumentVO>> getDocumentDetails(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<KnowledgeDocumentVO> result = knowledgeBaseService.getDocumentDetailsByKnowledgeId(id, page, size);
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

//        knowledgeBaseService.uploadDocumentToKnowledgeBase(id, document);

        return Result.success("上传成功", null);
    }

    /**
     * 关联已有文档到知识库
     */
    @PostMapping("/{id}/documents/link")
    public Result<Void> linkDocuments(@PathVariable Long id, @RequestBody LinkDocumentsRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        // 检查是否有文档级别配置
        if (request.getConfigs() != null && !request.getConfigs().isEmpty()) {
            knowledgeBaseService.linkDocumentsWithConfig(id, request.getConfigs());
        } else {
            knowledgeBaseService.linkDocumentsToKnowledgeBase(id, request.getDocumentIds());
        }
        return Result.success("关联成功", null);
    }

    /**
     * 更新文档关联配置
     */
    @PostMapping("/{id}/documents/{documentId}/config")
    public Result<Void> updateDocumentConfig(
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestBody DocumentLinkConfig config) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeBaseService.updateDocumentLinkConfig(id, documentId, config);
        return Result.success("更新成功", null);
    }

    /**
     * 获取文档关联详情（包含切分配置）
     */
    @GetMapping("/{id}/documents/{documentId}/relation")
    public Result<KnowledgeDocumentRelation> getDocumentRelation(
            @PathVariable Long id,
            @PathVariable Long documentId) {
        KnowledgeDocumentRelation relation = knowledgeBaseService.getDocumentProcessStatus(id, documentId);
        return Result.success(relation);
    }

    /**
     * 移除文档与知识库的关联
     */
    @PostMapping("/{id}/documents/{documentId}/unlink")
    public Result<Void> unlinkDocument(@PathVariable Long id, @PathVariable Long documentId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeBaseService.unlinkDocumentFromKnowledgeBase(id, documentId);
        return Result.success("移除成功", null);
    }

    /**
     * 重新处理文档
     */
    @PostMapping("/{id}/documents/{documentId}/reprocess")
    public Result<Void> reprocessDocument(@PathVariable Long id, @PathVariable Long documentId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        knowledgeBaseService.reprocessDocument(id, documentId);
        return Result.success("已提交重新处理", null);
    }

    /**
     * 获取可关联的文档列表
     */
    @GetMapping("/documents/available")
    public Result<Page<KnowledgeDocument>> getAvailableDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long excludeKnowledgeId,
            @RequestParam(required = false) String keyword) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        Page<KnowledgeDocument> result = knowledgeBaseService.getAllDocumentsForLinking(
                userId, page, size, excludeKnowledgeId, keyword);
        return Result.success(result);
    }

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/stats")
    public Result<KnowledgeBaseService.KnowledgeBaseStats> getStats() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        KnowledgeBaseService.KnowledgeBaseStats stats = knowledgeBaseService.getKnowledgeBaseStats(userId);
        return Result.success(stats);
    }

    /**
     * 获取知识库详情（包含统计信息和标签）
     */
    @GetMapping("/{id}/detail")
    public Result<KnowledgeBaseDetailDTO> getKnowledgeBaseDetail(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(id);
        if (kb == null) {
            return Result.notFound("知识库不存在");
        }

        KnowledgeBaseDetailDTO dto = new KnowledgeBaseDetailDTO();
        dto.setKnowledgeBase(kb);

        // 获取标签
        List<Long> tagIds = knowledgeBaseTagService.getTagIdsByKnowledgeBaseId(id);
        if (!tagIds.isEmpty()) {
            dto.setTags(tagService.listByIds(tagIds));
        }

        // 统计信息
        dto.setDocumentCount((int) knowledgeBaseService.getDocumentsByKnowledgeId(id, 1, 1).getTotal());
        dto.setChunkCount((int) knowledgeBaseService.getChunksByKnowledgeId(id, 1, 1).getTotal());

        return Result.success(dto);
    }

    /**
     * 更新知识库配置
     */
    @PostMapping("/{id}/config")
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody KnowledgeBaseConfigRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }

        KnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(id);
        if (kb == null) {
            return Result.notFound("知识库不存在");
        }

        if (!kb.getUserId().equals(userId)) {
            return Result.error("无权修改该知识库");
        }

        // 更新配置（使用完整配置更新方法）
        KnowledgeBase config = new KnowledgeBase();
        config.setChunkSize(request.getChunkSize());
        config.setChunkOverlap(request.getChunkOverlap());
        config.setEmbeddingModel(request.getEmbeddingModel());
        config.setRankModel(request.getRankModel());
        config.setEnableRewrite(request.getEnableRewrite());
        config.setSimilarityThreshold(request.getSimilarityThreshold());
        config.setKeywordTopK(request.getKeywordTopK());
        config.setVectorTopK(request.getVectorTopK());
        config.setKeywordWeight(request.getKeywordWeight());
        config.setVectorWeight(request.getVectorWeight());
        config.setEnableQueryExpansion(request.getEnableQueryExpansion());
        config.setQueryExpansionCount(request.getQueryExpansionCount());
        config.setEnableHyde(request.getEnableHyde());
        config.setHydeModel(request.getHydeModel());


        knowledgeBaseService.updateKnowledgeBaseFullConfig(id, config);

        // 更新标签
        if (request.getTagIds() != null) {
            knowledgeBaseTagService.setKnowledgeBaseTags(id, request.getTagIds());
        }

        return Result.success("更新成功", null);
    }

    /**
     * 获取知识库分块列表
     */
    @GetMapping("/{id}/chunks")
    public Result<Page<KnowledgeChunkVO>> getChunks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<KnowledgeChunkVO> result = knowledgeBaseService.getChunksByKnowledgeId(id, page, size);
        return Result.success(result);
    }

    /**
     * 添加知识库标签
     */
    @PostMapping("/{id}/tags")
    public Result<Void> addTag(@PathVariable Long id, @RequestParam Long tagId) {
        knowledgeBaseTagService.addTagToKnowledgeBase(id, tagId);
        return Result.success("添加成功", null);
    }

    /**
     * 移除知识库标签
     */
    @PostMapping("/{id}/tags/{tagId}/remove")
    public Result<Void> removeTag(@PathVariable Long id, @PathVariable Long tagId) {
        knowledgeBaseTagService.removeTagFromKnowledgeBase(id, tagId);
        return Result.success("移除成功", null);
    }

    /**
     * 获取知识库标签列表
     */
    @GetMapping("/{id}/tags")
    public Result<List<Tag>> getTags(@PathVariable Long id) {
        List<Long> tagIds = knowledgeBaseTagService.getTagIdsByKnowledgeBaseId(id);
        if (tagIds.isEmpty()) {
            return Result.success(List.of());
        }
        return Result.success(tagService.listByIds(tagIds));
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
