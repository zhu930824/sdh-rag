package cn.sdh.backend.service;

import cn.sdh.backend.dto.IntentRouterRequest;
import cn.sdh.backend.dto.IntentRouterResponse;

public interface IntentRouterService {
    /**
     * 分析用户查询意图，决定是否需要检索知识库
     *
     * @param request 路由请求
     * @return 路由决策结果
     */
    IntentRouterResponse route(IntentRouterRequest request);

    /**
     * 简化方法：直接传入query和userId
     *
     * @param query  用户查询
     * @param userId 用户ID
     * @return 路由决策结果
     */
    IntentRouterResponse route(String query, Long userId);
}
