package cn.sdh.backend.graph.repository;

import cn.sdh.backend.graph.node.EntityNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;

/**
 * 图谱查询 Repository - 仅用于实体节点的基础 CRUD 操作
 * 复杂查询请使用 CustomGraphRepository
 */
public interface GraphRepository extends Neo4jRepository<EntityNode, Long> {
    // 基础 CRUD 操作由 Neo4jRepository 提供
    // 复杂查询（如最短路径、邻居查询等）由 CustomGraphRepository 使用 Neo4jClient 实现
}
