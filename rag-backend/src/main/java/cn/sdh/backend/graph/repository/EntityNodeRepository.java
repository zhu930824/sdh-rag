package cn.sdh.backend.graph.repository;

import cn.sdh.backend.graph.node.EntityNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 实体节点 Repository
 */
public interface EntityNodeRepository extends Neo4jRepository<EntityNode, Long> {

    List<EntityNode> findByEntityType(String entityType);

    Optional<EntityNode> findByNameAndEntityType(String name, String entityType);

    @Query("MATCH (e:Entity) WHERE e.name CONTAINS $keyword RETURN e ORDER BY e.frequency DESC LIMIT $limit")
    List<EntityNode> searchByName(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query("MATCH (e:Entity)-[r]-(n) WHERE id(e) = $id RETURN e, collect(r), collect(n)")
    Optional<EntityNode> findByIdWithRelationships(@Param("id") Long id);

    @Query("MATCH (e:Entity) RETURN e.entityType as type, count(e) as count ORDER BY count DESC")
    List<Object[]> countByEntityType();

    @Query("MATCH (e:Entity) WHERE e.entityType = $entityType RETURN e ORDER BY e.frequency DESC LIMIT $limit")
    List<EntityNode> findTopByEntityType(@Param("entityType") String entityType, @Param("limit") int limit);
}
