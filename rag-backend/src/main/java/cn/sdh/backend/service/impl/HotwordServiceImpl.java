package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.HotwordRecord;
import cn.sdh.backend.mapper.HotwordRecordMapper;
import cn.sdh.backend.service.HotwordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotwordServiceImpl implements HotwordService {

    private final HotwordRecordMapper hotwordRecordMapper;

    @Override
    public Map<String, Object> getStats(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate);
        
        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);
        
        long totalCount = records.stream().mapToInt(HotwordRecord::getCount).sum();
        long uniqueWords = records.stream().map(HotwordRecord::getWord).distinct().count();
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalQueries", totalCount);
        result.put("uniqueWords", (int) uniqueWords);
        result.put("avgQueries", uniqueWords > 0 ? Math.round(totalCount * 100.0 / uniqueWords) : 0.0);
        
        return result;
    }

    @Override
    public List<Map<String, Object>> getRanking(LocalDate startDate, LocalDate endDate, Integer limit) {
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate);
        
        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);
        
        Map<String, Integer> wordCount = records.stream()
            .collect(Collectors.groupingBy(HotwordRecord::getWord, Collectors.summingInt(HotwordRecord::getCount)));
        
        int total = wordCount.values().stream().mapToInt(Integer::intValue).sum();
        
        List<Map<String, Object>> ranking = new ArrayList<>();
        wordCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .forEach(entry -> {
                Map<String, Object> item = new HashMap<>();
                item.put("word", entry.getKey());
                item.put("count", entry.getValue());
                item.put("percent", total > 0 ? Math.round(entry.getValue() * 1000.0 / total) / 10.0 : 0);
                ranking.add(item);
            });
        
        return ranking;
    }

    @Override
    public List<Map<String, Object>> getTrend(String word, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotwordRecord::getWord, word)
               .ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate)
               .orderByAsc(HotwordRecord::getQueryDate);
        
        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);
        
        List<Map<String, Object>> trend = new ArrayList<>();
        for (HotwordRecord record : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", record.getQueryDate().toString());
            item.put("count", record.getCount());
            trend.add(item);
        }
        
        return trend;
    }

    @Override
    public List<Map<String, Object>> getList(Integer page, Integer pageSize, String keyword, LocalDate startDate, LocalDate endDate) {
        return getRanking(
            startDate != null ? startDate : LocalDate.now().minusDays(30), 
            endDate != null ? endDate : LocalDate.now(), 
            pageSize != null ? pageSize : 10
        );
    }

    @Override
    public void recordWord(String word, Long userId, String sessionId) {
        if (!StringUtils.hasText(word)) {
            return;
        }
        
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotwordRecord::getWord, word)
               .eq(HotwordRecord::getQueryDate, today);
        
        HotwordRecord record = hotwordRecordMapper.selectOne(wrapper);
        
        if (record != null) {
            record.setCount(record.getCount() + 1);
            hotwordRecordMapper.updateById(record);
        } else {
            record = new HotwordRecord();
            record.setWord(word);
            record.setCount(1);
            record.setQueryDate(today);
            record.setUserId(userId);
            record.setSessionId(sessionId);
            hotwordRecordMapper.insert(record);
        }
    }

    @Override
    public void recordWords(List<String> words, Long userId, String sessionId) {
        for (String word : words) {
            recordWord(word, userId, sessionId);
        }
    }
}