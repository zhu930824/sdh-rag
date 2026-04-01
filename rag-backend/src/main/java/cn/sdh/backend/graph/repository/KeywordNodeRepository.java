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

    Optional<KeywordNode> findByName(String name);

    @Query("MATCH (k:Keyword) WHERE k.name CONTAINS $keyword RETURN k ORDER BY k.frequency DESC LIMIT $limit")
    List<KeywordNode> searchByName(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query("MATCH (k:Keyword) RETURN k ORDER BY k.frequency DESC LIMIT $limit")
    List<KeywordNode> findTopKeywords(@Param("limit") int limit);
}
