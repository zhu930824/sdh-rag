package cn.sdh.backend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HotwordStatsResponse {

    private Long totalQueries;

    private Long uniqueWords;

    private Double avgQueries;

    private Double growthRate;

    private List<WordRankItem> topWords;

    private List<TrendItem> trend;

    @Data
    public static class WordRankItem {
        private String word;
        private Integer count;
        private Double percent;
        private Integer trend;
        private Boolean isNew;
    }

    @Data
    public static class TrendItem {
        private String date;
        private Integer count;
    }
}