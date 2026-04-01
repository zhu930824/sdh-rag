package cn.sdh.backend.graph.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * 实体节点 - Neo4j
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Node("Entity")
public class EntityNode extends BaseNode {

    /**
     * 实体类型: PERSON/ORG/LOCATION/DATE/MISC
     */
    private String entityType;

    /**
     * 来源文档ID
     */
    private Long sourceDocumentId;

    /**
     * 置信度
     */
    private Double confidence;

    /**
     * 出现次数
     */
    private Integer frequency = 1;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private Set<EntityNode> relatedEntities = new HashSet<>();

    @Relationship(type = "INSTANCE_OF", direction = Relationship.Direction.OUTGOING)
    private Set<ConceptNode> concepts = new HashSet<>();
}
