package cn.sdh.backend.service;

import java.util.*;

/** Pure classical IR metrics. IDs are documents and relevance grades are 0..3. */
public final class RagEvaluationMetrics {
    private RagEvaluationMetrics() {}

    public record Result(double recall, double precision, double reciprocalRank,
                         double ndcg, double averagePrecision, boolean hit, Integer firstRank) {}

    public static Result calculate(List<Long> retrieved, Map<Long, Integer> judgments, int requestedK) {
        int k = Math.max(1, requestedK);
        List<Long> ranked = retrieved == null ? List.of() : retrieved.stream().limit(k).toList();
        Map<Long, Integer> relevant = new LinkedHashMap<>();
        if (judgments != null) judgments.forEach((id, grade) -> {
            if (id != null && grade != null && grade > 0) relevant.put(id, Math.min(3, grade));
        });
        if (relevant.isEmpty()) return new Result(0, 0, 0, 0, 0, false, null);

        int hits = 0;
        double apSum = 0;
        double dcg = 0;
        Integer firstRank = null;
        Set<Long> matched = new HashSet<>();
        for (int i = 0; i < ranked.size(); i++) {
            Long documentId = ranked.get(i);
            int grade = documentId != null && matched.add(documentId)
                    ? relevant.getOrDefault(documentId, 0) : 0;
            if (grade > 0) {
                hits++;
                if (firstRank == null) firstRank = i + 1;
                apSum += (double) hits / (i + 1);
                dcg += grade / log2(i + 2);
            }
        }
        List<Integer> ideal = relevant.values().stream().sorted(Comparator.reverseOrder()).limit(k).toList();
        double idcg = 0;
        for (int i = 0; i < ideal.size(); i++) idcg += ideal.get(i) / log2(i + 2);
        return new Result(
                (double) hits / relevant.size(),
                (double) hits / k,
                firstRank == null ? 0 : 1.0 / firstRank,
                idcg == 0 ? 0 : dcg / idcg,
                apSum / relevant.size(),
                firstRank != null,
                firstRank);
    }

    private static double log2(double value) { return Math.log(value) / Math.log(2); }
}
