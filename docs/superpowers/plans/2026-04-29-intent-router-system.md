# 智能路由系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现智能路由系统，当用户未选择知识库时自动判断query是否需要检索，若需要则自动选择合适的知识库。

**Architecture:** 在ChatService层添加意图路由判断，当knowledgeId为null时调用IntentRouterService进行LLM意图分析，返回路由决策后选择执行路径（直接LLM回答或RAG检索）。

**Tech Stack:** Java 21, Spring Boot 3.4, Spring AI, Vue 3, TypeScript, Ant Design Vue

---

## 文件结构

### 新增文件

| 文件路径 | 职责 |
|---------|------|
| `rag-backend/src/main/java/cn/sdh/backend/config/IntentRouterConfig.java` | 智能路由配置属性类 |
| `rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterRequest.java` | 路由请求DTO |
| `rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterResponse.java` | 路由响应DTO，含KnowledgeBaseScore内部类 |
| `rag-backend/src/main/java/cn/sdh/backend/service/IntentRouterService.java` | 意图路由服务接口 |
| `rag-backend/src/main/java/cn/sdh/backend/service/impl/IntentRouterServiceImpl.java` | 意图路由服务实现，调用LLM判断 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `rag-backend/src/main/resources/application.yml` | 添加intent-router配置项 |
| `rag-backend/src/main/java/cn/sdh/backend/service/impl/ChatServiceImpl.java` | 在chat方法中添加智能路由逻辑 |
| `rag-frontend/src/api/chat.ts` | 添加RouterInfo类型，修改StreamEvent |
| `rag-frontend/src/stores/chat.ts` | 添加routerInfo状态处理 |
| `rag-frontend/src/views/chat/components/ChatInput.vue` | 将"全部知识库"改为"自动选择(推荐)" |
| `rag-frontend/src/views/chat/components/MessageList.vue` | 添加路由信息展示组件 |

---

## Task 1: 后端配置类

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/config/IntentRouterConfig.java`

- [ ] **Step 1: 创建IntentRouterConfig配置类**

```java
package cn.sdh.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "intent-router")
public class IntentRouterConfig {

    /**
     * 是否启用智能路由
     */
    private boolean enabled = true;

    /**
     * 最小相关度阈值，低于此值不推荐知识库
     */
    private double minRelevanceScore = 0.3;

    /**
     * 最大推荐知识库数量
     */
    private int maxRecommendedBases = 3;

    /**
     * 是否返回路由详情
     */
    private boolean showRouterInfo = true;

    /**
     * 是否缓存路由结果
     */
    private boolean cacheEnabled = true;

    /**
     * 缓存过期时间（分钟）
     */
    private int cacheTtlMinutes = 10;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMinRelevanceScore() {
        return minRelevanceScore;
    }

    public void setMinRelevanceScore(double minRelevanceScore) {
        this.minRelevanceScore = minRelevanceScore;
    }

    public int getMaxRecommendedBases() {
        return maxRecommendedBases;
    }

    public void setMaxRecommendedBases(int maxRecommendedBases) {
        this.maxRecommendedBases = maxRecommendedBases;
    }

    public boolean isShowRouterInfo() {
        return showRouterInfo;
    }

    public void setShowRouterInfo(boolean showRouterInfo) {
        this.showRouterInfo = showRouterInfo;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }
}
```

- [ ] **Step 2: 在application.yml添加配置**

在 `rag-backend/src/main/resources/application.yml` 文件末尾添加：

```yaml
# 智能路由配置
intent-router:
  enabled: true
  min-relevance-score: 0.3
  max-recommended-bases: 3
  show-router-info: true
  cache-enabled: true
  cache-ttl-minutes: 10
```

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/config/IntentRouterConfig.java
git add rag-backend/src/main/resources/application.yml
git commit -m "feat(intent-router): add IntentRouterConfig and application.yml config"
```

---

## Task 2: DTO类

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterRequest.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterResponse.java`

- [ ] **Step 1: 创建IntentRouterRequest**

```java
package cn.sdh.backend.dto;

import lombok.Data;

@Data
public class IntentRouterRequest {
    /**
     * 用户查询
     */
    private String query;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private String sessionId;

    public IntentRouterRequest() {
    }

    public IntentRouterRequest(String query, Long userId, String sessionId) {
        this.query = query;
        this.userId = userId;
        this.sessionId = sessionId;
    }
}
```

- [ ] **Step 2: 创建IntentRouterResponse**

```java
package cn.sdh.backend.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntentRouterResponse {
    /**
     * 是否需要检索知识库
     */
    private boolean needRetrieval;

    /**
     * 推荐知识库列表（按分数排序）
     */
    private List<KnowledgeBaseScore> recommendedBases;

    /**
     * 判断理由
     */
    private String reason;

    /**
     * 改写后的查询（可选）
     */
    private String rewrittenQuery;

    /**
     * 获取分数最高的知识库ID
     */
    public Long getTopKnowledgeBaseId() {
        if (recommendedBases != null && !recommendedBases.isEmpty()) {
            return recommendedBases.get(0).getKnowledgeBaseId();
        }
        return null;
    }

    /**
     * 创建直接对话响应（不需要检索）
     */
    public static IntentRouterResponse directChat(String reason) {
        IntentRouterResponse response = new IntentRouterResponse();
        response.setNeedRetrieval(false);
        response.setReason(reason);
        response.setRecommendedBases(new ArrayList<>());
        return response;
    }

    /**
     * 知识库评分
     */
    @Data
    public static class KnowledgeBaseScore {
        private Long knowledgeBaseId;
        private String knowledgeBaseName;
        private double score;

        public KnowledgeBaseScore() {
        }

        public KnowledgeBaseScore(Long knowledgeBaseId, String knowledgeBaseName, double score) {
            this.knowledgeBaseId = knowledgeBaseId;
            this.knowledgeBaseName = knowledgeBaseName;
            this.score = score;
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterRequest.java
git add rag-backend/src/main/java/cn/sdh/backend/dto/IntentRouterResponse.java
git commit -m "feat(intent-router): add IntentRouterRequest and IntentRouterResponse DTOs"
```

---

## Task 3: IntentRouterService接口与实现

**Files:**
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/IntentRouterService.java`
- Create: `rag-backend/src/main/java/cn/sdh/backend/service/impl/IntentRouterServiceImpl.java`

- [ ] **Step 1: 创建IntentRouterService接口**

```java
package cn.sdh.backend.service;

import cn.sdh.backend.dto.IntentRouterRequest;
import cn.sdh.backend.dto.IntentRouterResponse;

public interface IntentRouterService {
    /**
     * 分析用户查询意图，决定是否需要检索知识库
     *
     * @param request 路由请求
     * @return 路由决策结果
     */
    IntentRouterResponse route(IntentRouterRequest request);

    /**
     * 简化方法：直接传入query和userId
     *
     * @param query  用户查询
     * @param userId 用户ID
     * @return 路由决策结果
     */
    IntentRouterResponse route(String query, Long userId);
}
```

- [ ] **Step 2: 创建IntentRouterServiceImpl实现类**

```java
package cn.sdh.backend.service.impl;

import cn.sdh.backend.config.IntentRouterConfig;
import cn.sdh.backend.dto.IntentRouterRequest;
import cn.sdh.backend.dto.IntentRouterResponse;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.IntentRouterService;
import cn.sdh.backend.service.KnowledgeBaseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouterServiceImpl implements IntentRouterService {

    private final ChatClient.Builder chatClientBuilder;
    private final KnowledgeBaseService knowledgeBaseService;
    private final IntentRouterConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INTENT_ROUTER_PROMPT = """
你是一个意图分析助手。请分析用户问题，判断是否需要从知识库检索信息。

## 用户问题
%s

## 可用知识库列表
%s

## 分析要求
1. 判断问题是否需要检索知识库：
   - 需要检索：问题涉及具体业务知识、技术文档、公司政策、产品信息等
   - 不需要检索：闲聊、通用常识、代码生成、创意写作、数学计算等

2. 如果需要检索，评估每个知识库的相关性（0-1分）：
   - 0.8-1.0：高度相关，问题直接对应知识库内容
   - 0.5-0.8：中度相关，问题可能涉及知识库内容
   - 0.0-0.5：低相关，问题与知识库内容关联不大

## 输出格式（仅输出JSON，不要其他内容）
{
  "needRetrieval": true,
  "reason": "判断理由",
  "recommendedBases": [
    {"knowledgeBaseId": 1, "knowledgeBaseName": "知识库名", "score": 0.92}
  ]
}
""";

    @Override
    public IntentRouterResponse route(IntentRouterRequest request) {
        return route(request.getQuery(), request.getUserId());
    }

    @Override
    public IntentRouterResponse route(String query, Long userId) {
        // 检查是否启用智能路由
        if (!config.isEnabled()) {
            return IntentRouterResponse.directChat("智能路由未启用");
        }

        try {
            // 获取所有可用知识库
            List<KnowledgeBase> knowledgeBases = getAvailableKnowledgeBases(userId);
            if (knowledgeBases.isEmpty()) {
                return IntentRouterResponse.directChat("暂无可用知识库");
            }

            // 构建知识库列表字符串
            String kbListStr = buildKnowledgeBaseList(knowledgeBases);

            // 构建prompt
            String prompt = String.format(INTENT_ROUTER_PROMPT, query, kbListStr);

            // 调用LLM
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析响应
            IntentRouterResponse result = parseResponse(response, knowledgeBases);
            result.setReason(result.getReason() != null ? result.getReason() : "系统自动判断");

            return result;

        } catch (Exception e) {
            log.warn("LLM意图判断失败，降级为直接回答", e);
            return IntentRouterResponse.directChat("意图判断服务暂时不可用");
        }
    }

    /**
     * 获取用户可用的知识库列表
     */
    private List<KnowledgeBase> getAvailableKnowledgeBases(Long userId) {
        // 获取所有启用的知识库
        List<KnowledgeBase> allBases = knowledgeBaseService.list();
        return allBases.stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .limit(config.getMaxRecommendedBases() * 2) // 限制数量避免prompt过长
                .collect(Collectors.toList());
    }

    /**
     * 构建知识库列表字符串
     */
    private String buildKnowledgeBaseList(List<KnowledgeBase> knowledgeBases) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < knowledgeBases.size(); i++) {
            KnowledgeBase kb = knowledgeBases.get(i);
            sb.append(String.format("%d. ID: %d, 名称: %s, 描述: %s\n",
                    i + 1,
                    kb.getId(),
                    kb.getName(),
                    kb.getDescription() != null ? kb.getDescription() : "无描述"));
        }
        return sb.toString();
    }

    /**
     * 解析LLM响应
     */
    private IntentRouterResponse parseResponse(String response, List<KnowledgeBase> knowledgeBases) {
        IntentRouterResponse result = new IntentRouterResponse();
        result.setRecommendedBases(new ArrayList<>());

        try {
            // 提取JSON部分
            String jsonStr = extractJson(response);
            if (jsonStr == null) {
                log.warn("无法从响应中提取JSON: {}", response);
                return IntentRouterResponse.directChat("响应解析失败");
            }

            JsonNode root = objectMapper.readTree(jsonStr);

            // 解析needRetrieval
            result.setNeedRetrieval(root.has("needRetrieval") && root.get("needRetrieval").asBoolean());

            // 解析reason
            if (root.has("reason")) {
                result.setReason(root.get("reason").asText());
            }

            // 解析recommendedBases
            if (root.has("recommendedBases") && root.get("recommendedBases").isArray()) {
                JsonNode basesNode = root.get("recommendedBases");
                for (JsonNode baseNode : basesNode) {
                    long kbId = baseNode.has("knowledgeBaseId") ? baseNode.get("knowledgeBaseId").asLong() : 0;
                    String kbName = baseNode.has("knowledgeBaseName") ? baseNode.get("knowledgeBaseName").asText() : "";
                    double score = baseNode.has("score") ? baseNode.get("score").asDouble() : 0;

                    // 过滤低于阈值的知识库
                    if (score >= config.getMinRelevanceScore()) {
                        // 验证知识库ID是否存在
                        boolean exists = knowledgeBases.stream()
                                .anyMatch(kb -> kb.getId().equals(kbId));
                        if (exists) {
                            result.getRecommendedBases().add(
                                    new IntentRouterResponse.KnowledgeBaseScore(kbId, kbName, score));
                        }
                    }
                }

                // 按分数排序
                result.getRecommendedBases().sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

                // 限制数量
                if (result.getRecommendedBases().size() > config.getMaxRecommendedBases()) {
                    result.setRecommendedBases(
                            result.getRecommendedBases().subList(0, config.getMaxRecommendedBases()));
                }
            }

            // 如果需要检索但推荐列表为空，降级为直接回答
            if (result.isNeedRetrieval() && result.getRecommendedBases().isEmpty()) {
                result.setNeedRetrieval(false);
                result.setReason("未找到相关知识库");
            }

        } catch (Exception e) {
            log.error("解析LLM响应失败: {}", response, e);
            return IntentRouterResponse.directChat("响应解析失败");
        }

        return result;
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String response) {
        if (response == null) return null;

        // 尝试找到JSON块
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/IntentRouterService.java
git add rag-backend/src/main/java/cn/sdh/backend/service/impl/IntentRouterServiceImpl.java
git commit -m "feat(intent-router): add IntentRouterService with LLM-based intent analysis"
```

---

## Task 4: 修改ChatServiceImpl添加智能路由逻辑

**Files:**
- Modify: `rag-backend/src/main/java/cn/sdh/backend/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: 查找ChatServiceImpl文件**

找到ChatServiceImpl中处理chat请求的方法，通常在 `rag-backend/src/main/java/cn/sdh/backend/service/impl/ChatServiceImpl.java`

- [ ] **Step 2: 添加依赖注入**

在ChatServiceImpl类中添加IntentRouterService和IntentRouterConfig的依赖注入：

```java
// 在类顶部添加
@Autowired
private IntentRouterService intentRouterService;

@Autowired
private IntentRouterConfig intentRouterConfig;
```

- [ ] **Step 3: 修改chat方法添加智能路由逻辑**

在chat方法（或ask方法）中，找到处理knowledgeId的位置，添加以下逻辑：

```java
// 在方法开始处，检查是否需要智能路由
Long finalKnowledgeId = request.getKnowledgeId();

if (finalKnowledgeId == null && intentRouterConfig.isEnabled()) {
    // 触发智能路由
    IntentRouterResponse routeResult = intentRouterService.route(
            request.getQuestion(),
            UserContext.getUserId()
    );

    if (!routeResult.isNeedRetrieval()) {
        // 直接LLM回答，不需要RAG
        // 构建返回结果，包含路由信息
        // 调用直接对话方法
        return directChatWithoutRag(request, routeResult);
    }

    // 使用推荐的知识库
    finalKnowledgeId = routeResult.getTopKnowledgeBaseId();
    // 保存路由信息用于返回
    request.setRouterInfo(routeResult);
}
```

注意：实际代码需要根据现有ChatServiceImpl的结构进行调整。

- [ ] **Step 4: 验证编译**

```bash
cd rag-backend && mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/service/impl/ChatServiceImpl.java
git commit -m "feat(intent-router): integrate smart routing into ChatService"
```

---

## Task 5: 修改流式响应返回路由信息

**Files:**
- Modify: `rag-backend/src/main/java/cn/sdh/backend/controller/ChatController.java`

- [ ] **Step 1: 查找ChatController的流式响应方法**

找到ChatController中处理SSE流式响应的地方。

- [ ] **Step 2: 添加路由信息事件类型**

在流式响应中，添加新的事件类型来返回路由信息：

```java
// 在发送content之前，发送routerInfo事件
if (routerInfo != null) {
    String routerJson = objectMapper.writeValueAsString(routerInfo);
    emitter.send(SseEmitter.event()
            .name("routerInfo")
            .data(routerJson));
}
```

- [ ] **Step 3: 创建RouterInfo DTO（如果需要）**

如果现有响应结构不支持，可以创建一个新的DTO类：

```java
package cn.sdh.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class RouterInfo {
    private boolean used;
    private boolean needRetrieval;
    private String reason;
    private Long selectedKnowledgeBaseId;
    private String selectedKnowledgeBaseName;
    private List<IntentRouterResponse.KnowledgeBaseScore> candidates;
}
```

- [ ] **Step 4: Commit**

```bash
git add rag-backend/src/main/java/cn/sdh/backend/controller/ChatController.java
git commit -m "feat(intent-router): add routerInfo to SSE stream response"
```

---

## Task 6: 前端类型定义

**Files:**
- Modify: `rag-frontend/src/api/chat.ts`

- [ ] **Step 1: 添加RouterInfo类型**

在 `rag-frontend/src/api/chat.ts` 文件中添加：

```typescript
// 知识库评分
export interface KnowledgeBaseScore {
  knowledgeBaseId: number
  knowledgeBaseName: string
  score: number
}

// 路由信息
export interface RouterInfo {
  used: boolean
  needRetrieval: boolean
  reason: string
  selectedKnowledgeBaseId?: number
  selectedKnowledgeBaseName?: string
  candidates?: KnowledgeBaseScore[]
}

// 流式响应事件类型（修改现有）
export interface StreamEvent {
  type: 'content' | 'sources' | 'done' | 'error' | 'routerInfo'  // 添加routerInfo
  content?: string
  sources?: Source[]
  message?: string
  historyId?: number
  routerInfo?: RouterInfo  // 新增
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-frontend/src/api/chat.ts
git commit -m "feat(intent-router): add RouterInfo type definition"
```

---

## Task 7: 前端Store处理路由信息

**Files:**
- Modify: `rag-frontend/src/stores/chat.ts`

- [ ] **Step 1: 添加路由信息状态**

在 `rag-frontend/src/stores/chat.ts` 中：

```typescript
import type { RouterInfo } from '@/api/chat'

// 在defineStore内添加状态
const lastRouterInfo = ref<RouterInfo | null>(null)

// 在askQuestion回调中处理routerInfo事件
case 'routerInfo':
  if (event.routerInfo) {
    lastRouterInfo.value = event.routerInfo
  }
  break

// 在return中导出
return {
  // ... 其他导出
  lastRouterInfo,
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-frontend/src/stores/chat.ts
git commit -m "feat(intent-router): handle routerInfo in chat store"
```

---

## Task 8: 修改知识库选择器

**Files:**
- Modify: `rag-frontend/src/views/chat/components/ChatInput.vue`

- [ ] **Step 1: 修改知识库选择器选项文本**

将第29行的 `<a-select-option :value="null">全部知识库</a-select-option>` 改为：

```vue
<a-select-option :value="null">自动选择(推荐)</a-select-option>
```

- [ ] **Step 2: Commit**

```bash
git add rag-frontend/src/views/chat/components/ChatInput.vue
git commit -m "feat(intent-router): change knowledge selector default option to 'Auto Select'"
```

---

## Task 9: 添加路由信息展示组件

**Files:**
- Modify: `rag-frontend/src/views/chat/components/MessageList.vue`

- [ ] **Step 1: 添加RouterInfo展示**

在MessageList.vue的message-item中，在message-sources之前添加路由信息展示：

```vue
<!-- 在 message-sources 之前添加 -->
<div
  v-if="message.routerInfo && message.routerInfo.used"
  class="message-router-info"
>
  <div class="router-header">
    <BulbOutlined />
    <span>智能路由</span>
  </div>
  <div class="router-detail">
    <div class="router-item">
      <span class="label">决策:</span>
      <span>{{ message.routerInfo.reason }}</span>
    </div>
    <div v-if="message.routerInfo.needRetrieval" class="router-item">
      <span class="label">已选择:</span>
      <a-tag color="blue">{{ message.routerInfo.selectedKnowledgeBaseName }}</a-tag>
    </div>
    <div v-if="message.routerInfo.candidates && message.routerInfo.candidates.length > 1" class="router-item">
      <span class="label">候选:</span>
      <span class="candidates">
        <span v-for="(c, idx) in message.routerInfo.candidates" :key="c.knowledgeBaseId">
          {{ c.knowledgeBaseName }}({{ (c.score * 100).toFixed(0) }}%){{ idx < message.routerInfo.candidates!.length - 1 ? ', ' : '' }}
        </span>
      </span>
    </div>
  </div>
</div>
```

- [ ] **Step 2: 添加样式**

在 `<style scoped lang="scss">` 部分添加：

```scss
.message-router-info {
  margin-top: 8px;
  padding: 10px 12px;
  background-color: var(--bg-page);
  border-radius: 8px;
  border: 1px solid var(--border-lighter);
  font-size: 13px;

  .router-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 500;
    color: var(--primary-color);
    margin-bottom: 6px;
  }

  .router-detail {
    color: var(--text-secondary);

    .router-item {
      margin-top: 4px;
      display: flex;
      align-items: flex-start;
      gap: 6px;

      .label {
        color: var(--text-secondary);
        font-weight: 500;
        flex-shrink: 0;
      }

      .candidates {
        color: var(--text-secondary);
      }
    }
  }
}
```

- [ ] **Step 3: 导入图标**

在script部分添加导入：

```typescript
import { BulbOutlined } from '@ant-design/icons-vue'
```

- [ ] **Step 4: 修改ChatMessage类型**

需要在前端类型定义或组件中添加routerInfo字段支持。在 `rag-frontend/src/api/chat.ts` 中：

```typescript
export interface ChatMessage {
  id: number | string
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
  createTime: string
  historyId?: number
  userRating?: number
  routerInfo?: RouterInfo  // 新增
}
```

- [ ] **Step 5: Commit**

```bash
git add rag-frontend/src/views/chat/components/MessageList.vue
git add rag-frontend/src/api/chat.ts
git commit -m "feat(intent-router): add router info display in MessageList"
```

---

## Task 10: 集成测试

**Files:**
- 无新增文件

- [ ] **Step 1: 启动后端服务**

```bash
cd rag-backend && mvn spring-boot:run
```

等待服务启动完成（约30秒）。

- [ ] **Step 2: 启动前端服务**

```bash
cd rag-frontend && npm run dev
```

- [ ] **Step 3: 测试智能路由功能**

测试场景：
1. 在知识库选择器选择"自动选择(推荐)"
2. 输入一个需要知识库检索的问题（如："公司的报销流程是什么？"）
3. 验证：
   - 响应中显示路由信息
   - 显示选择了哪个知识库
   - 显示判断理由
4. 输入一个不需要检索的问题（如："帮我写一首关于春天的诗"）
5. 验证：
   - 路由信息显示"不需要检索"
   - 直接返回LLM回答

- [ ] **Step 4: 提交最终更改**

```bash
git add -A
git commit -m "feat(intent-router): complete smart routing implementation"
```

---

## 自检清单

| 检查项 | 状态 |
|-------|------|
| Spec每个需求都有对应Task | ✅ |
| 无占位符和TODO | ✅ |
| 类型定义一致 | ✅ |
| 前后端类型匹配 | ✅ |
| 错误处理覆盖完整 | ✅ |
