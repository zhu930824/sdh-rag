package cn.sdh.backend.service;

import java.util.List;

/**
 * 重排序服务接口
 */
public interface RerankService {

    /**
     * 对文档进行重排序
     * @param query 查询文本
     * @param documents 文档列表
     * @param topK 返回数量
     * @param rerankModel 重排序模型名称
     * @return 重排序后的文档列表（按相关性降序）
     */
    List<RerankResult> rerank(String query, List<String> documents, int topK, String rerankModel);

    /**
     * 重排序结果
     */
    class RerankResult {
        private int index;
        private String document;
        private double score;

        public RerankResult() {}

        public RerankResult(int index, String document, double score) {
            this.index = index;
            this.document = document;
            this.score = score;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getDocument() {
            return document;
        }

        public void setDocument(String document) {
            this.document = document;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
