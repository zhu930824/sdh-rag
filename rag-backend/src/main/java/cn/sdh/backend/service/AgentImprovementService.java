package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.AgentAction;
import cn.sdh.backend.entity.AgentActionFeedback;
import cn.sdh.backend.entity.AgentImprovementItem;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.mapper.AgentImprovementItemMapper;
import cn.sdh.backend.mapper.AgentRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AgentImprovementService {
    private final AgentImprovementItemMapper items;
    private final AgentRunMapper runs;

    public record ReviewCommand(String status, String note) {}

    public void captureFeedback(AgentAction action, AgentActionFeedback feedback) {
        if (action == null || feedback == null || !Integer.valueOf(1).equals(feedback.getReusableKnowledge())) return;
        AgentRun run = action.getRunId() == null ? null : runs.selectById(action.getRunId());
        AgentImprovementItem item = find("ACTION_FEEDBACK", feedback.getId());
        if (item == null) {
            item = base("ACTION_FEEDBACK", feedback.getId(), feedback.getUserId());
            item.setStatus("OPEN");
        }
        item.setRunId(action.getRunId());
        item.setActionId(action.getId());
        item.setKnowledgeId(run == null ? null : run.getKnowledgeId());
        item.setCategory("KNOWLEDGE_REUSE");
        item.setPriority("FAILED".equals(feedback.getOutcome()) ? "HIGH" : "PARTIAL".equals(feedback.getOutcome()) ? "MEDIUM" : "LOW");
        item.setTitle(cap("沉淀业务处理经验：" + action.getToolName(), 200));
        item.setDetail(cap(buildFeedbackDetail(action, feedback), 8000));
        item.setUpdatedAt(LocalDateTime.now());
        if (item.getId() == null) items.insert(item); else items.updateById(item);
    }

    public List<AgentImprovementItem> list(String status) {
        Long actor = requireActor();
        LambdaQueryWrapper<AgentImprovementItem> query = new LambdaQueryWrapper<AgentImprovementItem>()
                .eq(AgentImprovementItem::getCreatedBy, actor);
        if (status != null && !status.isBlank()) query.eq(AgentImprovementItem::getStatus, normalizeStatus(status));
        return items.selectList(query.last("ORDER BY FIELD(status,'OPEN','RESOLVED','IGNORED'), " +
                "FIELD(priority,'HIGH','MEDIUM','LOW'), created_at DESC LIMIT 300"));
    }

    public AgentImprovementItem review(Long id, ReviewCommand command) {
        Long actor = requireActor();
        AgentImprovementItem item = items.selectById(id);
        if (item == null || !actor.equals(item.getCreatedBy())) throw new BusinessException(404, "改进项不存在");
        if (command == null) throw new BusinessException(400, "处理内容不能为空");
        item.setStatus(normalizeStatus(command.status()));
        item.setReviewNote(capNullable(command.note(), 1000));
        item.setReviewedBy(actor);
        item.setReviewedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        items.updateById(item);
        return item;
    }

    public AgentImprovementItem requireOwned(Long id, Long actor) {
        AgentImprovementItem item = id == null ? null : items.selectById(id);
        if (item == null || actor == null || !actor.equals(item.getCreatedBy())) {
            throw new BusinessException(404, "改进项不存在");
        }
        return item;
    }

    public void markPublished(Long id, Long actor, Long draftId) {
        if (id == null) return;
        AgentImprovementItem item = requireOwned(id, actor);
        item.setStatus("RESOLVED");
        item.setReviewNote(cap("已通过知识草稿 #" + draftId + " 发布入库", 1000));
        item.setReviewedBy(actor);
        item.setReviewedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        items.updateById(item);
    }

    private AgentImprovementItem find(String sourceType, Long sourceId) {
        return items.selectOne(new LambdaQueryWrapper<AgentImprovementItem>()
                .eq(AgentImprovementItem::getSourceType, sourceType)
                .eq(AgentImprovementItem::getSourceId, sourceId));
    }

    private AgentImprovementItem base(String sourceType, Long sourceId, Long actor) {
        AgentImprovementItem item = new AgentImprovementItem();
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setCreatedBy(actor);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private String buildFeedbackDetail(AgentAction action, AgentActionFeedback feedback) {
        return "业务结果=" + feedback.getOutcome() + "\n反馈=" + value(feedback.getComment()) +
                "\n请求参数=" + value(action.getRequestJson()) + "\n工具结果=" + value(action.getResultJson());
    }

    private String normalizeStatus(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OPEN", "RESOLVED", "IGNORED").contains(result)) throw new BusinessException(400, "改进项状态不正确");
        return result;
    }

    private Long requireActor() {
        Long actor = UserContext.getCurrentUserId();
        if (actor == null) throw new BusinessException(401, "请先登录");
        return actor;
    }

    private String cap(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
    private String capNullable(String value, int max) { return value == null || value.isBlank() ? null : cap(value.trim(), max); }
    private String value(String value) { return value == null || value.isBlank() ? "-" : value; }
}
