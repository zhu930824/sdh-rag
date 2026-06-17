package cn.sdh.backend.config;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 文档新鲜度计算工具
 */
public class FreshnessConfig {

    /** 半年内满分 */
    private static final long FULL_SCORE_MONTHS = 6L;
    /** 线性衰减到 0.5 的月数 */
    private static final long HALF_SCORE_MONTHS = 12L;
    /** 线性衰减到 0.3 的月数 */
    private static final long LOW_SCORE_MONTHS = 24L;

    /** 新鲜度满分 */
    public static final double MAX_FRESHNESS = 1.0;
    /** 新鲜度保底分 */
    public static final double MIN_FRESHNESS = 0.3;
    /** 冷热分离阈值 */
    public static final double HOT_COLD_THRESHOLD = 0.5;

    /**
     * 根据文档日期计算新鲜度评分
     * @param docDate 文档日期
     * @return 0-1 之间的新鲜度评分
     */
    public static double calculateFreshness(LocalDate docDate) {
        if (docDate == null) {
            return 0.5; // 无日期默认中等
        }
        long months = ChronoUnit.MONTHS.between(docDate, LocalDate.now());

        if (months <= FULL_SCORE_MONTHS) {
            return MAX_FRESHNESS; // 6个月内满分
        }
        if (months <= HALF_SCORE_MONTHS) {
            // 6-12月线性衰减到 0.5
            return MAX_FRESHNESS - 0.5 * (months - FULL_SCORE_MONTHS) / (double)(HALF_SCORE_MONTHS - FULL_SCORE_MONTHS);
        }
        if (months <= LOW_SCORE_MONTHS) {
            // 12-24月线性衰减到 0.3
            return 0.5 - 0.2 * (months - HALF_SCORE_MONTHS) / (double)(LOW_SCORE_MONTHS - HALF_SCORE_MONTHS);
        }
        return MIN_FRESHNESS; // 超过2年保底 0.3
    }

    /**
     * 判断文档是否属于热库
     */
    public static boolean isHotDocument(Double freshnessScore) {
        return freshnessScore != null && freshnessScore >= HOT_COLD_THRESHOLD;
    }
}
