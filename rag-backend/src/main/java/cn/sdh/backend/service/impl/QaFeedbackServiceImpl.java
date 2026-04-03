package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.QaFeedback;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.mapper.QaFeedbackMapper;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.service.QaFeedbackService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QaFeedbackServiceImpl extends ServiceImpl<QaFeedbackMapper, QaFeedback> implements QaFeedbackService {

    private final QaFeedbackMapper qaFeedbackMapper;
    private final ChatHistoryMapper chatHistoryMapper;

    @Override
    public IPage<QaFeedback> getPage(Integer page, Integer pageSize, Integer rating, Integer status) {
        Page<QaFeedback> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<QaFeedback> wrapper = new LambdaQueryWrapper<>();
        
        if (rating != null) {
            wrapper.eq(QaFeedback::getRating, rating);
        }
        if (status != null) {
            wrapper.eq(QaFeedback::getStatus, status);
        }
        wrapper.orderByDesc(QaFeedback::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaFeedback createFeedback(QaFeedback feedback) {
        // 查找用户是否已经对该聊天记录评价过
        LambdaQueryWrapper<QaFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaFeedback::getChatHistoryId, feedback.getChatHistoryId())
                .eq(QaFeedback::getUserId, feedback.getUserId());
        QaFeedback existingFeedback = getOne(wrapper);

        if (existingFeedback != null) {
            // 已存在评价，更新评价状态
            existingFeedback.setRating(feedback.getRating());
            existingFeedback.setFeedbackType(feedback.getFeedbackType());
            existingFeedback.setComment(feedback.getComment());
            existingFeedback.setCorrectAnswer(feedback.getCorrectAnswer());
            existingFeedback.setStatus(0); // 重置状态为未处理
            existingFeedback.setCreateTime(LocalDateTime.now()); // 更新时间
            updateById(existingFeedback);
            return existingFeedback;
        }

        // 不存在评价，创建新记录
        feedback.setStatus(0);
        feedback.setCreateTime(LocalDateTime.now());
        save(feedback);
        return feedback;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleFeedback(Long id, Long handlerId, Integer status) {
        QaFeedback feedback = getById(id);
        if (feedback != null) {
            feedback.setStatus(status);
            feedback.setHandledBy(handlerId);
            feedback.setHandleTime(LocalDateTime.now());
            updateById(feedback);
        }
    }

    @Override
    public Map<String, Object> getFeedbackStats(Long chatHistoryId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("likes", qaFeedbackMapper.countLikes(chatHistoryId));
        
        LambdaQueryWrapper<QaFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaFeedback::getChatHistoryId, chatHistoryId);
        stats.put("total", count(wrapper));
        
        return stats;
    }

    @Override
    public Long getUserIdByChatHistoryId(Long chatHistoryId) {
        ChatHistory chatHistory = chatHistoryMapper.selectById(chatHistoryId);
        return chatHistory != null ? chatHistory.getUserId() : null;
    }

    @Override
    public Integer getUserRating(Long chatHistoryId, Long userId) {
        LambdaQueryWrapper<QaFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaFeedback::getChatHistoryId, chatHistoryId)
                .eq(QaFeedback::getUserId, userId);
        QaFeedback feedback = getOne(wrapper);
        return feedback != null ? feedback.getRating() : null;
    }
}