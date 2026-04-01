package cn.sdh.backend.graph.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * 概念节点 - Neo4j
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Node("Concept")
public class ConceptNode extends BaseNode {

    /**
     * 概念分类
     */
    private String category;

    /**
     * 权重（被引用次数）
     */
    private Integer weight = 0;

    @Relationship(type = "PARENT_OF", direction = Relationship.Direction.OUTGOING)
    private Set<ConceptNode> childConcepts = new HashSet<>();

    @Relationship(type = "INSTANCE_OF", direction = Relationship.Direction.INCOMING)
    private Set<EntityNode> instances = new HashSet<>();
}
