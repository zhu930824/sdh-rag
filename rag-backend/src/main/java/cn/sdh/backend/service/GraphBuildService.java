package cn.sdh.backend.service;

import cn.sdh.backend.dto.GraphBuildResponse;

/**
 * 图谱构建服务接口
 */
public interface GraphBuildService {

    /**
     * 从文档构建图谱
     * @param documentId 文档ID
     * @return 构建结果
     */
    GraphBuildResponse buildFromDocument(Long documentId);

    /**
     * 重建文档图谱
     * @param documentId 文档ID
     * @return 构建结果
     */
    GraphBuildResponse rebuildFromDocument(Long documentId);

    /**
     * 删除文档相关的图谱数据
     * @param documentId 文档ID
     */
    void deleteByDocument(Long documentId);

    /**
     * 批量构建图谱
     * @param documentIds 文档ID列表
     */
    void batchBuild(Long[] documentIds);
}
