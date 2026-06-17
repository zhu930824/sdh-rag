# RAG 企业级检索优化设计文档

**日期:** 2026-06-17  
**状态:** 已确认  
**方案:** C — 完整四层架构

---

## 1. 背景与目标

### 1.1 当前状态

SDH-RAG 已具备成熟的检索管线：
- Hybrid Search（向量 + 关键词）+ RRF 融合
- 查询扩展（Query Expansion）、HyDE、多轮对话改写
- Cross-encoder Reranker（DashScope Qwen3-rerank）
- 4 种分块策略（length, smart, page, heading, regex）
- 按知识库隔离的 ES 索引

### 1.2 核心问题

| 问题 | 现状 | 影响 |
|------|------|------|
| 无文档去重 | 同一份文档可重复上传建索引 | 检索结果冗余，浪费 token |
| 无质量过滤 | 短文本、纯表格等噪音直接入库 | 噪声干扰 LLM 生成 |
| 无分层索引 | 每次检索扫描全部 chunk（O(n)） | 万份文档时延飙升、召回率暴跌 |
| 无元数据过滤 | 文档缺乏部门/类型/日期标签 | 无法缩小检索范围 |
| 无 MMR 多样性 | Top-K 结果可能语义高度重复 | 信息覆盖面窄 |
| 阈值未硬执行 | similarityThreshold 配置了但检索未截断 | 低相关片段污染 LLM 输入 |
| 无时间衰减 | 旧文档和新文档平等对待 | 信息过时风险 |

### 1.3 目标

将系统从"单层平铺检索"升级为 **"清洗分层 → 索引路由 → 渐进召回"** 的四层企业级架构，支撑万级文档量下的高精度、低时延检索。

---

## 2. 整体架构

```
                        ┌─────────────────────────────────┐
                        │        📨 用户查询 (Query)        │
                        └──────────────┬──────────────────┘
                                       │
                        ┌──────────────▼──────────────────┐
                        │   🧠 智能路由器 (IntentRouter)     │
                        │  FAQ→关键词 | 分析→向量 | 结构化→SQL │
                        └──────┬───────┬───────┬──────────┘
                               │       │       │
              ┌────────────────┘       │       └──────────────┐
              ▼                        ▼                      ▼
   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
   │ 关键词直查 (BM25)  │   │   语义检索 (向量)   │   │  Text2SQL 引擎   │
   └──────────────────┘   └────────┬─────────┘   └──────────────────┘
                                   │
          ┌────────────────────────┘
          │  第一级：粗筛（摘要索引）
          │  ANN 检索 → Top-50 文档 ID
          ▼
          │  第二级：精排（Reranker）
          │  Cross-encoder 对 Top-50 文档的 chunks 打分 → Top-20
          ▼
          │  第三级：细排（MMR 多样性）
          │  去冗余 → Top-5 最相关且多样的片段
          ▼
          │  第四级：后处理（阈值截断 + 时间衰减）
          │  低于阈值丢弃 + 新鲜度加权
          ▼
   ┌──────────────────────────────────────────────┐
   │         🤖 LLM 生成（带完整父子文档上下文）        │
   └──────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════
                    入库侧（离线处理 Pipeline）
═══════════════════════════════════════════════════════════

   📄 文档上传 → 🔍 去重(MD5+MinHash) → 🧹 质量过滤
   → ⏱️ 新鲜度打标 → ✂️ 分块(现有4模式) → 📝 摘要生成(LLM)
   → 🔢 向量化(Embedding) → 🗂️ 分层入库
                              ├── 📊 摘要索引 (sdh-rag-summary-{kbId})
                              ├── 📊 块索引   (sdh-rag-kb-{kbId}) [现有]
                              └── 📊 冷归档   (sdh-rag-cold-{kbId}) [>6月]
```

---

## 3. 第一层：入库前数据预处理

### 3.1 强去重（MD5 + MinHash 两级）

**流程：**
```
文档上传 → MD5 内容哈希快速判重（O(1)）
         → 若 MD5 碰撞 → 直接拒绝/覆盖
         → 若 MD5 不碰撞 → MinHash 近似查重
             → Jaccard 相似度 > 85% → 标记为疑似重复
```

**新增类：** `DocumentDedupService`
- `checkMd5Duplicate(knowledgeId, contentHash)` → 查 `document_hash` 表
- `checkMinHashSimilarity(knowledgeId, minHashSignature)` → Jaccard 对比

**新增表：** `document_hash`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| document_id | bigint | FK → knowledge_document |
| knowledge_id | bigint | FK → knowledge_base |
| content_md5 | varchar(32) | 全文 MD5 哈希 |
| minhash_signature | text | MinHash 签名（JSON 数组，128 维） |
| create_time | datetime | |

**插入点：** `DocumentProcessServiceImpl.processDocumentForKnowledge()` 最开始。

### 3.2 质量阈值过滤

在 Tika 解析后、分块前执行：
- 文本 < 50 字符 → 标记低质，不建索引
- 纯数字/表格占比 > 80% → 标记低质，建议走 Text2SQL
- 质量状态写入 `knowledge_document.quality_status`：(0=正常, 1=低质, 2=疑似重复)

### 3.3 新鲜度打标

**计算公式：**
```java
public static double calculateFreshness(LocalDate docDate) {
    long months = ChronoUnit.MONTHS.between(docDate, LocalDate.now());
    if (months <= 6)  return 1.0;
    if (months <= 12) return 1.0 - 0.5 * (months - 6) / 6.0;  // → 0.5
    if (months <= 24) return 0.5 - 0.2 * (months - 12) / 12.0; // → 0.3
    return 0.3;
}
```

入库时计算并写入 ES chunk 的 `freshness_score` 字段，后续时间衰减加权时使用。

---

## 4. 第二层：索引路由与分层索引

### 4.1 摘要索引（Summary Index）

**新增 ES 索引：** `sdh-rag-summary-{knowledgeId}`

| 字段 | 类型 | 说明 |
|------|------|------|
| doc_id | keyword | 文档 ID |
| summary_text | text | LLM 生成的 150-200 字摘要 |
| summary_embedding | dense_vector (1024d) | 摘要向量 |
| department | keyword | 部门 |
| doc_type | keyword | 文档类型 |
| doc_date | date | 文档日期 |
| freshness_score | float | 新鲜度 0-1 |
| chunk_count | integer | 文档总 chunk 数 |
| create_time | date | |

**摘要生成 prompt：**
```
请为以下文档生成一段 150-200 字的核心摘要，涵盖：
1. 文档主题和目的
2. 关键知识点/结论
3. 适用场景/范围
文档标题：{title}
文档内容（前3000字）：{content}
```

使用小模型生成，异步执行，不阻塞主入库流程。

**配置项（KnowledgeBase 新增）：**

| 字段 | 默认值 | 说明 |
|------|--------|------|
| enableSummaryIndex | true | 启用摘要索引 |
| summaryCoarseTopK | 50 | 粗筛返回文档数 |

### 4.2 元数据预过滤

**MySQL — `knowledge_document` 新增字段：**
```sql
ALTER TABLE knowledge_document ADD COLUMN department VARCHAR(100);
ALTER TABLE knowledge_document ADD COLUMN doc_type VARCHAR(50);
ALTER TABLE knowledge_document ADD COLUMN doc_date DATE;
ALTER TABLE knowledge_document ADD COLUMN freshness_score DECIMAL(3,2) DEFAULT 1.0;
ALTER TABLE knowledge_document ADD COLUMN content_md5 VARCHAR(32);
ALTER TABLE knowledge_document ADD COLUMN quality_status TINYINT DEFAULT 0;
```

**ES chunk 索引 mapping 新增：**
```json
{
  "department": { "type": "keyword" },
  "doc_type": { "type": "keyword" },
  "doc_date": { "type": "date" },
  "freshness_score": { "type": "float" },
  "doc_summary": { "type": "text" }
}
```

**检索时 ES filter 前置：**
```java
// VectorStoreServiceImpl.hybridSearch() 新增 metadataFilter 参数
BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
metadataFilter.forEach((key, value) ->
    boolQuery.must(QueryBuilders.termQuery(key, value)));
// 然后叠加向量/关键词子查询
```

**前端变更：**
- 文档上传页新增：文档类型下拉（制度/手册/合同/报告/FAQ）、部门下拉、文档日期选择器
- 聊天输入框新增"高级筛选"折叠面板

### 4.3 层级索引（父子文档关联）

每个 chunk 检索命中时，自动携带父文档摘要和位置信息：

```java
// Source DTO 新增字段
private String documentSummary;    // 父文档摘要
private Integer chunkIndex;        // 第几个 chunk
private Integer totalChunks;       // 文档总 chunk 数
```

**LLM prompt 上下文构建（层级结构）：**
```
📄 文档：《{docTitle}》（{department} · {docType} · {docDate}）
   摘要：{docSummary}
   片段 [{chunkIndex}/{totalChunks}]（相关度: {score}）：
   {chunkContent}
```

### 4.4 冷热分离

- 热库 `sdh-rag-{kbId}`：freshness > 0.5（6 个月内），每次优先检索
- 冷库 `sdh-rag-cold-{kbId}`：freshness ≤ 0.5（归档），仅热库结果不足时触发
- 冷库结果最终分乘以 0.9 降权

---

## 5. 第三层：级联检索（三级漏斗）

### 5.1 检索流程（RagSearchServiceImpl 重构）

```
1. 智能路由 → 确定策略(关键词优先/向量优先/结构化)
2. 查询改写 + HyDE（复用现有逻辑）
3. 粗筛：ANN 检索摘要索引 → Top-50 文档 ID
4. 精排：仅对 Top-50 文档的 chunks 做 ES 检索 → 每文档 Top-3 chunk
          → Reranker 打分 → Top-20
5. 细排：MMR 去冗余 → Top-5
6. 后处理：阈值截断 + 时间衰减
7. 返回 LLM（带层级上下文）
```

### 5.2 智能路由器扩展

现有 `IntentRouter` 扩展为多策略路由：

```java
public enum QueryStrategy {
    KEYWORD_FIRST,  // FAQ/定义类 → keywordWeight=0.8
    VECTOR_FIRST,   // 分析/推理类 → vectorWeight=0.8
    STRUCTURED,     // 报表/统计类 → Text2SQL
    HYBRID          // 默认混合
}
```

Router prompt 中输出策略 + 提取的元数据过滤条件（如 department: "HR", doc_type: "policy"）。

### 5.3 MMR 多样性重排

**新增类：** `DiversityRankingService`

```
MMR = argmax[ λ × relevance(chunk) - (1-λ) × max_similarity(chunk, 已选中的所有chunk) ]
λ = 0.7（可配置）
```

贪心选取，每次选相关性高且与已选结果语义不重复的 chunk，最终返回 Top-5 覆盖面最广的结果。

### 5.4 降级策略矩阵

| 场景 | 策略 | 用户感知 |
|------|------|---------|
| 摘要索引 0 结果 | 降级为全量 chunk 检索 | 稍慢但能出结果 |
| 精排后全部低于阈值 | 返回"知识库未覆盖" + 推荐相似问题 | 明确告知，不编造 |
| Reranker 不可用 | 跳过精排，直接 MMR | 精度略降不中断 |
| Embedding 超时 | 降级为纯 BM25 关键词检索 | 能出结果 |
| 冷库连接失败 | 仅用热库结果 | 范围缩小不出错 |

---

## 6. 第四层：噪声过滤与后处理

### 6.1 硬阈值截断

**新增类：** `PostProcessingService`

```java
public List<KnowledgeChunkVO> process(List<KnowledgeChunkVO> chunks, double threshold) {
    // Step 1: 过滤低于阈值的结果
    List<KnowledgeChunkVO> passed = chunks.stream()
        .filter(c -> c.getScore() >= threshold)
        .collect(toList());

    // Step 2: 时间衰减加权
    // finalScore = relevanceScore × (0.7 + 0.3 × freshnessScore)
    for (KnowledgeChunkVO chunk : passed) {
        chunk.setFinalScore(chunk.getScore() * (0.7 + 0.3 * chunk.getFreshnessScore()));
    }

    // Step 3: 全部截断 → 降级提示
    if (passed.isEmpty()) {
        return emptyWithSuggestion("知识库未覆盖该问题，建议更换关键词或上传相关文档");
    }

    return passed;
}
```

### 6.2 配置项汇总（application.yml）

```yaml
knowledge:
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

---

## 7. LLM Prompt 优化

**改进后的 Prompt 模板：**

```
你是一个企业知识库助手。请基于以下检索到的文档片段回答用户问题。

【检索上下文】
{层级文档上下文，含标题/部门/类型/日期/摘要/片段位置/相关度}

【回答要求】
1. 优先使用高相关度（score ≥ 0.8）的片段
2. 如果多个片段信息矛盾，以最新文档（docDate 最近）为准
3. 如果检索到的信息不足以回答问题，请明确告知用户，不要编造不存在的信息
4. 引用信息时标注来源：如"根据《{docTitle}》..."

【用户问题】
{question}
```

---

## 8. 文件变更清单

### 新增文件（Backend）

| 文件 | 职责 |
|------|------|
| `service/DocumentDedupService.java` | MD5 + MinHash 去重接口 |
| `service/impl/DocumentDedupServiceImpl.java` | 去重实现 |
| `service/SummaryIndexService.java` | 摘要索引 CRUD + 粗筛检索接口 |
| `service/impl/SummaryIndexServiceImpl.java` | 摘要索引实现 |
| `service/DiversityRankingService.java` | MMR 多样性重排 |
| `service/PostProcessingService.java` | 硬阈值截断 + 时间衰减 |
| `config/FreshnessConfig.java` | 新鲜度计算配置 |

### 修改文件（Backend）

| 文件 | 变更内容 |
|------|---------|
| `service/impl/DocumentProcessServiceImpl.java` | +去重/质量过滤/摘要生成/新鲜度打标 |
| `service/impl/RagSearchServiceImpl.java` | 三级漏斗重构 |
| `service/impl/VectorStoreServiceImpl.java` | +元数据过滤参数/+冷热索引切换 |
| `service/impl/ElasticsearchIndexServiceImpl.java` | +摘要索引 mapping 创建 |
| `service/impl/ChatServiceImpl.java` | +层级上下文 prompt 构建 |
| `entity/KnowledgeBase.java` | +新配置字段（8 个） |
| `entity/KnowledgeDocument.java` | +元数据字段（6 个） |
| `dto/SearchResult.java` | +层级上下文字段 |
| `advisor/rag/*.java` | 适配新检索流程 |

### 新增 SQL 迁移

`src/main/resources/db/migration/V2__rag_optimization.sql`
- 新增 `document_hash` 表
- `knowledge_document` 新增 6 个字段
- `knowledge_base` 新增 8 个配置字段

### 修改文件（Frontend）

| 文件 | 变更内容 |
|------|---------|
| `views/chat/components/ChatInput.vue` | +高级筛选面板 |
| `views/knowledge-base/components/ConfigPanel.vue` | +摘要索引/冷热分离/MMR 配置项 |
| `views/document/components/DocumentUpload.vue` | +元数据采集表单 |
| `api/chat.ts` | +新接口类型 |
| `api/knowledgeBase.ts` | +新配置字段类型 |
| `types/` | +新 TypeScript 类型定义 |

---

## 9. 风险与注意事项

1. **摘要生成成本：** 每份文档额外调用一次 LLM。建议使用小模型（如 qwen-turbo），成本可控。预估 1 万份文档 × 0.5 元/次 = 5000 元一次性成本。
2. **ES 索引增多：** 每个 KB 从 1 个索引变为 3 个（热 chunk + 冷 chunk + 摘要），需评估 ES 集群容量。
3. **MMR 计算开销：** Top-20 中选 Top-5，需要 20 × 5 × embedding_similarity 次计算，约 100 次向量乘法，毫秒级。
4. **冷热分离边界：** 6 个月为默认值，需根据实际业务文档的生命周期调整。
5. **向后兼容：** 新字段全部设默认值，老文档 freshScore 默认为 0.5，不会影响现有功能。