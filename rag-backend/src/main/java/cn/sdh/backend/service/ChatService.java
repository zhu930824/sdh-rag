package cn.sdh.backend.service;

import cn.sdh.backend.entity.ChatHistory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import reactor.core.publisher.Flux;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 发起问答（流式响应）
     * @param question 问题
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param knowledgeId 知识库ID（可选）
     * @param modelId 模型ID（可选）
     * @return 流式响应
     */
    Flux<String> ask(String question, String sessionId, Long userId, Long knowledgeId, Long modelId);

    /**
     * 获取历史对话
     * @param sessionId 会话ID
     * @param page 页码
     * @param size 每页大小
     * @param userId 用户ID
     * @return 分页结果
     */
    Page<ChatHistory> getHistory(String sessionId, int page, int size, Long userId);

    /**
     * 删除对话
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteSession(String sessionId, Long userId);

    /**
     * 保存聊天记录
     * @param chatHistory 聊天历史
     * @return 是否成功
     */
    boolean saveChatHistory(ChatHistory chatHistory);

    /**
     * 同步聊天接口
     * @param userId 用户ID
     * @param question 问题
     * @param sessionId 会话ID
     * @return 回答
     */
    String chat(Long userId, String question, String sessionId);


    String chat(String question, String sessionId);
}
