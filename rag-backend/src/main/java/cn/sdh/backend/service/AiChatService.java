package cn.sdh.backend.service;

import reactor.core.publisher.Flux;

/**
 * AI聊天服务接口
 */
public interface AiChatService {

    /**
     * 流式对话
     * @param prompt 提示词/用户问题
     * @param modelId 模型ID（可选，使用数据库配置的模型）
     * @return 流式响应
     */
    Flux<String> streamChat(String prompt, String modelId);

    /**
     * 流式对话（带系统提示词）
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户问题
     * @param modelId 模型ID
     * @return 流式响应
     */
    Flux<String> streamChat(String systemPrompt, String userPrompt, String modelId);

    /**
     * 同步对话
     * @param prompt 提示词/用户问题
     * @param modelId 模型ID
     * @return 回答
     */
    String chat(String prompt, String modelId);
}
