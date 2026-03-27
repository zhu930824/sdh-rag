package cn.sdh.backend.service;

import cn.sdh.backend.entity.DocumentComparison;
import cn.sdh.backend.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface DocumentComparisonService extends IService<DocumentComparison> {

    DocumentComparison compareDocuments(Long docId1, Long docId2, Long userId, String comparisonType);

    DocumentComparison getById(Long id);

    List<DocumentComparison> getByDocumentId(Long docId);

    DocumentComparison getCachedComparison(Long docId1, Long docId2);

    Map<String, Object> getTextDiff(String text1, String text2);

    double calculateSimilarity(String text1, String text2);

    Map<String, Object> compareStructure(KnowledgeDocument doc1, KnowledgeDocument doc2);

    String generateComparisonSummary(DocumentComparison comparison);
}
