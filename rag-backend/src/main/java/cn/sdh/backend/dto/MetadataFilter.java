package cn.sdh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 元数据过滤条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataFilter {

    /** 部门 */
    private String department;

    /** 文档类型 */
    private String docType;

    /** 文档日期起始 */
    private LocalDate dateFrom;

    /** 文档日期截止 */
    private LocalDate dateTo;

    /** 自定义过滤条件 */
    private Map<String, Object> customFilters;

    /**
     * 转换为 ES term query 的 Map
     */
    public Map<String, Object> toEsFilters() {
        Map<String, Object> filters = new HashMap<>();
        if (department != null && !department.isEmpty()) {
            filters.put("department", department);
        }
        if (docType != null && !docType.isEmpty()) {
            filters.put("doc_type", docType);
        }
        if (customFilters != null) {
            filters.putAll(customFilters);
        }
        return filters;
    }

    public boolean hasDateRange() {
        return dateFrom != null || dateTo != null;
    }
}
