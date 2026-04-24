package cn.sdh.backend.memory.layer;

import cn.sdh.backend.memory.config.MemoryConfig;
import cn.sdh.backend.memory.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 模式记忆层 - Neo4j图谱实现
 * 存储用户行为模式，以图谱形式组织
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternMemoryLayer implements MemoryLayer {

    private final Neo4jClient neo4jClient;
    private final MemoryConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void store(MemoryEntry entry) {
        if (entry.getUserId() == null || entry.getContent() == null) {
            log.warn("Pattern memory requires userId and content");
            return;
        }

        try {
            Map<String, Object> pattern = parsePatternContent(entry.getContent());
            if (pattern.isEmpty()) {
                return;
            }

            String patternName = (String) pattern.get("name");
            String patternType = (String) pattern.getOrDefault("type", "behavior");
            String description = (String) pattern.get("description");
            Double confidence = ((Number) pattern.getOrDefault("confidence", 0.5)).doubleValue();

            // 确保用户节点存在
            neo4jClient.query("""
                MERGE (u:User {id: $userId})
                """)
                .bind(entry.getUserId()).to("userId")
                .run();

            // 根据模式类型创建不同节点
            switch (patternType) {
                case "behavior":
                    storeBehaviorPattern(entry.getUserId(), patternName, description, confidence);
                    break;
                case "preference":
                    storePreference(entry.getUserId(), patternName, (String) pattern.get("value"));
                    break;
                case "topic":
                    storeTopic(entry.getUserId(), patternName, (String) pattern.get("level"));
                    break;
                default:
                    log.warn("Unknown pattern type: {}", patternType);
            }

            log.debug("Stored pattern memory: {} - {}", patternType, patternName);
        } catch (Exception e) {
            log.error("Failed to store pattern memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        if (query.getUserId() == null) {
            return Collections.emptyList();
        }

        try {
            List<MemoryEntry> patterns = new ArrayList<>();

            // 获取行为模式
            patterns.addAll(getBehaviorPatterns(query.getUserId()));

            // 获取偏好
            patterns.addAll(getPreferences(query.getUserId()));

            // 获取熟悉主题
            patterns.addAll(getTopics(query.getUserId()));

            return patterns;
        } catch (Exception e) {
            log.error("Failed to recall pattern memory: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void update(MemoryEntry entry) {
        // 更新模式信心度
        try {
            if (entry.getMetadata() != null && entry.getMetadata().containsKey("name")) {
                neo4jClient.query("""
                    MATCH (u:User {id: $userId})-[r:HAS_PATTERN]->(p:BehaviorPattern {name: $name})
                    SET p.confidence = $confidence,
                        p.observedCount = p.observedCount + 1,
                        p.lastObserved = datetime()
                    """)
                    .bind(entry.getUserId()).to("userId")
                    .bind(entry.getMetadata().get("name")).to("name")
                    .bind(entry.getMetadata().get("confidence")).to("confidence")
                    .run();
            }
        } catch (Exception e) {
            log.warn("Failed to update pattern memory: {}", e.getMessage());
        }
    }

    @Override
    public void delete(String id) {
        log.warn("Pattern memory does not support single entry deletion");
    }

    @Override
    public MemoryLayerType getType() {
        return MemoryLayerType.PATTERN;
    }

    @Override
    public void clearByUserId(Long userId) {
        try {
            neo4jClient.query("""
                MATCH (u:User {id: $userId})
                DETACH DELETE u
                """)
                .bind(userId).to("userId")
                .run();
        } catch (Exception e) {
            log.error("Failed to clear pattern memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            neo4jClient.query("RETURN 1").run();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j not available for pattern memory: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取用户行为模式
     */
    public List<MemoryEntry> getBehaviorPatterns(Long userId) {
        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                MATCH (u:User {id: $userId})-[:HAS_PATTERN]->(p:BehaviorPattern)
                RETURN p.name as name, p.description as description, p.confidence as confidence
                ORDER BY p.confidence DESC
                """)
                .bind(userId).to("userId")
                .fetch()
                .all();

            List<MemoryEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                int importance = (int) (((Number) row.get("confidence")).doubleValue() * 10);
                entries.add(MemoryEntry.builder()
                    .id("pattern_" + row.get("name"))
                    .userId(userId)
                    .layer(MemoryLayerType.PATTERN)
                    .type(MemoryType.PATTERN)
                    .content((String) row.get("description"))
                    .importance(importance)
                    .build());
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to get behavior patterns: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户偏好
     */
    public List<MemoryEntry> getPreferences(Long userId) {
        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                MATCH (u:User {id: $userId})-[:PREFERS]->(p:Preference)
                RETURN p.name as name, p.value as value
                """)
                .bind(userId).to("userId")
                .fetch()
                .all();

            List<MemoryEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                entries.add(MemoryEntry.builder()
                    .id("pref_" + row.get("name"))
                    .userId(userId)
                    .layer(MemoryLayerType.PATTERN)
                    .type(MemoryType.PREFERENCE)
                    .content(row.get("name") + ": " + row.get("value"))
                    .importance(6)
                    .build());
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to get preferences: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户熟悉主题
     */
    public List<MemoryEntry> getTopics(Long userId) {
        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                MATCH (u:User {id: $userId})-[:FAMILIAR_WITH]->(t:Topic)
                RETURN t.name as name, t.level as level
                """)
                .bind(userId).to("userId")
                .fetch()
                .all();

            List<MemoryEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                entries.add(MemoryEntry.builder()
                    .id("topic_" + row.get("name"))
                    .userId(userId)
                    .layer(MemoryLayerType.PATTERN)
                    .type(MemoryType.FACT)
                    .content("熟悉" + row.get("name") + "，水平：" + row.get("level"))
                    .importance(5)
                    .build());
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to get topics: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void storeBehaviorPattern(Long userId, String name, String description, Double confidence) {
        neo4jClient.query("""
            MATCH (u:User {id: $userId})
            MERGE (p:BehaviorPattern {name: $name})
            ON CREATE SET p.description = $description, p.confidence = $confidence, p.observedCount = 1
            ON MATCH SET p.confidence = CASE
                WHEN p.confidence < $confidence THEN $confidence
                ELSE p.confidence
            END,
            p.observedCount = p.observedCount + 1,
            p.lastObserved = datetime()
            MERGE (u)-[:HAS_PATTERN]->(p)
            """)
            .bind(userId).to("userId")
            .bind(name).to("name")
            .bind(description).to("description")
            .bind(confidence).to("confidence")
            .run();
    }

    private void storePreference(Long userId, String name, String value) {
        neo4jClient.query("""
            MATCH (u:User {id: $userId})
            MERGE (p:Preference {name: $name})
            SET p.value = $value, p.updatedAt = datetime()
            MERGE (u)-[:PREFERS]->(p)
            """)
            .bind(userId).to("userId")
            .bind(name).to("name")
            .bind(value).to("value")
            .run();
    }

    private void storeTopic(Long userId, String name, String level) {
        neo4jClient.query("""
            MATCH (u:User {id: $userId})
            MERGE (t:Topic {name: $name})
            SET t.level = $level
            MERGE (u)-[:FAMILIAR_WITH]->(t)
            """)
            .bind(userId).to("userId")
            .bind(name).to("name")
            .bind(level).to("level")
            .run();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePatternContent(String content) {
        Map<String, Object> pattern = new HashMap<>();
        try {
            if (content.startsWith("{")) {
                pattern = objectMapper.readValue(content, Map.class);
            } else if (content.contains(":")) {
                String[] parts = content.split(":", 2);
                pattern.put("name", parts[0].trim());
                pattern.put("description", parts[1].trim());
                pattern.put("type", "behavior");
                pattern.put("confidence", 0.7);
            }
        } catch (Exception e) {
            log.warn("Failed to parse pattern content: {}", content);
        }
        return pattern;
    }
}
