package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 问答检索的文档元数据过滤条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataFilter {

    private String department;

    private String docType;

    /** ISO-8601 日期，例如 2026-01-01。 */
    private LocalDate dateFrom;

    /** ISO-8601 日期，例如 2026-12-31。 */
    private LocalDate dateTo;

    /** 预留的安全自定义过滤条件。 */
    private Map<String, Object> customFilters;

    /**
     * 转换为检索层使用的字段名，避免把前端字段约定泄漏到 ES 查询实现。
     */
    public Map<String, Object> toSearchFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (customFilters != null) {
            customFilters.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null
                        && !"doc_date_gte".equals(key) && !"doc_date_lte".equals(key)) {
                    filters.put(key, value);
                }
            });
        }
        if (StringUtils.hasText(department)) {
            filters.put("department", department.trim());
        }
        if (StringUtils.hasText(docType)) {
            filters.put("doc_type", docType.trim());
        }
        if (dateFrom != null) {
            filters.put("doc_date_gte", dateFrom.toString());
        }
        if (dateTo != null) {
            filters.put("doc_date_lte", dateTo.toString());
        }
        return filters;
    }

    /** 向后兼容原有调用名称。 */
    public Map<String, Object> toEsFilters() {
        return toSearchFilters();
    }

    public boolean hasDateRange() {
        return dateFrom != null || dateTo != null;
    }
}
