package cn.sdh.backend.dto;

import lombok.Data;

@Data
public class DashboardStatsResponse {
    private Long knowledgeCount;
    private Long documentCount;
    private Long chatCount;
    private Long userCount;
}