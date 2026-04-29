package cn.sdh.backend.service.impl;

import cn.sdh.backend.config.IntentRouterConfig;
import cn.sdh.backend.dto.IntentRouterRequest;
import cn.sdh.backend.dto.IntentRouterResponse;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.IntentRouterService;
import cn.sdh.backend.service.KnowledgeBaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        // 获取用户的所有启用知识库
        List<KnowledgeBase> knowledgeBases = knowledgeBaseService.getKnowledgeBaseList(userId);
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return List.of();
        }
        // 限制数量避免prompt过长
        return knowledgeBases.stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .limit(config.getMaxRecommendedBases() * 2)
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
