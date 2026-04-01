package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.StatsDaily;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.StatsDailyMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.service.StatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl extends ServiceImpl<StatsDailyMapper, StatsDaily> implements StatsService {

    private final KnowledgeDocumentMapper documentMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final UserMapper userMapper;

    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("totalDocuments", documentMapper.selectCount(null));
        result.put("totalUsers", userMapper.selectCount(null));
        result.put("totalChats", chatHistoryMapper.selectCount(null));
        
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long todayChats = chatHistoryMapper.selectCount(
            new LambdaQueryWrapper<ChatHistory>()
                .ge(ChatHistory::getCreateTime, todayStart)
        );
        result.put("todayChats", todayChats);
        
        return result;
    }

    @Override
    public List<Map<String, Object>> getChatTrend(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> trend = new ArrayList<>();
        
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", current.toString());
            
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();
            
            Long count = chatHistoryMapper.selectCount(
                new LambdaQueryWrapper<ChatHistory>()
                    .ge(ChatHistory::getCreateTime, dayStart)
                    .lt(ChatHistory::getCreateTime, dayEnd)
            );
            dayData.put("count", count);
            
            trend.add(dayData);
            current = current.plusDays(1);
        }
        
        return trend;
    }

    @Override
    public Map<String, Object> getApiCostStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("totalTokens", 1000000);
        result.put("totalCost", 123.45);
        result.put("todayTokens", 50000);
        result.put("todayCost", 12.34);
        
        List<Map<String, Object>> modelStats = new ArrayList<>();
        result.put("modelStats", modelStats);
        
        return result;
    }

    @Override
    public void recordApiCall(Long userId, Long modelId, String apiType, int inputTokens, int outputTokens, double cost, long duration, boolean success, String errorMsg) {
        // TODO: 记录API调用日志
    }

    @Override
    public void incrementDailyStat(String statType, String metricName, long value) {
        // TODO: 更新每日统计
    }
}