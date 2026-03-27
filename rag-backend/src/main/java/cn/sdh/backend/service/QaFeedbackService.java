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
}