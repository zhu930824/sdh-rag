package cn.sdh.backend.graph.node;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;

import java.time.LocalDateTime;

/**
 * Neo4j 节点基类
 */
@Data
public abstract class BaseNode {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String description;

    /**
     * 知识库ID，用于数据隔离
     */
    private Long knowledgeBaseId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
