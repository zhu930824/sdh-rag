package cn.sdh.backend.service;

import cn.sdh.backend.dto.HotwordStatsResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface HotwordService {

    HotwordStatsResponse getStats(LocalDate startDate, LocalDate endDate);

    List<Map<String, Object>> getRanking(LocalDate startDate, LocalDate endDate, Integer limit);

    List<Map<String, Object>> getTrend(String word, LocalDate startDate, LocalDate endDate);

    List<Map<String, Object>> getList(Integer page, Integer pageSize, String keyword, 
                                       LocalDate startDate, LocalDate endDate);

    void recordWord(String word, Long userId, String sessionId);

    void recordWords(List<String> words, Long userId, String sessionId);
}