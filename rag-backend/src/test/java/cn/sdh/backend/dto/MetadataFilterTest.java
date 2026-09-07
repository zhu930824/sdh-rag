package cn.sdh.backend.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataFilterTest {

    @Test
    void shouldMapApiFieldsToSearchFieldsAndProtectReservedDateKeys() {
        MetadataFilter filter = MetadataFilter.builder()
            .department(" 研发部 ")
            .docType("policy")
            .dateFrom(LocalDate.of(2026, 1, 1))
            .dateTo(LocalDate.of(2026, 6, 30))
            .customFilters(Map.of(
                "language", "zh-CN",
                "doc_date_gte", "1900-01-01"))
            .build();

        assertEquals(Map.of(
            "department", "研发部",
            "doc_type", "policy",
            "doc_date_gte", "2026-01-01",
            "doc_date_lte", "2026-06-30",
            "language", "zh-CN"
        ), filter.toSearchFilters());
    }
}
