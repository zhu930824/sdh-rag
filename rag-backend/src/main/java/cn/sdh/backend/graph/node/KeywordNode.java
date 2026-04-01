package cn.sdh.backend.graph.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * 关键词节点 - Neo4j
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Node("Keyword")
public class KeywordNode extends BaseNode {

    /**
     * 来源文档ID
     */
    private Long sourceDocumentId;

    /**
     * TF-IDF 权重
     */
    private Double tfidf;

    /**
     * 出现次数
     */
    private Integer frequency = 1;
}
