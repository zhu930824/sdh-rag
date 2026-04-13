package cn.sdh.backend.rag;

import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.service.ChatService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.List;

/**
 * 历史感知查询转换器
 * 根据多轮对话历史改写查询，使其更加完整和明确
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class HistoryAwareQueryTransformer implements QueryTransformer {

    private final ChatService chatService;
    private final KnowledgeBase knowledgeBase;

    @Override
    public Query transform(Query query) {
        // 根据知识库设置判断是否启用查询改写
        boolean enableRewrite = knowledgeBase.getEnableRewrite() != null && knowledgeBase.getEnableRewrite();

        // 如果未启用查询改写，或者没有对话历史，直接返回原查询
        if (!enableRewrite || query.history() == null || query.history().isEmpty()) {
            log.debug("查询改写未启用或无历史记录，跳过改写 (knowledgeBase.enableRewrite={})", knowledgeBase.getEnableRewrite());
            return query;
        }

        String originalQuery = query.text();
        List<Message> history = query.history();

        log.info("开始查询改写: 原始查询={}, 历史消息数={}", originalQuery, history.size());

        // 构建改写提示词
        String rewritePrompt = buildRewritePrompt(originalQuery, history);

        try {
            // 从上下文获取 modelId
            String modelId = null;
            Object modelIdObj = query.context().get("modelId");
            if (modelIdObj != null) {
                modelId = modelIdObj.toString();
            }

            // 调用 LLM 进行改写
            String rewrittenQuery = chatService.chat(rewritePrompt, modelId);

            if (rewrittenQuery != null && !rewrittenQuery.trim().isEmpty()) {
                rewrittenQuery = rewrittenQuery.trim();
                log.info("查询改写完成: 原始=[{}] -> 改写=[{}]", originalQuery, rewrittenQuery);

                // 返回改写后的查询
                return Query.builder()
                        .text(rewrittenQuery)
                        .history(query.history())
                        .context(query.context())
                        .build();
            }
        } catch (Exception e) {
            log.error("查询改写失败: {}", e.getMessage(), e);
        }

        // 改写失败，返回原查询
        return query;
    }

    /**
     * 构建查询改写提示词
     */
    private String buildRewritePrompt(String query, List<Message> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个查询改写助手。请根据对话历史，将用户的最新问题改写为一个独立的、完整的查询。\n\n");
        prompt.append("要求：\n");
        prompt.append("1. 保持用户原有意图\n");
        prompt.append("2. 补充必要的上下文信息（如代词指代的具体内容）\n");
        prompt.append("3. 只输出改写后的查询，不要解释\n\n");

        prompt.append("对话历史：\n");
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            String role = msg.getMessageType().getValue();
            prompt.append(role).append(": ").append(msg.getText()).append("\n");
        }

        prompt.append("\n用户最新问题: ").append(query);
        prompt.append("\n\n改写后的查询：");

        return prompt.toString();
    }
}
