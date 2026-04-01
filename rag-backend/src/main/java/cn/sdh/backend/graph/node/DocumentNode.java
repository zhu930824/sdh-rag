package cn.sdh.backend.graph.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * 文档节点 - Neo4j
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Node("Document")
public class DocumentNode extends BaseNode {

    private Long documentId;

    private String title;

    private String fileType;

    private Long userId;

    private Long categoryId;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private Set<EntityNode> entities = new HashSet<>();

    @Relationship(type = "REFERENCES", direction = Relationship.Direction.OUTGOING)
    private Set<ConceptNode> concepts = new HashSet<>();

    @Relationship(type = "HAS_KEYWORD", direction = Relationship.Direction.OUTGOING)
    private Set<KeywordNode> keywords = new HashSet<>();
}
