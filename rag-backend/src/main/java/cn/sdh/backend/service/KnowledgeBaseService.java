package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.entity.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库
     * @param knowledgeBase 知识库实体
     * @return 是否成功
     */
    boolean createKnowledgeBase(KnowledgeBase knowledgeBase);

    /**
     * 获取用户的知识库列表
     * @param userId 用户ID
     * @return 知识库列表
     */
    List<KnowledgeBase> getKnowledgeBaseList(Long userId);

    /**
     * 分页获取知识库列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @param keyword 关键词
     * @param status 状态
     * @return 分页结果
     */
    Page<KnowledgeBase> getKnowledgeBasePage(Long userId, int page, int size, String keyword, Integer status);

    /**
     * 获取知识库详情
     * @param id 知识库ID
     * @return 知识库实体
     */
    KnowledgeBase getKnowledgeBaseById(Long id);

    /**
     * 更新知识库
     * @param knowledgeBase 知识库实体
     * @return 是否成功
     */
    boolean updateKnowledgeBase(KnowledgeBase knowledgeBase);

    /**
     * 更新知识库配置
     * @param id 知识库ID
     * @param chunkSize 分块大小
     * @param chunkOverlap 分块重叠
     * @param embeddingModel 嵌入模型
     * @return 是否成功
     */
    boolean updateKnowledgeBaseConfig(Long id, Integer chunkSize, Integer chunkOverlap, String embeddingModel);

    /**
     * 删除知识库
     * @param id 知识库ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteKnowledgeBase(Long id, Long userId);

    /**
     * 获取知识库下的文档列表
     * @param knowledgeId 知识库ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    Page<KnowledgeDocument> getDocumentsByKnowledgeId(Long knowledgeId, int page, int size);

    /**
     * 关联已有文档到知识库
     * @param knowledgeId 知识库ID
     * @param documentIds 文档ID列表
     * @return 是否成功
     */
    boolean linkDocumentsToKnowledgeBase(Long knowledgeId, List<Long> documentIds);

    /**
     * 移除文档与知识库的关联
     * @param knowledgeId 知识库ID
     * @param documentId 文档ID
     * @return 是否成功
     */
    boolean unlinkDocumentFromKnowledgeBase(Long knowledgeId, Long documentId);

    /**
     * 获取所有文档（用于关联选择）
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @param excludeKnowledgeId 排除已关联到该知识库的文档
     * @return 分页结果
     */
    Page<KnowledgeDocument> getAllDocumentsForLinking(Long userId, int page, int size, Long excludeKnowledgeId);

    /**
     * 获取文档处理状态
     * @param knowledgeId 知识库ID
     * @param documentId 文档ID
     * @return 关联记录
     */
    KnowledgeDocumentRelation getDocumentProcessStatus(Long knowledgeId, Long documentId);

    /**
     * 获取知识库统计信息
     * @param userId 用户ID
     * @return 统计信息
     */
    KnowledgeBaseStats getKnowledgeBaseStats(Long userId);

    /**
     * 获取知识库的分块列表
     * @param knowledgeId 知识库ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    Page<KnowledgeChunk> getChunksByKnowledgeId(Long knowledgeId, int page, int size);

    /**
     * 添加知识库标签
     * @param knowledgeId 知识库ID
     * @param tagId 标签ID
     * @return 是否成功
     */
    boolean addTagToKnowledgeBase(Long knowledgeId, Long tagId);

    /**
     * 移除知识库标签
     * @param knowledgeId 知识库ID
     * @param tagId 标签ID
     * @return 是否成功
     */
    boolean removeTagFromKnowledgeBase(Long knowledgeId, Long tagId);

    /**
     * 获取知识库的标签列表
     * @param knowledgeId 知识库ID
     * @return 标签列表
     */
    List<Tag> getTagsByKnowledgeId(Long knowledgeId);

    /**
     * 知识库统计信息
     */
    class KnowledgeBaseStats {
        public long totalBases;
        public long totalDocuments;
        public long totalChunks;
        public long activeBases;
    }
}
