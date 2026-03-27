package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeHealth;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface KnowledgeHealthService extends IService<KnowledgeHealth> {

    List<KnowledgeHealth> getHealthHistory(Long knowledgeId);

    KnowledgeHealth checkDocumentCount(Long knowledgeId);

    KnowledgeHealth checkEmbeddingQuality(Long knowledgeId);

    KnowledgeHealth checkIndexStatus(Long knowledgeId);

    Map<String, Object> runFullHealthCheck(Long knowledgeId);

    List<Map<String, Object>> getHealthSummary();

    void scheduleHealthCheck(Long knowledgeId);

    double getOverallHealthScore(Long knowledgeId);
}
