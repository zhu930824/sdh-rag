package cn.sdh.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 文档关联配置DTO
 * 用于关联文档时设置单个文档的切分策略
 */
@Data
public class DocumentLinkConfig {

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 切分策略模式
     * - default: 默认模式，使用知识库全局配置
     * - smart: 智能切分，根据文档内容自动调整
     * - custom: 自定义模式，使用自定义参数
     */
    private String chunkMode = "default";

    /**
     * 自定义切分方式（仅当 chunkMode = custom 时生效）
     * - length: 按长度切分
     * - page: 按页切分
     * - heading: 按标题切分
     * - regex: 按正则切分
     */
    private String splitType = "length";

    /**
     * 分块大小（字符数）
     * 仅当 splitType = length 时生效
     */
    private Integer chunkSize;

    /**
     * 分块重叠大小
     * 仅当 splitType = length 时生效
     */
    private Integer chunkOverlap;

    /**
     * 每块页数
     * 仅当 splitType = page 时生效
     */
    private Integer pagesPerChunk;

    /**
     * 标题层级列表
     * 仅当 splitType = heading 时生效
     * 例如：["h1", "h2", "h3"]
     */
    private List<String> headingLevels;

    /**
     * 正则表达式
     * 仅当 splitType = regex 时生效
     */
    private String regexPattern;

    /**
     * 嵌入模型
     * 可选，不设置则使用知识库默认值
     */
    private String embeddingModel;

    /**
     * 智能切分参数
     * 仅当 chunkMode = smart 时生效
     */
    private SmartChunkConfig smartConfig;

    /**
     * 智能切分配置
     */
    @Data
    public static class SmartChunkConfig {
        /**
         * 是否识别段落结构
         */
        private Boolean respectParagraphs = true;

        /**
         * 是否识别标题层级
         */
        private Boolean respectHeaders = true;

        /**
         * 最大分块大小（智能切分上限）
         */
        private Integer maxChunkSize = 1000;

        /**
         * 最小分块大小（智能切分下限）
         */
        private Integer minChunkSize = 100;

        /**
         * 句子分割模式：simple（简单）、semantic（语义）
         */
        private String sentenceMode = "semantic";
    }
}
