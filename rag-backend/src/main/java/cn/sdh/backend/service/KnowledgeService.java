package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentCategory;
import cn.sdh.backend.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeService {

    /**
     * 上传文档
     * @param document 文档实体
     * @return 是否成功
     */
    boolean uploadDocument(KnowledgeDocument document);

    /**
     * 分页获取文档列表
     * @param page 页码
     * @param size 每页大小
     * @param categoryId 分类ID（可选）
     * @param userId 用户ID
     * @return 分页结果
     */
    Page<KnowledgeDocument> getDocumentList(int page, int size, Long categoryId, Long userId);

    /**
     * 搜索文档
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @param userId 用户ID
     * @return 分页结果
     */
    Page<KnowledgeDocument> searchDocuments(String keyword, int page, int size, Long userId);

    /**
     * 删除文档
     * @param id 文档ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteDocument(Long id, Long userId);

    /**
     * 获取文档详情
     * @param id 文档ID
     * @return 文档实体
     */
    KnowledgeDocument getDocumentById(Long id);

    /**
     * 获取所有分类
     * @return 分类列表
     */
    List<DocumentCategory> getAllCategories();

    /**
     * 创建分类
     * @param category 分类实体
     * @return 是否成功
     */
    boolean createCategory(DocumentCategory category);

    /**
     * 更新文档状态
     * @param id 文档ID
     * @param status 状态
     * @return 是否成功
     */
    boolean updateDocumentStatus(Long id, Integer status);

    /**
     * 重建所有索引
     */
    void rebuildAllIndexes();
}
