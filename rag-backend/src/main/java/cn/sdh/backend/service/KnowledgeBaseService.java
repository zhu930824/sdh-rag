package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
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
     * 上传文档到知识库
     * @param knowledgeId 知识库ID
     * @param document 文档实体
     * @return 是否成功
     */
    boolean uploadDocumentToKnowledgeBase(Long knowledgeId, KnowledgeDocument document);

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
}
