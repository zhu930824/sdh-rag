# RAG 企业级检索优化 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 SDH-RAG 从"单层平铺检索"升级为"清洗分层 → 索引路由 → 渐进召回"的四层企业级架构，支撑万级文档量下的高精度、低时延检索。

**Architecture:** 四层改造 — (1) 入库预处理：MD5/MinHash去重 + 质量过滤 + 新鲜度打标；(2) 索引路由：摘要索引 + 元数据预过滤 + 父子层级 + 冷热分离；(3) 级联检索：摘要粗筛→Reranker精排→MMR细排三级漏斗；(4) 后处理：硬阈值截断 + 时间衰减重排 + 降级策略矩阵。

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis Plus, Elasticsearch 8, Spring AI, Vue 3 + TypeScript + Ant Design Vue

**Spec:** `docs/superpowers/specs/2026-06-17-rag-enterprise-optimization-design.md`

---

## 文件结构总览

### 新增文件

| 文件 | 职责 |
|------|------|
| `rag-backend/src/main/java/cn/sdh/backend/service/DocumentDedupService.java` | 去重服务接口 |
| `rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentDedupServiceImpl.java` | MD5 + MinHash 去重实现 |
| `rag-backend/src/main/java/cn/sdh/backend/service/SummaryIndexService.java` | 摘要索引接口 |
| `rag-backend/src/main/java/cn/sdh/backend/service/impl/SummaryIndexServiceImpl.java` | 摘要索引 ES 操作 + 粗筛检索 |
| `rag-backend/src/main/java/cn/sdh/backend/service/DiversityRankingService.java` | MMR 多样性重排 |
| `rag-backend/src/main/java/cn/sdh/backend/service/PostProcessingService.java` | 阈值截断 + 时间衰减 |
| `rag-backend/src/main/java/cn/sdh/backend/config/FreshnessConfig.java` | 新鲜度计算工具类 |
| `rag-backend/src/main/java/cn/sdh/backend/entity/DocumentHash.java` | document_hash 表实体 |
| `rag-backend/src/main/java/cn/sdh/backend/mapper/DocumentHashMapper.java` | document_hash MyBatis Mapper |
| `rag-backend/src/main/java/cn/sdh/backend/dto/MetadataFilter.java` | 元数据过滤 DTO |
| `rag-backend/src/main/java/cn/sdh/backend/dto/SearchResult.java` | 层级搜索结果 DTO |
| `rag-backend/src/main/resources/db/migration/V2__rag_optimization.sql` | 数据库迁移 |

### 修改文件

| 文件 | 变更内容 |
|------|---------|
| `rag-backend/.../entity/KnowledgeBase.java` | +8 新配置字段 |
| `rag-backend/.../entity/KnowledgeDocument.java` | +6 元数据字段 |
| `rag-backend/.../service/VectorStoreService.java` | +metadataFilter 参数 / +方法 |
| `rag-backend/.../service/RagSearchService.java` | +searchWithCascade 方法 |
| `rag-backend/.../service/impl/VectorStoreServiceImpl.java` | +元数据过滤 / +冷热索引 / +按文档ID批量检索 |
| `rag-backend/.../service/impl/RagSearchServiceImpl.java` | 三级漏斗重构 |
| `rag-backend/.../service/impl/DocumentProcessServiceImpl.java` | +去重/质量过滤/摘要生成/新鲜度 |
| `rag-backend/.../service/impl/ElasticsearchIndexServiceImpl.java` | +摘要索引创建 / +冷索引创建 |
| `rag-backend/.../service/impl/ChatServiceImpl.java` | +层级上下文 prompt |
| `rag-backend/.../service/impl/KnowledgeBaseServiceImpl.java` | +新配置字段保存 |
| `rag-backend/.../controller/KnowledgeBaseController.java` | +新配置字段 API |
| `rag-frontend/src/views/chat/components/ChatInput.vue` | +高级筛选面板 |
| `rag-frontend/src/views/knowledge-base/components/ConfigPanel.vue` | +新配置项 |
| `rag-frontend/src/views/document/components/DocumentUpload.vue` | +元数据采集表单 |
| `rag-frontend/src/api/chat.ts` | +SearchRequest 类型更新 |
| `rag-frontend/src/api/knowledgeBase.ts` | +新配置字段类型 |
| `rag-frontend/src/types/search.ts` | 新增 MetadataFilter, SearchResult 类型 |

---

## Phase 1: 基础设施（数据库 + 实体）

### Task 1: 数据库迁移

**Files:**
- Create: `rag-backend/src/main/resources/db/migration/V2__rag_optimization.sql`

- [ ] **Step 1: 编写 SQL 迁移脚本**

```sql
-- V2: RAG 企业级检索优化
-- 1. 新增去重表
CREATE TABLE IF NOT EXISTS document_hash (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    knowledge_id BIGINT NOT NULL COMMENT '知识库ID',
    content_md5 VARCHAR(32) NOT NULL COMMENT '全文MD5哈希',
    minhash_signature TEXT COMMENT 'MinHash签名(JSON数组)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_knowledge_md5 (knowledge_id, content_md5),
    INDEX idx_document_id (document_id)
) COMMENT '文档去重哈希表';

-- 2. knowledge_document 新增元数据字段
ALTER TABLE knowledge_document
    ADD COLUMN department VARCHAR(100) COMMENT '部门',
    ADD COLUMN doc_type VARCHAR(50) COMMENT '文档类型: policy/manual/contract/report/faq',
    ADD COLUMN doc_date DATE COMMENT '文档日期',
    ADD COLUMN freshness_score DECIMAL(3,2) DEFAULT 1.0 COMMENT '新鲜度评分0-1',
    ADD COLUMN content_md5 VARCHAR(32) COMMENT '内容MD5',
    ADD COLUMN quality_status TINYINT DEFAULT 0 COMMENT '0=正常 1=低质 2=疑似重复';

-- 3. knowledge_base 新增检索配置字段
ALTER TABLE knowledge_base
    ADD COLUMN enable_summary_index TINYINT(1) DEFAULT 1 COMMENT '启用摘要索引',
    ADD COLUMN summary_coarse_top_k INT DEFAULT 50 COMMENT '摘要粗筛返回数',
    ADD COLUMN enable_cold_archive TINYINT(1) DEFAULT 1 COMMENT '启用冷热分离',
    ADD COLUMN hot_min_results INT DEFAULT 3 COMMENT '热库最低结果数',
    ADD COLUMN mmr_lambda DECIMAL(3,2) DEFAULT 0.70 COMMENT 'MMR相关性权重',
    ADD COLUMN recency_alpha DECIMAL(3,2) DEFAULT 0.30 COMMENT '时间衰减系数',
    ADD COLUMN coarse_top_k INT DEFAULT 50 COMMENT '粗筛返回文档数',
    ADD COLUMN fine_top_k INT DEFAULT 20 COMMENT '精排返回chunk数';
```

- [ ] **Step 2: 执行迁移验证**

```bash
cd rag-backend
# 如果使用 Flyway，启动时自动执行
# 或者手动执行 SQL
```

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/resources/db/migration/V2__rag_optimization.sql
git commit -m "feat: add RAG optimization database migration

- Add document_hash table for MD5/MinHash dedup
- Add metadata fields to knowledge_document (department, doc_type, doc_date, freshness_score, content_md5, quality_status)
- Add retrieval config fields to knowledge_base (summary_index, cold_archive, MMR, recency settings)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 实体层更新

**Files:**
- Modify: `rag-backend/src/main/java/cn/sdh/backend/entity/KnowledgeBase.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/entity/KnowledgeDocument.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/entity/DocumentHash.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/mapper/DocumentHashMapper.java`

- [ ] **Step 1: 更新 KnowledgeBase 实体**

在 `KnowledgeBase.java` 现有字段末尾（`updateTime` 之前）添加：

```java
    // ==================== RAG 优化新增配置 ====================

    /** 是否启用摘要索引 */
    @TableField("enable_summary_index")
    private Boolean enableSummaryIndex;

    /** 摘要粗筛返回文档数 */
    @TableField("summary_coarse_top_k")
    private Integer summaryCoarseTopK;

    /** 是否启用冷热分离 */
    @TableField("enable_cold_archive")
    private Boolean enableColdArchive;

    /** 热库最低结果数（不足触发冷库） */
    @TableField("hot_min_results")
    private Integer hotMinResults;

    /** MMR 相关性权重（0-1，越大越偏向相关性） */
    @TableField("mmr_lambda")
    private Double mmrLambda;

    /** 时间衰减系数（0-1，越大新鲜度影响越大） */
    @TableField("recency_alpha")
    private Double recencyAlpha;

    /** 粗筛返回文档数 */
    @TableField("coarse_top_k")
    private Integer coarseTopK;

    /** 精排返回 chunk 数 */
    @TableField("fine_top_k")
    private Integer fineTopK;
```

- [ ] **Step 2: 更新 KnowledgeDocument 实体**

在现有字段中添加（参考前次探索中的实体结构）：

```java
    /** 部门 */
    private String department;

    /** 文档类型: policy/manual/contract/report/faq */
    @TableField("doc_type")
    private String docType;

    /** 文档日期 */
    @TableField("doc_date")
    private LocalDate docDate;

    /** 新鲜度评分 0-1（入库时自动计算） */
    @TableField("freshness_score")
    private Double freshnessScore;

    /** 内容 MD5 哈希 */
    @TableField("content_md5")
    private String contentMd5;

    /** 质量状态: 0=正常 1=低质 2=疑似重复 */
    @TableField("quality_status")
    private Integer qualityStatus;
```

- [ ] **Step 3: 创建 DocumentHash 实体**

```java
package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("document_hash")
public class DocumentHash implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("content_md5")
    private String contentMd5;

    @TableField("minhash_signature")
    private String minhashSignature;

    @TableField("create_time")
    private LocalDateTime createTime;
}
```

- [ ] **Step 4: 创建 DocumentHashMapper**

```java
package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.DocumentHash;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentHashMapper extends BaseMapper<DocumentHash> {

    /**
     * 按知识库ID和MD5查找（用于快速判重）
     */
    default DocumentHash findByKnowledgeAndMd5(Long knowledgeId, String contentMd5) {
        return selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentHash>()
                .eq(DocumentHash::getKnowledgeId, knowledgeId)
                .eq(DocumentHash::getContentMd5, contentMd5)
        );
    }

    /**
     * 按知识库ID查找所有哈希记录（用于 MinHash 比对）
     */
    default java.util.List<DocumentHash> findByKnowledgeId(Long knowledgeId) {
        return selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentHash>()
                .eq(DocumentHash::getKnowledgeId, knowledgeId)
        );
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/entity/KnowledgeBase.java \
        rag-backend/src/main/java/cn/sdh/backend/entity/KnowledgeDocument.java \
        rag-backend/src/main/java/cn/sdh/backend/entity/DocumentHash.java \
        rag-backend/src/main/java/cn/sdh/backend/mapper/DocumentHashMapper.java
git commit -m "feat: add entity and mapper for RAG optimization"
```

---

### Task 3: DTO 层新增

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/dto/MetadataFilter.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/dto/SearchResult.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterResponse.java` (根据文件加密情况，可独立新建)

- [ ] **Step 1: 创建 MetadataFilter DTO**

```java
package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 元数据过滤条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataFilter {

    /** 部门 */
    private String department;

    /** 文档类型 */
    private String docType;

    /** 文档日期起始 */
    private LocalDate dateFrom;

    /** 文档日期截止 */
    private LocalDate dateTo;

    /** 自定义过滤条件 */
    private Map<String, Object> customFilters;

    /**
     * 转换为 ES term query 的 Map
     */
    public Map<String, Object> toEsFilters() {
        Map<String, Object> filters = new HashMap<>();
        if (department != null && !department.isEmpty()) {
            filters.put("department", department);
        }
        if (docType != null && !docType.isEmpty()) {
            filters.put("doc_type", docType);
        }
        if (customFilters != null) {
            filters.putAll(customFilters);
        }
        return filters;
    }

    public boolean hasDateRange() {
        return dateFrom != null || dateTo != null;
    }
}
```

- [ ] **Step 2: 创建 SearchResult DTO（层级结构）**

```java
package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 层级搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /** 检索是否成功 */
    private boolean success;

    /** 降级提示消息 */
    private String message;

    /** 最终返回的片段 */
    private List<RankedChunk> chunks;

    /** 原始查询 */
    private String originalQuery;

    /** 是否触发降级策略 */
    private boolean fallbackTriggered;

    /** 降级原因 */
    private String fallbackReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedChunk {
        private Long documentId;
        private String documentTitle;
        private String documentSummary;
        private String content;
        private Integer chunkIndex;
        private Integer totalChunks;
        private Double relevanceScore;
        private Double freshnessScore;
        private Double finalScore;
        private String department;
        private String docType;
        private java.time.LocalDate docDate;
    }

    public static SearchResult empty(String reason) {
        return SearchResult.builder()
            .success(false)
            .message("知识库中未找到与该问题匹配的文档内容。建议：\n1. 更换关键词重新提问\n2. 检查是否有相关文档已上传\n3. 尝试扩大知识库检索范围")
            .fallbackTriggered(true)
            .fallbackReason(reason)
            .chunks(List.of())
            .build();
    }

    public static SearchResult of(List<RankedChunk> chunks, String originalQuery) {
        return SearchResult.builder()
            .success(true)
            .chunks(chunks)
            .originalQuery(originalQuery)
            .fallbackTriggered(false)
            .build();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/dto/
git commit -m "feat: add MetadataFilter and SearchResult DTOs for RAG optimization"
```

---

## Phase 2: 第一层 — 入库前数据预处理

### Task 4: 新鲜度计算 + 质量过滤

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/config/FreshnessConfig.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentProcessServiceImpl.java`

- [ ] **Step 1: 创建 FreshnessConfig 工具类**

```java
package cn.sdh.backend.config;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 文档新鲜度计算工具
 */
public class FreshnessConfig {

    /** 半年内满分 */
    private static final long FULL_SCORE_MONTHS = 6L;
    /** 线性衰减到 0.5 的月数 */
    private static final long HALF_SCORE_MONTHS = 12L;
    /** 线性衰减到 0.3 的月数 */
    private static final long LOW_SCORE_MONTHS = 24L;

    /** 新鲜度满分 */
    public static final double MAX_FRESHNESS = 1.0;
    /** 新鲜度保底分 */
    public static final double MIN_FRESHNESS = 0.3;
    /** 冷热分离阈值 */
    public static final double HOT_COLD_THRESHOLD = 0.5;

    /**
     * 根据文档日期计算新鲜度评分
     * @param docDate 文档日期
     * @return 0-1 之间的新鲜度评分
     */
    public static double calculateFreshness(LocalDate docDate) {
        if (docDate == null) {
            return 0.5; // 无日期默认中等
        }
        long months = ChronoUnit.MONTHS.between(docDate, LocalDate.now());

        if (months <= FULL_SCORE_MONTHS) {
            return MAX_FRESHNESS; // 6个月内满分
        }
        if (months <= HALF_SCORE_MONTHS) {
            // 6-12月线性衰减到 0.5
            return MAX_FRESHNESS - 0.5 * (months - FULL_SCORE_MONTHS) / (double)(HALF_SCORE_MONTHS - FULL_SCORE_MONTHS);
        }
        if (months <= LOW_SCORE_MONTHS) {
            // 12-24月线性衰减到 0.3
            return 0.5 - 0.2 * (months - HALF_SCORE_MONTHS) / (double)(LOW_SCORE_MONTHS - HALF_SCORE_MONTHS);
        }
        return MIN_FRESHNESS; // 超过2年保底 0.3
    }

    /**
     * 判断文档是否属于热库
     */
    public static boolean isHotDocument(Double freshnessScore) {
        return freshnessScore != null && freshnessScore >= HOT_COLD_THRESHOLD;
    }
}
```

- [ ] **Step 2: 在 DocumentProcessServiceImpl 中插入质量过滤**

在 `processDocumentForKnowledge` 方法中，`parseDocumentContent` 调用之后、分块逻辑之前，插入：

```java
// === RAG优化：质量过滤 ===
String content = parseDocumentContent(filePath);

// 质量检查1: 内容长度
if (content == null || content.trim().length() < 50) {
    markDocumentLowQuality(document, "文档内容过短（< 50字符），不适合建立向量索引");
    updateDocumentRelation(relation, 2); // 标记为处理完成但低质
    return;
}

// 质量检查2: 纯结构化数据检测（表格/数字占比 > 80%）
if (isTabularNoise(content)) {
    markDocumentLowQuality(document, "文档主要为结构化表格数据，建议走 Text2SQL 路线");
    updateDocumentRelation(relation, 2);
    return;
}

// === RAG优化：新鲜度计算 ===
if (document.getDocDate() != null) {
    double freshness = FreshnessConfig.calculateFreshness(document.getDocDate());
    document.setFreshnessScore(freshness);
    knowledgeDocumentMapper.updateById(document);
}

// 继续现有的分块逻辑...
```

添加辅助方法：

```java
/**
 * 检测内容是否为纯结构化噪音（表格/数字占比过高）
 */
private boolean isTabularNoise(String content) {
    if (content == null || content.isEmpty()) return false;
    // 统计非字母中文字符占比
    int totalChars = content.replaceAll("\\s+", "").length();
    if (totalChars == 0) return false;
    // 统计数字和表格分隔符
    long numericAndTabular = content.chars()
        .filter(c -> Character.isDigit(c) || c == '|' || c == '\t' || c == ',')
        .count();
    return (double) numericAndTabular / totalChars > 0.8;
}

/**
 * 标记文档为低质量
 */
private void markDocumentLowQuality(KnowledgeDocument document, String reason) {
    document.setQualityStatus(1);
    knowledgeDocumentMapper.updateById(document);
    log.warn("文档 [{}] 标记为低质: {}", document.getId(), reason);
}
```

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/config/FreshnessConfig.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentProcessServiceImpl.java
git commit -m "feat: add quality filter and freshness calculation to document processing"
```

---

### Task 5: 文档去重服务

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/DocumentDedupService.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentDedupServiceImpl.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentProcessServiceImpl.java`

- [ ] **Step 1: 创建 DocumentDedupService 接口**

```java
package cn.sdh.backend.service;

/**
 * 文档去重服务
 */
public interface DocumentDedupService {

    /**
     * MD5 快速判重
     * @param knowledgeId 知识库ID
     * @param contentMd5 内容MD5哈希
     * @return 已存在的文档ID，null 表示不重复
     */
    Long checkMd5Duplicate(Long knowledgeId, String contentMd5);

    /**
     * MinHash 近似查重
     * @param knowledgeId 知识库ID
     * @param content 文档内容
     * @param threshold Jaccard 相似度阈值（0-1），默认 0.85
     * @return 最相似的文档ID和相似度，null 表示无相似文档
     */
    SimilarDocResult checkMinHashSimilarity(Long knowledgeId, String content, double threshold);

    /**
     * 保存文档哈希
     */
    void saveDocumentHash(Long documentId, Long knowledgeId, String contentMd5, String content);

    /**
     * MinHash 近似查重结果
     */
    record SimilarDocResult(Long documentId, Double similarity) {}
}
```

- [ ] **Step 2: 创建 DocumentDedupServiceImpl 实现**

```java
package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentHash;
import cn.sdh.backend.mapper.DocumentHashMapper;
import cn.sdh.backend.service.DocumentDedupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDedupServiceImpl implements DocumentDedupService {

    private final DocumentHashMapper documentHashMapper;
    private final ObjectMapper objectMapper;

    private static final int MINHASH_SEED_COUNT = 128; // MinHash 签名维度

    @Override
    public Long checkMd5Duplicate(Long knowledgeId, String contentMd5) {
        DocumentHash existing = documentHashMapper.findByKnowledgeAndMd5(knowledgeId, contentMd5);
        if (existing != null) {
            log.info("发现MD5重复文档: knowledgeId={}, existingDocId={}", knowledgeId, existing.getDocumentId());
            return existing.getDocumentId();
        }
        return null;
    }

    @Override
    public SimilarDocResult checkMinHashSimilarity(Long knowledgeId, String content, double threshold) {
        int[] querySignature = computeMinHashSignature(content);
        List<DocumentHash> allHashes = documentHashMapper.findByKnowledgeId(knowledgeId);

        double bestSimilarity = 0;
        Long bestDocId = null;

        for (DocumentHash docHash : allHashes) {
            if (docHash.getMinhashSignature() == null) continue;
            try {
                int[] existingSignature = objectMapper.readValue(
                    docHash.getMinhashSignature(), int[].class);
                double similarity = jaccardSimilarity(querySignature, existingSignature);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestDocId = docHash.getDocumentId();
                }
            } catch (JsonProcessingException e) {
                log.warn("MinHash签名解析失败: docId={}", docHash.getDocumentId());
            }
        }

        if (bestSimilarity >= threshold) {
            log.info("发现近似重复文档: knowledgeId={}, existingDocId={}, similarity={}",
                knowledgeId, bestDocId, bestSimilarity);
            return new SimilarDocResult(bestDocId, bestSimilarity);
        }
        return null;
    }

    @Override
    public void saveDocumentHash(Long documentId, Long knowledgeId, String contentMd5, String content) {
        DocumentHash hash = new DocumentHash();
        hash.setDocumentId(documentId);
        hash.setKnowledgeId(knowledgeId);
        hash.setContentMd5(contentMd5);
        try {
            hash.setMinhashSignature(
                objectMapper.writeValueAsString(computeMinHashSignature(content)));
        } catch (JsonProcessingException e) {
            log.error("MinHash签名序列化失败", e);
        }
        hash.setCreateTime(java.time.LocalDateTime.now());
        documentHashMapper.insert(hash);
    }

    /**
     * 计算 MinHash 签名（128维，使用简单哈希）
     * 注意：生产环境可替换为 MurmurHash3 以提升精度
     */
    private int[] computeMinHashSignature(String content) {
        // 使用 3-gram shingles
        Set<String> shingles = new HashSet<>();
        for (int i = 0; i < content.length() - 2; i++) {
            shingles.add(content.substring(i, i + 3));
        }
        String[] shingleArray = shingles.toArray(new String[0]);

        int[] signature = new int[MINHASH_SEED_COUNT];
        Arrays.fill(signature, Integer.MAX_VALUE);

        for (int seed = 0; seed < MINHASH_SEED_COUNT; seed++) {
            final int s = seed; // effectively final for lambda
            for (String shingle : shingleArray) {
                int hash = hashWithSeed(shingle, s);
                if (hash < signature[s]) {
                    signature[s] = hash;
                }
            }
        }
        return signature;
    }

    /**
     * 带种子的简单哈希
     */
    private int hashWithSeed(String str, int seed) {
        int hash = seed;
        for (char c : str.toCharArray()) {
            hash = 31 * hash + c;
        }
        return hash & Integer.MAX_VALUE; // 保证非负
    }

    /**
     * 计算两个 MinHash 签名的 Jaccard 相似度
     */
    private double jaccardSimilarity(int[] sig1, int[] sig2) {
        if (sig1.length != sig2.length) return 0;
        int match = 0;
        for (int i = 0; i < sig1.length; i++) {
            if (sig1[i] == sig2[i]) match++;
        }
        return (double) match / sig1.length;
    }
}
```

- [ ] **Step 3: 在 DocumentProcessServiceImpl 中插入去重逻辑**

在 `processDocumentForKnowledge` 方法最开始，Tika 解析之前：

```java
// === RAG优化：文档去重 ===
String docContent = parseDocumentContent(filePath);
String contentMd5 = DigestUtils.md5DigestAsHex(docContent.getBytes(StandardCharsets.UTF_8));

// 1. MD5 快速判重
Long duplicateDocId = documentDedupService.checkMd5Duplicate(knowledgeId, contentMd5);
if (duplicateDocId != null) {
    document.setQualityStatus(2); // 标记疑似重复
    knowledgeDocumentMapper.updateById(document);
    log.warn("文档 [{}] 与 [{}] MD5完全重复，跳过索引", documentId, duplicateDocId);
    updateDocumentRelation(relation, 2);
    return;
}

// 2. MinHash 近似查重
DocumentDedupService.SimilarDocResult similar = 
    documentDedupService.checkMinHashSimilarity(knowledgeId, docContent, 0.85);
if (similar != null) {
    document.setQualityStatus(2);
    knowledgeDocumentMapper.updateById(document);
    log.warn("文档 [{}] 与 [{}] 近似重复 (相似度={:.2f})，跳过索引",
        documentId, similar.documentId(), similar.similarity());
    updateDocumentRelation(relation, 2);
    return;
}

// 3. 保存哈希用于后续去重
document.setContentMd5(contentMd5);
knowledgeDocumentMapper.updateById(document);
documentDedupService.saveDocumentHash(documentId, knowledgeId, contentMd5, docContent);
```

- [ ] **Step 4: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/DocumentDedupService.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentDedupServiceImpl.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentProcessServiceImpl.java
git commit -m "feat: add MD5 and MinHash document deduplication"
```

---

## Phase 3: 第二层 — 索引路由与分层索引

### Task 6: 摘要索引服务

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/SummaryIndexService.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/impl/SummaryIndexServiceImpl.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/ElasticsearchIndexService.java` (+ createSummaryIndex method)
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/ElasticsearchIndexServiceImpl.java`

- [ ] **Step 1: 创建 SummaryIndexService 接口**

```java
package cn.sdh.backend.service;

import java.util.List;
import java.util.Map;

/**
 * 摘要索引服务 — 管理文档摘要的 ES 索引和粗筛检索
 */
public interface SummaryIndexService {

    /** 摘要索引名称前缀 */
    String SUMMARY_INDEX_PREFIX = "sdh-rag-summary-";
    /** 冷库摘要索引名称前缀 */
    String COLD_SUMMARY_INDEX_PREFIX = "sdh-rag-summary-cold-";

    /**
     * 为文档创建摘要并写入摘要索引
     */
    void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                              String content, String embeddingModel);

    /**
     * 粗筛检索：从摘要索引中检索相关文档
     * @param query 查询文本
     * @param knowledgeId 知识库ID
     * @param topK 返回文档数
     * @param embeddingModel 嵌入模型
     * @param metadataFilters 元数据过滤条件（可为空）
     * @return 文档ID -> 相关度分数
     */
    Map<Long, Double> coarseRetrieval(String query, Long knowledgeId, int topK,
                                       String embeddingModel,
                                       Map<String, Object> metadataFilters);

    /**
     * 删除文档的摘要
     */
    void deleteDocumentSummary(Long documentId, Long knowledgeId);

    /**
     * 删除知识库的所有摘要索引
     */
    void deleteByKnowledgeId(Long knowledgeId);
}
```

- [ ] **Step 2: 创建 SummaryIndexServiceImpl 实现**

```java
package cn.sdh.backend.service.impl;

import cn.sdh.backend.service.ChatService;
import cn.sdh.backend.service.EmbeddingService;
import cn.sdh.backend.service.SummaryIndexService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryIndexServiceImpl implements SummaryIndexService {

    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final ChatService chatService;

    @Override
    @Async
    public void indexDocumentSummary(Long documentId, Long knowledgeId, String title,
                                      String content, String embeddingModel) {
        try {
            // 1. 生成摘要（取前 3000 字）
            String truncated = content.substring(0, Math.min(3000, content.length()));
            String summary = generateSummary(title, truncated);

            // 2. 向量化摘要
            float[] embedding = embeddingService.embed(summary, embeddingModel);

            // 3. 写入 ES
            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            Map<String, Object> doc = new HashMap<>();
            doc.put("doc_id", documentId);
            doc.put("summary_text", summary);
            doc.put("summary_embedding", embedding);
            doc.put("create_time", new Date());

            esClient.index(i -> i.index(indexName).id("doc_" + documentId).document(doc));
            log.info("摘要索引写入成功: docId={}, kbId={}", documentId, knowledgeId);

        } catch (Exception e) {
            log.error("摘要索引写入失败: docId={}, kbId={}", documentId, knowledgeId, e);
        }
    }

    @Override
    public Map<Long, Double> coarseRetrieval(String query, Long knowledgeId, int topK,
                                              String embeddingModel,
                                              Map<String, Object> metadataFilters) {
        try {
            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            float[] queryEmbedding = embeddingService.embed(query, embeddingModel);

            // 构建 ES kNN + filter 查询
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            // 元数据过滤
            if (metadataFilters != null && !metadataFilters.isEmpty()) {
                for (var entry : metadataFilters.entrySet()) {
                    boolBuilder.must(m -> m.term(
                        TermQuery.of(t -> t.field(entry.getKey()).value(entry.getValue().toString()))
                    ));
                }
            }

            KnnQuery knn = KnnQuery.of(k -> k
                .field("summary_embedding")
                .queryVector(Arrays.asList(toFloatList(queryEmbedding)))
                .k(topK)
                .numCandidates(topK * 10)
                .filter(boolBuilder.build()._toQuery())
            );

            SearchResponse<Map> response = esClient.search(s -> s
                .index(indexName)
                .knn(knn)
                .size(topK),
                Map.class
            );

            // 解析结果
            Map<Long, Double> results = new LinkedHashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source != null && source.get("doc_id") != null) {
                    Long docId = Long.valueOf(source.get("doc_id").toString());
                    Double score = hit.score() != null ? hit.score() : 0.0;
                    results.put(docId, score);
                }
            }
            return results;

        } catch (Exception e) {
            log.error("摘要粗筛检索失败: kbId={}", knowledgeId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void deleteDocumentSummary(Long documentId, Long knowledgeId) {
        try {
            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            esClient.delete(d -> d.index(indexName).id("doc_" + documentId));
        } catch (Exception e) {
            log.warn("删除摘要索引失败: docId={}, kbId={}", documentId, knowledgeId);
        }
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        try {
            String indexName = SUMMARY_INDEX_PREFIX + knowledgeId;
            esClient.indices().delete(d -> d.index(indexName));
        } catch (Exception e) {
            log.warn("删除摘要索引失败: kbId={}", knowledgeId);
        }
    }

    private String generateSummary(String title, String content) {
        try {
            String prompt = String.format("""
                请为以下文档生成一段150-200字的核心摘要，涵盖：
                1. 文档主题和目的
                2. 关键知识点/结论
                3. 适用场景/范围
                文档标题：%s
                文档内容：%s
                请直接输出摘要，不要添加额外说明。""", title, content);

            return chatService.chatSync(prompt);
        } catch (Exception e) {
            log.error("摘要生成失败: {}", title, e);
            // 降级：取文档前200字作为摘要
            return content.substring(0, Math.min(200, content.length())) + "...";
        }
    }

    private Float[] toFloatList(float[] arr) {
        Float[] result = new Float[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i];
        return result;
    }
}
```

- [ ] **Step 3: 扩展 ElasticsearchIndexService**

在 `ElasticsearchIndexService.java` 接口中新增方法声明：

```java
/**
 * 为知识库创建摘要索引
 */
void createSummaryIndexForKnowledgeBase(Long knowledgeId, String embeddingModelName);

/**
 * 为知识库创建冷归档摘要索引
 */
void createColdSummaryIndexForKnowledgeBase(Long knowledgeId, String embeddingModelName);
```

在 `ElasticsearchIndexServiceImpl.java` 中实现（参考现有 `createIndexForKnowledgeBase` 方法的 ES mapping 结构）：

```java
@Override
public void createSummaryIndexForKnowledgeBase(Long knowledgeId, String embeddingModelName) {
    String indexName = SummaryIndexService.SUMMARY_INDEX_PREFIX + knowledgeId;
    int dims = EmbeddingDimensionMapping.getDimension(embeddingModelName);
    // 与现有 createIndexForKnowledgeBase 类似的 ES 索引创建逻辑
    // mapping 包含: doc_id(keyword), summary_text(text+ik), 
    //   summary_embedding(dense_vector,dims), department(keyword),
    //   doc_type(keyword), doc_date(date), freshness_score(float)
}

@Override
public void createColdSummaryIndexForKnowledgeBase(Long knowledgeId, String embeddingModelName) {
    String indexName = SummaryIndexService.COLD_SUMMARY_INDEX_PREFIX + knowledgeId;
    // 与摘要索引相同的 mapping，索引名不同
}
```

- [ ] **Step 4: 在 DocumentProcessServiceImpl 中接入摘要生成**

在文档处理完成、分块入库后，异步生成摘要：

```java
// 在 processDocumentForKnowledge 方法的末尾，分块入库完成之后：
// === RAG优化：异步生成摘要并写入摘要索引 ===
if (Boolean.TRUE.equals(knowledgeBase.getEnableSummaryIndex())) {
    summaryIndexService.indexDocumentSummary(
        documentId, knowledgeId, 
        document.getTitle(), 
        docContent,
        relation.getEmbeddingModel()
    );
}
```

- [ ] **Step 5: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/SummaryIndexService.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/SummaryIndexServiceImpl.java \
        rag-backend/src/main/java/cn/sdh/backend/service/ElasticsearchIndexService.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/ElasticsearchIndexServiceImpl.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/DocumentProcessServiceImpl.java
git commit -m "feat: add summary index service for coarse retrieval"
```

---

### Task 7: VectorStoreService 扩展 — 元数据过滤 + 按文档ID检索 + 冷热索引

**Files:**
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/VectorStoreService.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/VectorStoreServiceImpl.java`

- [ ] **Step 1: 扩展 VectorStoreService 接口**

```java
// 新增方法声明：

/**
 * 按文档ID列表检索 chunks（用于二级精排）
 * @param queries 查询文本列表
 * @param documentIds 候选文档ID集合
 * @param knowledgeId 知识库ID
 * @param topKPerDoc 每个文档最多返回的chunk数
 * @param embeddingModel 嵌入模型
 * @return 候选chunk列表
 */
List<Document> searchByDocumentIds(List<String> queries, Set<Long> documentIds,
    Long knowledgeId, int topKPerDoc, String embeddingModel);

/**
 * 混合检索（带元数据过滤）
 * @param metadataFilters 元数据过滤条件
 */
List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK,
    int keywordTopK, double vectorWeight, double keywordWeight,
    String embeddingModelName, Double minScore,
    Map<String, Object> metadataFilters);

/**
 * 冷库检索（仅在热库不足时调用）
 */
List<Document> coldSearch(String query, Long knowledgeId, int topK,
    String embeddingModelName, Map<String, Object> metadataFilters);
```

- [ ] **Step 2: 在 VectorStoreServiceImpl 中实现 searchByDocumentIds**

```java
@Override
public List<Document> searchByDocumentIds(List<String> queries, Set<Long> documentIds,
        Long knowledgeId, int topKPerDoc, String embeddingModel) {
    String indexName = getIndexName(knowledgeId);
    
    // 构建 document_id IN (...) 的 terms filter
    List<String> docIdStrings = documentIds.stream()
        .map(String::valueOf).collect(Collectors.toList());

    // 对每个查询执行 kNN + terms filter
    List<Document> allResults = new ArrayList<>();
    for (String query : queries) {
        float[] embedding = embeddingService.embed(query, embeddingModel);
        
        TermQuery docIdFilter = TermQuery.of(t -> t
            .field("document_id")
            .value(docIdStrings)  // multi-value
        );
        
        // ES kNN with filter
        // ... (使用 ES Java Client 的术语构建 kNN 查询)
        // 结果限制每文档 topKPerDoc 个
    }
    return allResults;
}
```

- [ ] **Step 3: 在 VectorStoreServiceImpl 中实现 metadataFilter 版本的 hybridSearch**

```java
@Override
public List<Document> hybridSearch(String query, Long knowledgeId, int vectorTopK,
        int keywordTopK, double vectorWeight, double keywordWeight,
        String embeddingModelName, Double minScore,
        Map<String, Object> metadataFilters) {
    
    // 1. 向量搜索（带过滤）
    List<Document> vectorResults = similaritySearchWithFilter(
        query, knowledgeId, vectorTopK, embeddingModelName, minScore, metadataFilters);
    
    // 2. 关键词搜索（带过滤）
    List<Document> keywordResults = keywordSearchWithFilter(
        query, knowledgeId, keywordTopK, metadataFilters);
    
    // 3. RRF 融合（复用现有逻辑）
    return rrfMerge(vectorResults, keywordResults, vectorWeight, keywordWeight);
}

private List<Document> similaritySearchWithFilter(String query, Long knowledgeId,
        int topK, String embeddingModel, Double minScore,
        Map<String, Object> metadataFilters) {
    // 在现有 similaritySearch 的 ES 查询中增加 metadataFilter 的 term 子句
    // ...
}

private List<Document> keywordSearchWithFilter(String query, Long knowledgeId,
        int topK, Map<String, Object> metadataFilters) {
    // 在现有 keywordSearch 的 ES bool query 中增加 metadataFilter 的 term 子句
    // ...
}
```

- [ ] **Step 4: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/VectorStoreService.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/VectorStoreServiceImpl.java
git commit -m "feat: add metadata filter and document-ID-based search to VectorStoreService"
```

---

## Phase 4: 第三层 — 级联检索（三级漏斗）

### Task 8: MMR 多样性重排服务

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/DiversityRankingService.java`

- [ ] **Step 1: 创建 DiversityRankingService（MMR 算法）**

```java
package cn.sdh.backend.service;

import cn.sdh.backend.dto.SearchResult.RankedChunk;
import cn.sdh.backend.service.impl.EmbeddingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * MMR (Maximum Marginal Relevance) 多样性重排服务
 * 贪心选取每次最大化边际相关性的结果，去除语义重复的片段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiversityRankingService {

    private final EmbeddingServiceImpl embeddingService;

    /**
     * MMR 算法贪心选取
     * @param candidates 精排后的候选 chunk 列表（按相关性降序）
     * @param topK       最终返回数量
     * @param lambda     相关性权重（0-1），越大越偏向相关性
     * @return 选取的最终 chunk 列表
     */
    public List<RankedChunk> mmrSelect(List<RankedChunk> candidates, int topK, double lambda) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (candidates.size() <= topK) return new ArrayList<>(candidates);

        List<RankedChunk> selected = new ArrayList<>();
        List<RankedChunk> remaining = new ArrayList<>(candidates);

        // 缓存所有候选 chunk 的 embedding 以减少重复计算
        List<float[]> embeddings = new ArrayList<>();
        for (RankedChunk c : remaining) {
            embeddings.add(embeddingService.embed(c.getContent(), null));
        }

        // 第一步：选相关性最高的
        selected.add(remaining.remove(0));
        float[] firstEmbedding = embeddings.remove(0);

        // 贪心迭代：每次选 MMR 最大的
        while (selected.size() < topK && !remaining.isEmpty()) {
            double bestMmr = Double.NEGATIVE_INFINITY;
            int bestIdx = -1;

            for (int i = 0; i < remaining.size(); i++) {
                RankedChunk candidate = remaining.get(i);
                double relevance = candidate.getRelevanceScore() != null 
                    ? candidate.getRelevanceScore() : 0.5;

                // 计算与已选结果的最大语义相似度
                double maxSimilarity = 0;
                float[] candEmbedding = embeddings.get(i);
                for (float[] selEmbedding : getAllSelectedEmbeddings(selected, selected.size())) {
                    int idx = selected.size() - 1;
                    // 使用已缓存的 embedding
                    float[] selEmb = selected.size() == 1 ? firstEmbedding : 
                        embeddingService.embed(selected.get(idx).getContent(), null);
                    double sim = embeddingService.cosineSimilarity(
                        toDoubleArray(candEmbedding), toDoubleArray(selEmb));
                    maxSimilarity = Math.max(maxSimilarity, sim);
                }

                double mmr = lambda * relevance - (1 - lambda) * maxSimilarity;
                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                selected.add(remaining.remove(bestIdx));
                embeddings.remove(bestIdx);
            } else {
                break; // 安全兜底
            }
        }

        log.debug("MMR 从 {} 个候选中选取 {} 个结果, lambda={}",
            candidates.size(), selected.size(), lambda);
        return selected;
    }

    private double[] toDoubleArray(float[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i];
        return result;
    }

    private List<float[]> getAllSelectedEmbeddings(List<RankedChunk> selected, int size) {
        // 简化：仅对前 size 个做嵌入计算
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < Math.min(size, selected.size()); i++) {
            result.add(embeddingService.embed(selected.get(i).getContent(), null));
        }
        return result;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/DiversityRankingService.java
git commit -m "feat: add MMR diversity reranking service"
```

---

### Task 9: 后处理服务（硬阈值截断 + 时间衰减）

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/PostProcessingService.java`

- [ ] **Step 1: 创建 PostProcessingService**

```java
package cn.sdh.backend.service;

import cn.sdh.backend.config.FreshnessConfig;
import cn.sdh.backend.dto.SearchResult;
import cn.sdh.backend.dto.SearchResult.RankedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索后处理服务：硬阈值截断 + 时间衰减重排
 */
@Slf4j
@Service
public class PostProcessingService {

    /**
     * 后处理管线
     * @param chunks      精排后的候选 chunk
     * @param threshold   相似度硬阈值
     * @param recencyAlpha 时间衰减系数 (0-1)
     * @param finalTopK   最终返回数量
     * @return 通过筛选的最终结果
     */
    public List<RankedChunk> process(List<RankedChunk> chunks, double threshold,
                                      double recencyAlpha, int finalTopK) {

        // Step 1: 硬阈值截断
        List<RankedChunk> passed = chunks.stream()
            .filter(c -> c.getRelevanceScore() != null && c.getRelevanceScore() >= threshold)
            .collect(Collectors.toList());

        int filteredCount = chunks.size() - passed.size();
        if (filteredCount > 0) {
            log.info("硬阈值截断: {} 个结果低于阈值 {} 被过滤", filteredCount, threshold);
        }

        // Step 2: 全部截断 → 返回空列表，由上层生成降级消息
        if (passed.isEmpty()) {
            log.warn("所有检索结果均低于相似度阈值 {}，返回空", threshold);
            return List.of();
        }

        // Step 3: 时间衰减加权
        for (RankedChunk chunk : passed) {
            double relevance = chunk.getRelevanceScore() != null ? chunk.getRelevanceScore() : 0.5;
            double freshness = chunk.getFreshnessScore() != null 
                ? chunk.getFreshnessScore() 
                : FreshnessConfig.MIN_FRESHNESS;
            // finalScore = relevance × (1 - recencyAlpha + recencyAlpha × freshness)
            double finalScore = relevance * ((1 - recencyAlpha) + recencyAlpha * freshness);
            chunk.setFinalScore(finalScore);
        }

        // Step 4: 按最终分重排
        passed.sort(Comparator.comparingDouble(RankedChunk::getFinalScore).reversed());

        // Step 5: 截断到 finalTopK
        return passed.subList(0, Math.min(finalTopK, passed.size()));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/PostProcessingService.java
git commit -m "feat: add post-processing service with threshold cutoff and recency decay"
```

---

### Task 10: RagSearchServiceImpl — 三级漏斗重构

**Files:**
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/RagSearchService.java`
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/RagSearchServiceImpl.java`

**注意：** 由于 RagSearchService.java 可能被 DLP 加密，本任务需在实际开发时根据 IDE 中显示的源码直接修改。以下代码保持与现有代码一致的模式。

- [ ] **Step 1: 在 RagSearchService 接口新增方法**

```java
/**
 * 级联检索（三级漏斗）
 * @param query 原始查询
 * @param knowledgeBase 知识库配置
 * @param chatHistory 对话历史
 * @param metadataFilters 元数据过滤条件
 * @return 层级搜索结果
 */
SearchResult searchWithCascade(String query, KnowledgeBase knowledgeBase,
    List<ChatMessage> chatHistory, Map<String, Object> metadataFilters);
```

- [ ] **Step 2: 重构 RagSearchServiceImpl.search() 为三级漏斗**

核心思路：将现有 `search()` 方法的逻辑（查询改写→HyDE→Hybrid→RRF→Rerank）重新编排为：

```java
/**
 * 级联检索流程:
 * 1. 智能路由（复用现有 IntentRouter）
 * 2. 查询改写 + HyDE（复用现有逻辑）
 * 3. 粗筛：摘要索引 ANN → Top-N 文档 ID
 * 4. 精排：对 Top-N 文档的 chunks 做 hybrid search + Reranker → Top-K
 * 5. 细排：MMR 去冗余 → Top-M
 * 6. 后处理：阈值截断 + 时间衰减
 */
public SearchResult searchWithCascade(String query, KnowledgeBase kb,
        List<ChatMessage> chatHistory, Map<String, Object> metadataFilters) {

    // === 阶段 1: 查询改写 + 扩展（复用现有逻辑）===
    List<String> queries = buildQueries(query, kb, chatHistory);

    // === 阶段 2: 粗筛（摘要索引）===
    Map<Long, Double> candidateDocs;
    if (Boolean.TRUE.equals(kb.getEnableSummaryIndex())) {
        candidateDocs = summaryIndexService.coarseRetrieval(
            query, kb.getId(),
            kb.getCoarseTopK() != null ? kb.getCoarseTopK() : 50,
            kb.getEmbeddingModel(),
            metadataFilters
        );
    } else {
        // 降级：直接查 ES chunk 索引获取 Top-N 文档
        candidateDocs = fallbackCoarseRetrieval(query, kb, 50, metadataFilters);
    }

    if (candidateDocs.isEmpty()) {
        return SearchResult.empty("摘要索引粗筛无结果");
    }

    // === 阶段 3: 精排（对候选文档的 chunks 做 hybrid + rerank）===
    List<Document> candidateChunks = vectorStoreService.searchByDocumentIds(
        queries, candidateDocs.keySet(), kb.getId(), 
        3, // 每文档 Top-3 chunk
        kb.getEmbeddingModel()
    );

    // Reranker 精排
    List<RankedChunk> reranked = rerankAndConvert(
        query, candidateChunks, kb, 
        kb.getFineTopK() != null ? kb.getFineTopK() : 20
    );

    if (reranked.isEmpty()) {
        return SearchResult.empty("精排后无合格结果");
    }

    // === 阶段 4: MMR 多样性细排 ===
    double mmrLambda = kb.getMmrLambda() != null ? kb.getMmrLambda() : 0.7;
    List<RankedChunk> diversified = diversityRankingService.mmrSelect(
        reranked, 5, mmrLambda  // 最终 Top-5
    );

    // === 阶段 5: 后处理（阈值截断 + 时间衰减）===
    double threshold = kb.getSimilarityThreshold() != null 
        ? kb.getSimilarityThreshold() : 0.6;
    double recencyAlpha = kb.getRecencyAlpha() != null 
        ? kb.getRecencyAlpha() : 0.3;
    int finalTopK = 5;

    List<RankedChunk> finalChunks = postProcessingService.process(
        diversified, threshold, recencyAlpha, finalTopK
    );

    // === 阶段 6: 冷热分离降级（热库不足时触发）===
    if (finalChunks.size() < (kb.getHotMinResults() != null ? kb.getHotMinResults() : 3)
            && Boolean.TRUE.equals(kb.getEnableColdArchive())) {
        log.info("热库结果不足 ({}), 触发冷库检索", finalChunks.size());
        List<RankedChunk> coldChunks = coldArchiveRetrieval(
            query, kb, metadataFilters, finalTopK - finalChunks.size());
        // 冷库结果降权 0.9
        coldChunks.forEach(c -> c.setFinalScore(c.getFinalScore() * 0.9));
        finalChunks = mergeAndSort(finalChunks, coldChunks, finalTopK);
    }

    if (finalChunks.isEmpty()) {
        return SearchResult.empty("所有结果低于相似度阈值");
    }

    // === 阶段 7: 附加父文档上下文 ===
    enrichWithDocumentContext(finalChunks);

    return SearchResult.of(finalChunks, query);
}
```

- [ ] **Step 3: 添加辅助方法**

```java
/**
 * 将 Reranker 结果转换为 RankedChunk
 */
private List<RankedChunk> rerankAndConvert(String query, List<Document> chunks,
        KnowledgeBase kb, int topK) {
    // 复用现有 RerankService
    List<Document> reranked = rerankService.rerank(query, chunks, topK);
    
    return reranked.stream().map(doc -> {
        Map<String, Object> meta = doc.getMetadata();
        return RankedChunk.builder()
            .documentId(parseLong(meta.get("document_id")))
            .documentTitle((String) meta.getOrDefault("document_title", ""))
            .content(doc.getContent())
            .chunkIndex(parseInt(meta.get("chunk_index")))
            .totalChunks(parseInt(meta.get("total_chunks")))
            .relevanceScore((Double) meta.getOrDefault("_score", 0.5))
            .freshnessScore((Double) meta.getOrDefault("freshness_score", 0.5))
            .department((String) meta.get("department"))
            .docType((String) meta.get("doc_type"))
            .build();
    }).collect(Collectors.toList());
}

/**
 * 为结果附加父文档摘要上下文
 */
private void enrichWithDocumentContext(List<RankedChunk> chunks) {
    Set<Long> docIds = chunks.stream()
        .map(RankedChunk::getDocumentId)
        .collect(Collectors.toSet());
    
    for (RankedChunk chunk : chunks) {
        // 从摘要索引获取文档摘要（或从 KnowledgeDocument 查）
        // ...
    }
}

/**
 * 冷归档检索
 */
private List<RankedChunk> coldArchiveRetrieval(String query, KnowledgeBase kb,
        Map<String, Object> metadataFilters, int topK) {
    // 使用冷索引检索
    List<Document> coldResults = vectorStoreService.coldSearch(
        query, kb.getId(), topK, kb.getEmbeddingModel(), metadataFilters);
    return rerankAndConvert(query, coldResults, kb, topK);
}
```

- [ ] **Step 4: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/RagSearchService.java \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/RagSearchServiceImpl.java
git commit -m "feat: refactor RAG search to 3-stage cascade retrieval pipeline

- Stage 1: Summary index coarse retrieval (Top-N document IDs)
- Stage 2: Hybrid search + Reranker fine ranking (Top-K chunks)
- Stage 3: MMR diversity reranking (Top-M final chunks)
- Post-processing: threshold cutoff + recency decay
- Cold archive fallback when hot results insufficient"
```

---

## Phase 5: 第四层 — LLM 上下文增强

### Task 11: ChatService 层级上下文 Prompt 优化

**Files:**
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: 改进上下文构建方法**

在 ChatServiceImpl 中修改送给 LLM 的 prompt 构建逻辑：

```java
/**
 * 构建层级文档上下文（替代原有的简单拼接）
 */
private String buildHierarchicalContext(List<SearchResult.RankedChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) return "";

    // 按文档分组
    Map<Long, List<SearchResult.RankedChunk>> groupedByDoc = chunks.stream()
        .collect(Collectors.groupingBy(
            SearchResult.RankedChunk::getDocumentId,
            LinkedHashMap::new,
            Collectors.toList()
        ));

    StringBuilder context = new StringBuilder();
    context.append("【检索上下文】\n\n");

    for (var entry : groupedByDoc.entrySet()) {
        SearchResult.RankedChunk first = entry.getValue().get(0);
        String docDate = first.getDocDate() != null 
            ? first.getDocDate().toString() : "未知日期";
        String department = first.getDepartment() != null 
            ? first.getDepartment() : "未知部门";
        String docType = first.getDocType() != null 
            ? first.getDocType() : "未知类型";

        context.append(String.format(
            "📄 文档：《%s》（%s · %s · %s）\n",
            first.getDocumentTitle(), department, docType, docDate));

        if (first.getDocumentSummary() != null && !first.getDocumentSummary().isEmpty()) {
            context.append(String.format("   摘要：%s\n", first.getDocumentSummary()));
        }

        for (SearchResult.RankedChunk chunk : entry.getValue()) {
            double score = chunk.getFinalScore() != null 
                ? chunk.getFinalScore() 
                : (chunk.getRelevanceScore() != null ? chunk.getRelevanceScore() : 0);
            context.append(String.format(
                "   片段 [%d/%d]（相关度: %.2f）：\n   %s\n",
                chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
                chunk.getTotalChunks() != null ? chunk.getTotalChunks() : 0,
                score,
                chunk.getContent().trim()
            ));
        }
        context.append("\n---\n\n");
    }

    context.append("""
        【回答要求】
        1. 优先使用高相关度（score ≥ 0.8）的片段
        2. 如果多个片段信息矛盾，以最新文档（docDate 最近）为准
        3. 如果检索到的信息不足以回答问题，请明确告知用户，不要编造不存在的信息
        4. 引用信息时标注来源：如"根据《文档标题》..."
        """);

    return context.toString();
}
```

- [ ] **Step 2: 在 RAG 查询流程中使用新的上下文构建方法**

在现有点用 `sources` 构建 prompt 的地方，改为使用 `buildHierarchicalContext(chunks)`。

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/impl/ChatServiceImpl.java
git commit -m "feat: add hierarchical document context to LLM prompt

- Group chunks by parent document
- Include document metadata (title, department, type, date, summary)
- Add chunk position info (index/total)
- Add answer quality instructions to prevent hallucination"
```

---

## Phase 6: 前端改造

### Task 12: 前端类型定义更新

**Files:**
- Create: `rag-frontend/src/types/search.ts`

- [ ] **Step 1: 创建前端搜索类型定义**

```typescript
// rag-frontend/src/types/search.ts

export interface MetadataFilter {
  department?: string
  docType?: string
  dateFrom?: string  // ISO date string
  dateTo?: string
}

export interface RankedChunk {
  documentId: number
  documentTitle: string
  documentSummary?: string
  content: string
  chunkIndex: number
  totalChunks: number
  relevanceScore: number
  freshnessScore: number
  finalScore: number
  department?: string
  docType?: string
  docDate?: string
}

export interface SearchResult {
  success: boolean
  message?: string
  chunks: RankedChunk[]
  originalQuery: string
  fallbackTriggered: boolean
  fallbackReason?: string
}

export interface KnowledgeBaseConfigExtended {
  enableSummaryIndex?: boolean
  summaryCoarseTopK?: number
  enableColdArchive?: boolean
  hotMinResults?: number
  mmrLambda?: number
  recencyAlpha?: number
  coarseTopK?: number
  fineTopK?: number
}

export const DOC_TYPES = [
  { value: 'policy', label: '制度规范' },
  { value: 'manual', label: '操作手册' },
  { value: 'contract', label: '合同协议' },
  { value: 'report', label: '分析报告' },
  { value: 'faq', label: 'FAQ问答' },
] as const
```

- [ ] **Step 2: Commit**

```bash
git add rag-frontend/src/types/search.ts
git commit -m "feat: add frontend TypeScript types for RAG optimization"
```

---

### Task 13: 知识库配置面板扩展

**Files:**
- Modify: `rag-frontend/src/views/knowledge-base/components/ConfigPanel.vue`
- Modify: `rag-frontend/src/api/knowledgeBase.ts`

- [ ] **Step 1: 在 ConfigPanel.vue 中新增展开区域**

在"检索配置"区域末尾（HyDE 配置之后），新增"高级检索优化"折叠面板：

```vue
<!-- 在 ConfigPanel.vue 的 template 中，HyDE 配置之后添加 -->
<a-divider />
<a-collapse ghost>
  <a-collapse-panel key="advanced" header="🔧 高级检索优化">
    <a-space direction="vertical" style="width: 100%">
      <!-- 摘要索引 -->
      <a-form-item label="摘要索引">
        <a-switch v-model:checked="formState.enableSummaryIndex" />
        <span class="hint">入库时自动生成文档摘要，用于快速粗筛</span>
      </a-form-item>

      <a-form-item v-if="formState.enableSummaryIndex" label="粗筛文档数">
        <a-slider v-model:value="formState.summaryCoarseTopK"
          :min="10" :max="200" :step="10" style="width: 200px" />
        <span class="hint">{{ formState.summaryCoarseTopK }} 个文档</span>
      </a-form-item>

      <!-- 冷热分离 -->
      <a-form-item label="冷热分离">
        <a-switch v-model:checked="formState.enableColdArchive" />
        <span class="hint">6个月前的文档自动归档冷库，热库不足时触发检索</span>
      </a-form-item>

      <a-form-item v-if="formState.enableColdArchive" label="热库最低结果数">
        <a-input-number v-model:value="formState.hotMinResults"
          :min="1" :max="10" />
        <span class="hint">热库结果少于此数自动触发冷库检索</span>
      </a-form-item>

      <!-- MMR 配置 -->
      <a-form-item label="MMR 多样性">
        <a-slider v-model:value="formState.mmrLambda"
          :min="0.1" :max="1.0" :step="0.05" style="width: 200px" />
        <span class="hint">λ={{ formState.mmrLambda }}（越大越偏向相关性）</span>
      </a-form-item>

      <!-- 时间衰减 -->
      <a-form-item label="时间衰减系数">
        <a-slider v-model:value="formState.recencyAlpha"
          :min="0.0" :max="0.5" :step="0.05" style="width: 200px" />
        <span class="hint">α={{ formState.recencyAlpha }}（越大新文档权重越高）</span>
      </a-form-item>
    </a-space>
  </a-collapse-panel>
</a-collapse>
```

- [ ] **Step 2: 更新 knowledgeBase.ts API 类型**

在 `KnowledgeBaseConfigRequest` 接口中新增字段：

```typescript
export interface KnowledgeBaseConfigRequest {
  // ... 现有字段
  enableSummaryIndex?: boolean
  summaryCoarseTopK?: number
  enableColdArchive?: boolean
  hotMinResults?: number
  mmrLambda?: number
  recencyAlpha?: number
  coarseTopK?: number
  fineTopK?: number
}
```

- [ ] **Step 3: 更新 formState 默认值**

在 ConfigPanel.vue 的 `formState` reactive 中添加新字段的默认值：

```typescript
const formState = reactive({
  // ... 现有字段
  enableSummaryIndex: true,
  summaryCoarseTopK: 50,
  enableColdArchive: true,
  hotMinResults: 3,
  mmrLambda: 0.7,
  recencyAlpha: 0.3,
  coarseTopK: 50,
  fineTopK: 20,
})
```

- [ ] **Step 4: Commit**

```bash
git add rag-frontend/src/views/knowledge-base/components/ConfigPanel.vue \
        rag-frontend/src/api/knowledgeBase.ts
git commit -m "feat: add advanced RAG optimization settings to ConfigPanel

- Summary index toggle and coarse top-K slider
- Cold/hot archive separation toggle
- MMR lambda slider
- Recency decay alpha slider"
```

---

### Task 14: 聊天输入框 — 高级筛选面板

**Files:**
- Modify: `rag-frontend/src/views/chat/components/ChatInput.vue`

- [ ] **Step 1: 在 ChatInput.vue 中添加高级筛选折叠面板**

在知识库选择器下方，新增可折叠的高级筛选区域：

```vue
<!-- 高级筛选按钮 -->
<div class="filter-toggle">
  <a-button type="link" size="small" @click="showFilter = !showFilter">
    <FilterOutlined />
    {{ showFilter ? '收起筛选' : '高级筛选' }}
  </a-button>
</div>

<!-- 筛选面板 -->
<div v-if="showFilter" class="filter-panel">
  <a-space wrap>
    <a-select
      v-model:value="metadataFilter.department"
      placeholder="部门"
      allow-clear
      size="small"
      style="width: 120px"
    >
      <a-select-option v-for="d in departments" :key="d" :value="d">
        {{ d }}
      </a-select-option>
    </a-select>

    <a-select
      v-model:value="metadataFilter.docType"
      placeholder="文档类型"
      allow-clear
      size="small"
      style="width: 120px"
    >
      <a-select-option
        v-for="t in DOC_TYPES" :key="t.value" :value="t.value"
      >
        {{ t.label }}
      </a-select-option>
    </a-select>

    <a-date-picker
      v-model:value="metadataFilter.dateFrom"
      placeholder="开始日期"
      size="small"
      style="width: 130px"
    />

    <a-date-picker
      v-model:value="metadataFilter.dateTo"
      placeholder="结束日期"
      size="small"
      style="width: 130px"
    />
  </a-space>
</div>
```

- [ ] **Step 2: 添加对应的 script 状态和 emit**

```typescript
import { FilterOutlined } from '@ant-design/icons-vue'
import { DOC_TYPES, type MetadataFilter } from '@/types/search'

const showFilter = ref(false)
const metadataFilter = reactive<MetadataFilter>({})
const departments = ['技术部', '财务部', '人力资源部', '市场部', '运营部']

// 发送时将 metadataFilter 传递给父组件
const emit = defineEmits<{
  send: [params: { 
    question: string; 
    knowledgeId: number | null; 
    modelId: number | null;
    memoryEnabled: boolean;
    metadataFilter?: MetadataFilter;
  }]
}>()
```

- [ ] **Step 3: Commit**

```bash
git add rag-frontend/src/views/chat/components/ChatInput.vue
git commit -m "feat: add advanced metadata filter panel to chat input

- Department, document type, date range filters
- Collapsible panel design
- Filter parameters passed with search request"
```

---

### Task 15: 文档上传 — 元数据采集

**Files:**
- Modify: `rag-frontend/src/views/document/components/DocumentUpload.vue`

- [ ] **Step 1: 在上传表单中添加元数据字段**

在文件选择器下方添加：

```vue
<a-form layout="vertical" style="margin-top: 16px">
  <a-row :gutter="16">
    <a-col :span="8">
      <a-form-item label="文档类型">
        <a-select v-model:value="uploadForm.docType"
          placeholder="选择文档类型" allow-clear>
          <a-select-option v-for="t in DOC_TYPES" :key="t.value" :value="t.value">
            {{ t.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
    </a-col>
    <a-col :span="8">
      <a-form-item label="所属部门">
        <a-input v-model:value="uploadForm.department"
          placeholder="如：技术部" />
      </a-form-item>
    </a-col>
    <a-col :span="8">
      <a-form-item label="文档日期">
        <a-date-picker v-model:value="uploadForm.docDate"
          style="width: 100%" placeholder="选择日期" />
      </a-form-item>
    </a-col>
  </a-row>
</a-form>
```

- [ ] **Step 2: 添加对应的响应式数据**

```typescript
import { DOC_TYPES } from '@/types/search'

const uploadForm = reactive({
  docType: undefined as string | undefined,
  department: '',
  docDate: undefined as string | undefined,
})

// 上传时将这些字段一起发送到后端
```

- [ ] **Step 3: Commit**

```bash
git add rag-frontend/src/views/document/components/DocumentUpload.vue
git commit -m "feat: add metadata fields to document upload form

- Document type selector (policy/manual/contract/report/faq)
- Department input
- Document date picker"
```

---

## Phase 7: 后端 Controller 适配

### Task 16: 后端 API 适配

**Files:**
- Modify: `rag-backend/.../controller/KnowledgeBaseController.java`
- Modify: `rag-backend/.../service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `rag-backend/.../dto/AskRequest.java` (如可访问)

- [ ] **Step 1: KnowledgeBaseController 保存新字段**

确保 `saveConfig` / `updateConfig` 接口正确映射新增的 8 个配置字段到 KnowledgeBase 实体。

- [ ] **Step 2: AskRequest 添加 metadataFilter**

```java
// 在 AskRequest DTO 中新增字段
private String department;
private String docType;
private String dateFrom;
private String dateTo;
```

- [ ] **Step 3: ChatController 传递 metadataFilter 到搜索层**

在调用 `ragSearchService.searchWithCascade()` 时，将 `AskRequest` 中的过滤字段转换为 `MetadataFilter` 对象传入。

- [ ] **Step 4: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/controller/ \
        rag-backend/src/main/java/cn/sdh/backend/service/impl/KnowledgeBaseServiceImpl.java
git commit -m "feat: expose new RAG config fields and metadata filters via API"
```

---

## Phase 8: 配置与集成测试

### Task 17: application.yml 默认配置

**Files:**
- Modify: `rag-backend/src/main/resources/application.yml`

- [ ] **Step 1: 新增检索默认配置节**

```yaml
knowledge:
  default:
    chunk-size: 500
    chunk-overlap: 50
    embeddingModel: text-embedding-v2
    rankModel: qwen3-rerank
  retrieval:
    hot-min-results: 3
    cold-score-penalty: 0.9
    hard-threshold: 0.6
    mmr-lambda: 0.7
    recency-alpha: 0.3
    coarse-top-k: 50
    fine-top-k: 20
    final-top-k: 5
    enable-cold-archive: true
    enable-summary-index: true
    fallback-strategy: keyword
```

- [ ] **Step 2: Commit**

```bash
git add rag-backend/src/main/resources/application.yml
git commit -m "feat: add default RAG retrieval configuration"
```

---

### Task 18: 集成测试与端到端验证

- [ ] **Step 1: 编写 MMR 单元测试**

```java
@Test
void testMmrSelect_ShouldReturnDiverseResults() {
    // Given: 5 个语义相似的 chunk
    List<RankedChunk> candidates = List.of(
        chunk("绩效管理制度 2025", 0.95, "技术部"),
        chunk("绩效管理制度 2024", 0.90, "技术部"),  // 内容相似
        chunk("绩效管理 FAQ", 0.85, "HR"),
        chunk("年终奖发放办法", 0.80, "财务部"),      // 内容不同
        chunk("员工晋升通道", 0.75, "HR")            // 内容不同
    );
    
    // When: MMR 选 Top-3, λ=0.7
    List<RankedChunk> result = diversityRankingService.mmrSelect(candidates, 3, 0.7);
    
    // Then: 应该选择 3 个覆盖不同主题的 chunk
    assertEquals(3, result.size());
    // 验证选择的 chunk 涵盖不同部门/主题
}
```

- [ ] **Step 2: 编写去重测试**

```java
@Test
void testMd5Dedup_ShouldDetectDuplicate() {
    String content = "这是一份测试文档内容";
    String md5 = DigestUtils.md5DigestAsHex(content.getBytes());
    
    // 先保存一份
    documentDedupService.saveDocumentHash(1L, 1L, md5, content);
    
    // 再尝试上传相同内容
    Long duplicateId = documentDedupService.checkMd5Duplicate(1L, md5);
    assertNotNull(duplicateId);
    assertEquals(1L, duplicateId);
}
```

- [ ] **Step 3: 端到端验证清单**

启动完整服务栈后验证：
1. ✅ 上传文档 → 去重检查生效 → 质量过滤生效 → 摘要生成
2. ✅ 知识库配置页 → 新配置项可见 → 保存生效
3. ✅ 聊天输入 → 高级筛选面板 → 筛选条件传到后端
4. ✅ 问答检索 → 摘要索引粗筛 → MMR 多样性 → 结果不重复
5. ✅ 低阈值查询 → "知识库未覆盖"提示返回
6. ✅ 冷热分离 → 热库不足时自动触发冷库检索

- [ ] **Step 4: Commit**

```bash
git add rag-backend/src/test/
git commit -m "test: add unit tests for MMR and deduplication"
```

---

## 实施顺序与依赖

```
Phase 1: 数据库迁移 (Task 1)
    ↓
Phase 1: 实体+Dapper (Task 2) + DTO (Task 3)
    ↓
Phase 2: 新鲜度+质量过滤 (Task 4) ←→ 去重 (Task 5)
    ↓
Phase 3: 摘要索引 (Task 6) ←→ VectorStore扩展 (Task 7)
    ↓
Phase 4: MMR (Task 8) → 后处理 (Task 9) → RagSearch重构 (Task 10)
    ↓
Phase 5: ChatService Prompt (Task 11)
    ↓
Phase 6: 前端类型 (Task 12) → ConfigPanel (Task 13) ←→ ChatInput (Task 14) ←→ Upload (Task 15)
    ↓
Phase 7: Controller 适配 (Task 16)
    ↓
Phase 8: 配置 (Task 17) + 测试 (Task 18)
```

**可并行的工作：**
- Phase 6（前端）与 Phase 2-4（后端服务）可并行开发
- Task 4（质量过滤）和 Task 5（去重）可并行
- Task 8（MMR）和 Task 9（后处理）可并行

---

## 回滚方案

所有新增字段均有默认值，老数据不受影响。若出现问题：
1. 将 `enableSummaryIndex`, `enableColdArchive` 设为 false 即可降级为原有检索逻辑
2. 新增的 ES 索引可独立删除，不影响现有 chunk 索引
3. 前端新增组件可独立回滚，不影响原有功能
