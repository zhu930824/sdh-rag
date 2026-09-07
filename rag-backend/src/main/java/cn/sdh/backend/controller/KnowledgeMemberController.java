package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.KnowledgeBaseMember;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.KnowledgeBaseMemberMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.service.KnowledgeAccessService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-base/{id}")
@RequiredArgsConstructor
public class KnowledgeMemberController {
    private final KnowledgeAccessService access;
    private final KnowledgeBaseMemberMapper members;
    private final UserMapper users;

    public record GrantRequest(String username, String permission) {}
    public record MemberView(Long userId, String username, String permission) {}

    @GetMapping("/access")
    public Result<Map<String, String>> getAccess(@PathVariable Long id) {
        Long actor = UserContext.getCurrentUserId();
        var base = access.requireRead(id, actor);
        return Result.success(Map.of("role", access.role(base, actor)));
    }

    @GetMapping("/members")
    public Result<List<MemberView>> list(@PathVariable Long id) {
        access.requireOwner(id, UserContext.getCurrentUserId());
        return Result.success(members.selectList(new LambdaQueryWrapper<KnowledgeBaseMember>()
            .eq(KnowledgeBaseMember::getKnowledgeId, id)).stream().map(member -> {
                User user = users.selectById(member.getUserId());
                return new MemberView(member.getUserId(), user == null ? "已删除用户" : user.getUsername(), member.getPermission());
            }).toList());
    }

    @PostMapping("/members")
    public Result<Void> grant(@PathVariable Long id, @RequestBody GrantRequest request) {
        Long actor = UserContext.getCurrentUserId();
        var base = access.requireOwner(id, actor);
        if (request.username() == null || request.username().isBlank()
            || !("reader".equals(request.permission()) || "editor".equals(request.permission()))) {
            throw new BusinessException(400, "请填写用户名并选择阅读者或编辑者");
        }
        User user = users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username().trim()));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(400, "用户不存在或已停用");
        }
        if (user.getId().equals(base.getUserId())) throw new BusinessException(400, "不能修改所有者权限");
        members.grant(id, user.getId(), request.permission(), actor);
        return Result.success("成员权限已保存", null);
    }

    @DeleteMapping("/members/{userId}")
    public Result<Void> revoke(@PathVariable Long id, @PathVariable Long userId) {
        access.requireOwner(id, UserContext.getCurrentUserId());
        members.delete(new LambdaQueryWrapper<KnowledgeBaseMember>()
            .eq(KnowledgeBaseMember::getKnowledgeId, id).eq(KnowledgeBaseMember::getUserId, userId));
        return Result.success("成员权限已移除", null);
    }
}
