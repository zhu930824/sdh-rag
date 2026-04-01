package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体提取结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityExtractionResult {

    /**
     * 提取的实体列表
     */
    @Builder.Default
    private List<EntityInfo> entities = new ArrayList<>();

    /**
     * 提取的关系列表
     */
    @Builder.Default
    private List<RelationInfo> relations = new ArrayList<>();

    /**
     * 提取的概念列表
     */
    @Builder.Default
    private List<ConceptInfo> concepts = new ArrayList<>();

    /**
     * 提取的关键词列表
     */
    @Builder.Default
    private List<KeywordInfo> keywords = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityInfo {
        /**
         * 实体名称
         */
        private String name;

        /**
         * 实体类型: PERSON/ORG/LOCATION/DATE/MISC
         */
        private String entityType;

        /**
         * 实体描述
         */
        private String description;

        /**
         * 置信度
         */
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationInfo {
        /**
         * 源实体名称
         */
        private String sourceName;

        /**
         * 源实体类型
         */
        private String sourceType;

        /**
         * 目标实体名称
         */
        private String targetName;

        /**
         * 目标实体类型
         */
        private String targetType;

        /**
         * 关系类型
         */
        private String relationType;

        /**
         * 关系权重
         */
        private Double weight;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConceptInfo {
        /**
         * 概念名称
         */
        private String name;

        /**
         * 概念描述
         */
        private String description;

        /**
         * 概念分类
         */
        private String category;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordInfo {
        /**
         * 关键词
         */
        private String keyword;

        /**
         * TF-IDF 权重
         */
        private Double tfidf;
    }
}
