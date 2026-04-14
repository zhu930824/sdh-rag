package cn.sdh.backend.service.impl;

import cn.sdh.backend.dto.HotwordStatsResponse;
import cn.sdh.backend.entity.HotwordRecord;
import cn.sdh.backend.mapper.HotwordRecordMapper;
import cn.sdh.backend.service.HotwordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotwordServiceImpl implements HotwordService {

    private final HotwordRecordMapper hotwordRecordMapper;

    @Override
    public HotwordStatsResponse getStats(LocalDate startDate, LocalDate endDate) {
        // 当前时间范围的统计
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate);

        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);

        long totalCount = records.stream().mapToInt(HotwordRecord::getCount).sum();
        long uniqueWords = records.stream().map(HotwordRecord::getWord).distinct().count();

        // 计算环比增长（与上一周期对比）
        long daysDiff = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevStartDate = startDate.minusDays(daysDiff);
        LocalDate prevEndDate = startDate.minusDays(1);

        LambdaQueryWrapper<HotwordRecord> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.ge(HotwordRecord::getQueryDate, prevStartDate)
                  .le(HotwordRecord::getQueryDate, prevEndDate);
        List<HotwordRecord> prevRecords = hotwordRecordMapper.selectList(prevWrapper);
        long prevTotalCount = prevRecords.stream().mapToInt(HotwordRecord::getCount).sum();

        double growthRate = 0.0;
        if (prevTotalCount > 0) {
            growthRate = Math.round((totalCount - prevTotalCount) * 1000.0 / prevTotalCount) / 10.0;
        } else if (totalCount > 0) {
            growthRate = 100.0;
        }

        HotwordStatsResponse response = new HotwordStatsResponse();
        response.setTotalQueries(totalCount);
        response.setUniqueWords(uniqueWords);
        response.setAvgQueries(uniqueWords > 0 ? Math.round(totalCount * 100.0 / uniqueWords) / 100.0 : 0.0);
        response.setGrowthRate(growthRate);

        return response;
    }

    @Override
    public List<Map<String, Object>> getRanking(LocalDate startDate, LocalDate endDate, Integer limit) {
        // 当前时间范围
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate);

        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);

        Map<String, Integer> wordCount = records.stream()
            .collect(Collectors.groupingBy(HotwordRecord::getWord, Collectors.summingInt(HotwordRecord::getCount)));

        int total = wordCount.values().stream().mapToInt(Integer::intValue).sum();

        // 上一周期数据（用于计算趋势）
        long daysDiff = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevStartDate = startDate.minusDays(daysDiff);
        LocalDate prevEndDate = startDate.minusDays(1);

        LambdaQueryWrapper<HotwordRecord> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.ge(HotwordRecord::getQueryDate, prevStartDate)
                  .le(HotwordRecord::getQueryDate, prevEndDate);
        List<HotwordRecord> prevRecords = hotwordRecordMapper.selectList(prevWrapper);

        Map<String, Integer> prevWordCount = prevRecords.stream()
            .collect(Collectors.groupingBy(HotwordRecord::getWord, Collectors.summingInt(HotwordRecord::getCount)));

        // 判断是否是新词（上一周期不存在）
        Set<String> prevWords = prevWordCount.keySet();

        List<Map<String, Object>> ranking = new ArrayList<>();
        wordCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .forEach(entry -> {
                Map<String, Object> item = new HashMap<>();
                String word = entry.getKey();
                int count = entry.getValue();

                item.put("word", word);
                item.put("count", count);
                item.put("percent", total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0);

                // 计算趋势
                Integer prevCount = prevWordCount.get(word);
                if (prevCount == null || prevCount == 0) {
                    item.put("trend", 100);
                } else {
                    item.put("trend", Math.round((count - prevCount) * 100.0 / prevCount));
                }

                // 是否是新词
                item.put("isNew", !prevWords.contains(word));

                ranking.add(item);
            });

        return ranking;
    }

    @Override
    public List<Map<String, Object>> getTrend(String word, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotwordRecord::getWord, word)
               .ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate);

        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);

        // 转换为日期->count的Map
        Map<LocalDate, Integer> dateCountMap = records.stream()
            .collect(Collectors.toMap(HotwordRecord::getQueryDate, HotwordRecord::getCount, (a, b) -> a + b));

        // 填充所有日期
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", current.toString());
            item.put("count", dateCountMap.getOrDefault(current, 0));
            trend.add(item);
            current = current.plusDays(1);
        }

        return trend;
    }

    @Override
    public Map<String, Object> getList(Integer page, Integer pageSize, String keyword, LocalDate startDate, LocalDate endDate) {
        // 如果没有指定时间范围，默认近30天
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        LambdaQueryWrapper<HotwordRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(HotwordRecord::getQueryDate, startDate)
               .le(HotwordRecord::getQueryDate, endDate);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(HotwordRecord::getWord, keyword);
        }

        List<HotwordRecord> records = hotwordRecordMapper.selectList(wrapper);

        // 聚合统计
        Map<String, Integer> wordCount = records.stream()
            .collect(Collectors.groupingBy(
                HotwordRecord::getWord,
                Collectors.summingInt(HotwordRecord::getCount)
            ));

        int total = wordCount.values().stream().mapToInt(Integer::intValue).sum();

        // 上一周期数据（用于判断是否新词）
        long daysDiff = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevStartDate = startDate.minusDays(daysDiff);
        LocalDate prevEndDate = startDate.minusDays(1);

        LambdaQueryWrapper<HotwordRecord> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.ge(HotwordRecord::getQueryDate, prevStartDate)
                  .le(HotwordRecord::getQueryDate, prevEndDate);
        List<HotwordRecord> prevRecords = hotwordRecordMapper.selectList(prevWrapper);

        Set<String> prevWords = prevRecords.stream()
            .map(HotwordRecord::getWord)
            .collect(Collectors.toSet());

        // 排序并分页
        List<Map<String, Object>> list = new ArrayList<>();
        wordCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .skip((long) (page - 1) * pageSize)
            .limit(pageSize)
            .forEach(entry -> {
                Map<String, Object> item = new HashMap<>();
                String word = entry.getKey();
                int count = entry.getValue();

                item.put("word", word);
                item.put("count", count);
                item.put("percent", total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0);
                item.put("isNew", !prevWords.contains(word));

                list.add(item);
            });

        // 返回分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", wordCount.size());
        result.put("page", page);
        result.put("pageSize", pageSize);

        return result;
    }

    @Override
    public void recordWord(String word, Long userId, String sessionId) {
        if (!StringUtils.hasText(word) || word.trim().length() < 2) {
            return;
        }

        word = word.trim().toLowerCase();

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
        if (words == null || words.isEmpty()) {
            return;
        }
        words.forEach(word -> recordWord(word, userId, sessionId));
    }
}
