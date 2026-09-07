package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** Resource authorization. Actor IDs must come from authentication, never request payloads. */
@Service
@RequiredArgsConstructor
public class KnowledgeAccessService {
    private final KnowledgeBaseMapper bases;
    private final KnowledgeBaseMemberMapper members;
    private final KnowledgeDocumentMapper documents;
    private final KnowledgeDocumentRelationMapper relations;

    public String role(KnowledgeBase base, Long actor) {
        if (actor == null || base == null) return "none";
        if (actor.equals(base.getUserId())) return "owner";
        String member = members.permission(base.getId(), actor);
        if ("reader".equals(member) || "editor".equals(member)) return member;
        return Boolean.TRUE.equals(base.getIsPublic()) ? "reader" : "none";
    }

    public boolean canRead(KnowledgeBase base, Long actor) {
        return !"none".equals(role(base, actor));
    }

    public KnowledgeBase requireRead(Long id, Long actor) { return require(id, actor, false, false); }
    public KnowledgeBase requireEdit(Long id, Long actor) { return require(id, actor, true, false); }
    public KnowledgeBase requireOwner(Long id, Long actor) { return require(id, actor, true, true); }

    private KnowledgeBase require(Long id, Long actor, boolean edit, boolean owner) {
        requireActor(actor);
        KnowledgeBase base = id == null ? null : bases.selectById(id);
        String role = role(base, actor);
        if ("none".equals(role) || (edit && "reader".equals(role)) || (owner && !"owner".equals(role))) {
            throw new BusinessException(403, "无权访问或操作该知识库");
        }
        return base;
    }

    /** SQL pagination must use the same visibility policy as individual reads. */
    public void constrainReadable(LambdaQueryWrapper<KnowledgeBase> query, Long actor) {
        requireActor(actor);
        var readable = members.readableIds(actor);
        final List<Long> shared = readable == null ? List.of() : readable;
        query.and(w -> {
            w.eq(KnowledgeBase::getUserId, actor).or().eq(KnowledgeBase::getIsPublic, true);
            if (!shared.isEmpty()) w.or().in(KnowledgeBase::getId, shared);
        });
    }

    public List<Long> readableKnowledgeIds(Long actor) {
        LambdaQueryWrapper<KnowledgeBase> query = new LambdaQueryWrapper<>();
        constrainReadable(query, actor);
        query.select(KnowledgeBase::getId);
        List<KnowledgeBase> result = bases.selectList(query);
        if (result == null) return List.of();
        return result.stream().map(KnowledgeBase::getId).filter(Objects::nonNull).toList();
    }

    public KnowledgeDocument requireDocumentOwner(Long id, Long actor) {
        requireActor(actor);
        KnowledgeDocument doc = id == null ? null : documents.selectById(id);
        if (doc == null || !actor.equals(doc.getUserId())) {
            throw new BusinessException(403, "只有文档所有者可修改、删除或分享该文档");
        }
        return doc;
    }

    public KnowledgeDocument requireDocumentRead(Long id, Long actor) {
        requireActor(actor);
        KnowledgeDocument doc = id == null ? null : documents.selectById(id);
        if (doc != null) {
            if (actor.equals(doc.getUserId())) return doc;
            for (Long knowledgeId : relations.selectKnowledgeIdsByDocumentId(id)) {
                if (canRead(bases.selectById(knowledgeId), actor)) return doc;
            }
        }
        throw new BusinessException(403, "无权访问该文档");
    }

    private void requireActor(Long actor) {
        if (actor == null) throw new BusinessException(401, "请先登录");
    }
}
