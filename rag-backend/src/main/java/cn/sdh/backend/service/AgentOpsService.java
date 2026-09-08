package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.mapper.AgentOpsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentOpsService {
    private final AgentOpsMapper ops;

    public record OpsReport(int days, Map<String, Object> overview,
                            List<Map<String, Object>> tools, List<Map<String, Object>> trend) {}

    public OpsReport report(int days) {
        if (days < 1 || days > 365) throw new BusinessException(400, "统计范围必须在 1-365 天之间");
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new BusinessException(401, "请先登录");
        LocalDateTime since = LocalDateTime.now().minusDays(days - 1L).toLocalDate().atStartOfDay();
        Map<String, Object> overview = new LinkedHashMap<>();
        merge(overview, ops.runOverview(actor, since));
        merge(overview, ops.actionOverview(actor, since));
        merge(overview, ops.feedbackOverview(actor, since));
        return new OpsReport(days, overview, ops.toolMetrics(actor, since), ops.dailyTrend(actor, since));
    }

    private void merge(Map<String, Object> target, Map<String, Object> values) {
        if (values != null) target.putAll(values);
    }
}
