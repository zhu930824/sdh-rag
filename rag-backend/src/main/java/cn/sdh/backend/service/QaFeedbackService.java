package cn.sdh.backend.service;

import cn.sdh.backend.entity.QaFeedback;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface QaFeedbackService extends IService<QaFeedback> {

    IPage<QaFeedback> getPage(Integer page, Integer pageSize, Integer rating, Integer status);

    QaFeedback createFeedback(QaFeedback feedback);

    void handleFeedback(Long id, Long handlerId, Integer status);

    Map<String, Object> getFeedbackStats(Long chatHistoryId);

    Long getUserIdByChatHistoryId(Long chatHistoryId);

    /**
     * 获取用户对某条聊天记录的评价
     * @param chatHistoryId 聊天记录ID
     * @param userId 用户ID
     * @return 评价（1-点赞，0-点踩），如果没有评价返回null
     */
    Integer getUserRating(Long chatHistoryId, Long userId);
}