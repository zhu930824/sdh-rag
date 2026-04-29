package cn.sdh.backend.dto;

import lombok.Data;

@Data
public class IntentRouterRequest {
    /**
     * 用户查询
     */
    private String query;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private String sessionId;

    public IntentRouterRequest() {
    }

    public IntentRouterRequest(String query, Long userId, String sessionId) {
        this.query = query;
        this.userId = userId;
        this.sessionId = sessionId;
    }
}