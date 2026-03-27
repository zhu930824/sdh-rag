package cn.sdh.backend.service;

import cn.sdh.backend.entity.KnowledgeDocument;
import java.util.List;
import java.util.Map;

public interface BatchProcessService {

    Map<String, Object> batchUpload(Long knowledgeId, List<String> fileUrls, String category);

    Map<String, Object> batchDelete(List<Long> documentIds);

    Map<String, Object> batchMove(List<Long> documentIds, Long targetKnowledgeId);

    Map<String, Object> batchUpdateCategory(List<Long> documentIds, Long categoryId);

    Map<String, Object> batchProcess(List<Long> documentIds, String processType);

    Map<String, Object> batchEmbed(List<Long> documentIds);

    Map<String, Object> batchExport(List<Long> documentIds, String format);

    void processInBackground(Long batchId, List<Long> documentIds, String processType);

    Map<String, Object> getBatchStatus(Long batchId);

    void cancelBatch(Long batchId);
}
