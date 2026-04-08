package cn.sdh.backend.entity;

import lombok.Data;

@Data
public class DashboardStats {
    private Long knowledgeCount;
    private Long documentCount;
    private Long chatCount;
    private Long userCount;
}