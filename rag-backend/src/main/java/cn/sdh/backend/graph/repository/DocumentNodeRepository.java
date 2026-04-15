package cn.sdh.backend.graph.repository;

import cn.sdh.backend.graph.node.DocumentNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 文档节点 Repository
 */
public interface DocumentNodeRepository extends Neo4jRepository<DocumentNode, Long> {

    List<DocumentNode> findAllByDocumentId(Long documentId);

    default Optional<DocumentNode> findByDocumentId(Long documentId) {
        List<DocumentNode> nodes = findAllByDocumentId(documentId);
        return nodes.isEmpty() ? Optional.empty() : Optional.of(nodes.get(0));
    }

    @Query("MATCH (d:Document) WHERE d.documentId = $documentId DELETE d")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    @Query("MATCH (d:Document) RETURN d ORDER BY d.createTime DESC LIMIT $limit")
    List<DocumentNode> findTopDocuments(@Param("limit") int limit);

    @Query("MATCH (d:Document)-[r]-(n) WHERE id(d) = $id RETURN d, collect(r), collect(n)")
    Optional<DocumentNode> findByIdWithRelationships(@Param("id") Long id);
}
