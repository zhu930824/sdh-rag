package cn.sdh.backend.graph.repository;

import cn.sdh.backend.graph.node.KeywordNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 关键词节点 Repository
 */
public interface KeywordNodeRepository extends Neo4jRepository<KeywordNode, Long> {

    List<KeywordNode> findAllByName(String name);

    default Optional<KeywordNode> findByName(String name) {
        List<KeywordNode> nodes = findAllByName(name);
        return nodes.isEmpty() ? Optional.empty() : Optional.of(nodes.get(0));
    }

    Optional<KeywordNode> findByNameAndKnowledgeBaseId(String name, Long knowledgeBaseId);

    @Query("MATCH (k:Keyword) WHERE k.name CONTAINS $keyword RETURN k ORDER BY k.frequency DESC LIMIT $limit")
    List<KeywordNode> searchByName(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query("MATCH (k:Keyword) WHERE k.name CONTAINS $keyword AND k.knowledgeBaseId = $knowledgeBaseId RETURN k ORDER BY k.frequency DESC LIMIT $limit")
    List<KeywordNode> searchByNameAndKnowledgeBaseId(@Param("keyword") String keyword, @Param("knowledgeBaseId") Long knowledgeBaseId, @Param("limit") int limit);

    @Query("MATCH (k:Keyword) RETURN k ORDER BY k.frequency DESC LIMIT $limit")
    List<KeywordNode> findTopKeywords(@Param("limit") int limit);

    @Query("MATCH (k:Keyword) WHERE k.knowledgeBaseId = $knowledgeBaseId RETURN k ORDER BY k.frequency DESC LIMIT $limit")
    List<KeywordNode> findTopByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("limit") int limit);
}
