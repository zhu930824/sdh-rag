package cn.sdh.backend.graph.repository;

import cn.sdh.backend.graph.node.ConceptNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 概念节点 Repository
 */
public interface ConceptNodeRepository extends Neo4jRepository<ConceptNode, Long> {

    List<ConceptNode> findAllByName(String name);

    default Optional<ConceptNode> findByName(String name) {
        List<ConceptNode> nodes = findAllByName(name);
        return nodes.isEmpty() ? Optional.empty() : Optional.of(nodes.get(0));
    }

    Optional<ConceptNode> findByNameAndKnowledgeBaseId(String name, Long knowledgeBaseId);

    @Query("MATCH (c:Concept) WHERE c.name CONTAINS $keyword RETURN c ORDER BY c.weight DESC LIMIT $limit")
    List<ConceptNode> searchByName(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query("MATCH (c:Concept) WHERE c.name CONTAINS $keyword AND c.knowledgeBaseId = $knowledgeBaseId RETURN c ORDER BY c.weight DESC LIMIT $limit")
    List<ConceptNode> searchByNameAndKnowledgeBaseId(@Param("keyword") String keyword, @Param("knowledgeBaseId") Long knowledgeBaseId, @Param("limit") int limit);

    @Query("MATCH (c:Concept)-[r]-(n) WHERE id(c) = $id RETURN c, collect(r), collect(n)")
    Optional<ConceptNode> findByIdWithRelationships(@Param("id") Long id);

    @Query("MATCH (c:Concept) RETURN c ORDER BY c.weight DESC LIMIT $limit")
    List<ConceptNode> findTopConcepts(@Param("limit") int limit);

    @Query("MATCH (c:Concept) WHERE c.knowledgeBaseId = $knowledgeBaseId RETURN c ORDER BY c.weight DESC LIMIT $limit")
    List<ConceptNode> findTopByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("limit") int limit);
}
