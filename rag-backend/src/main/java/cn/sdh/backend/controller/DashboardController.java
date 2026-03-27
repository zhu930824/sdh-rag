package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
class DashboardStats {
    private Long knowledgeCount;
    private Long documentCount;
    private Long chatCount;
    private Long userCount;
}

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final KnowledgeDocumentMapper documentMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final UserMapper userMapper;

    @GetMapping("/stats")
    public Result<DashboardStats> getStats() {
        DashboardStats stats = new DashboardStats();
        
        stats.setDocumentCount(documentMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.setChatCount(chatHistoryMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.setUserCount(userMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.setKnowledgeCount(stats.getDocumentCount());
        
        return Result.success(stats);
    }
}