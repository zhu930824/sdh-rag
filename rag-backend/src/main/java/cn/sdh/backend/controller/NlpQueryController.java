package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/nlp-query")
@RequiredArgsConstructor
public class NlpQueryController {

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "user", "knowledge_document", "chat_history", 
        "sensitive_word", "operation_log", "hotword_record",
        "model_config", "workflow", "embed_config"
    );

    @PostMapping
    public Result<Map<String, Object>> query(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            return Result.error("查询内容不能为空");
        }
        
        // TODO: 实际实现需要调用LLM将自然语言转换为SQL
        // 这里返回安全的模拟结果，不执行实际SQL
        String safeSql = generateSafeSqlPreview(question);
        
        List<Map<String, Object>> mockResults = new ArrayList<>();
        
        return Result.success(Map.of(
            "sql", safeSql,
            "results", mockResults,
            "message", "查询已生成，请确认后执行"
        ));
    }
    
    private String generateSafeSqlPreview(String question) {
        // 返回安全的SQL预览，不直接拼接用户输入
        // 实际实现应使用LLM生成SQL，并进行安全验证
        return "-- SQL将由AI根据以下问题生成:\n-- " + sanitizeInput(question) + "\n\n-- 示例SQL结构:\n-- SELECT * FROM [表名] WHERE [条件] LIMIT 100";
    }
    
    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("['\"\\\\;--]", "");
    }

    @GetMapping("/tables")
    public Result<List<Map<String, String>>> getTables() {
        List<Map<String, String>> tables = new ArrayList<>();
        
        tables.add(Map.of("name", "user", "comment", "用户表"));
        tables.add(Map.of("name", "knowledge_document", "comment", "知识库文档表"));
        tables.add(Map.of("name", "chat_history", "comment", "聊天历史表"));
        tables.add(Map.of("name", "sensitive_word", "comment", "敏感词表"));
        tables.add(Map.of("name", "operation_log", "comment", "操作日志表"));
        tables.add(Map.of("name", "hotword_record", "comment", "热点词记录表"));
        tables.add(Map.of("name", "model_config", "comment", "模型配置表"));
        tables.add(Map.of("name", "workflow", "comment", "工作流表"));
        tables.add(Map.of("name", "embed_config", "comment", "嵌入配置表"));
        
        return Result.success(tables);
    }

    @GetMapping("/schema/{tableName}")
    public Result<List<Map<String, String>>> getSchema(@PathVariable String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            return Result.error("不允许访问该表");
        }
        
        List<Map<String, String>> columns = getTableSchema(tableName);
        return Result.success(columns);
    }
    
    private List<Map<String, String>> getTableSchema(String tableName) {
        List<Map<String, String>> columns = new ArrayList<>();
        Map<String, List<Map<String, String>>> schemaMap = new HashMap<>();
        
        schemaMap.put("user", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "用户ID"),
            Map.of("name", "username", "type", "VARCHAR(50)", "comment", "用户名"),
            Map.of("name", "nickname", "type", "VARCHAR(50)", "comment", "昵称"),
            Map.of("name", "status", "type", "TINYINT", "comment", "状态"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
        
        schemaMap.put("knowledge_document", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "文档ID"),
            Map.of("name", "title", "type", "VARCHAR(255)", "comment", "文档标题"),
            Map.of("name", "content", "type", "TEXT", "comment", "文档内容"),
            Map.of("name", "file_type", "type", "VARCHAR(20)", "comment", "文件类型"),
            Map.of("name", "status", "type", "TINYINT", "comment", "状态"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
        
        schemaMap.put("chat_history", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "记录ID"),
            Map.of("name", "user_id", "type", "BIGINT", "comment", "用户ID"),
            Map.of("name", "session_id", "type", "VARCHAR(100)", "comment", "会话ID"),
            Map.of("name", "question", "type", "TEXT", "comment", "问题"),
            Map.of("name", "answer", "type", "TEXT", "comment", "回答"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
        
        schemaMap.put("sensitive_word", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "敏感词ID"),
            Map.of("name", "word", "type", "VARCHAR(100)", "comment", "敏感词"),
            Map.of("name", "category", "type", "VARCHAR(50)", "comment", "分类"),
            Map.of("name", "status", "type", "TINYINT", "comment", "状态"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
        
        schemaMap.put("operation_log", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "日志ID"),
            Map.of("name", "user_id", "type", "BIGINT", "comment", "用户ID"),
            Map.of("name", "type", "type", "VARCHAR(20)", "comment", "操作类型"),
            Map.of("name", "content", "type", "VARCHAR(500)", "comment", "操作内容"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
        
        schemaMap.put("hotword_record", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "记录ID"),
            Map.of("name", "word", "type", "VARCHAR(100)", "comment", "关键词"),
            Map.of("name", "count", "type", "INT", "comment", "查询次数"),
            Map.of("name", "query_date", "type", "DATE", "comment", "查询日期")
        ));
        
        schemaMap.put("model_config", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "配置ID"),
            Map.of("name", "name", "type", "VARCHAR(100)", "comment", "模型名称"),
            Map.of("name", "provider", "type", "VARCHAR(50)", "comment", "提供商"),
            Map.of("name", "model_type", "type", "VARCHAR(50)", "comment", "模型类型"),
            Map.of("name", "model_id", "type", "VARCHAR(100)", "comment", "模型ID"),
            Map.of("name", "status", "type", "TINYINT", "comment", "状态")
        ));
        
        schemaMap.put("workflow", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "工作流ID"),
            Map.of("name", "name", "type", "VARCHAR(100)", "comment", "工作流名称"),
            Map.of("name", "description", "type", "VARCHAR(500)", "comment", "描述"),
            Map.of("name", "status", "type", "TINYINT", "comment", "状态"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
        
        schemaMap.put("embed_config", List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "配置ID"),
            Map.of("name", "name", "type", "VARCHAR(100)", "comment", "配置名称"),
            Map.of("name", "theme", "type", "VARCHAR(20)", "comment", "主题"),
            Map.of("name", "position", "type", "VARCHAR(20)", "comment", "位置"),
            Map.of("name", "status", "type", "TINYINT", "comment", "状态")
        ));
        
        return schemaMap.getOrDefault(tableName, List.of(
            Map.of("name", "id", "type", "BIGINT", "comment", "ID"),
            Map.of("name", "create_time", "type", "DATETIME", "comment", "创建时间")
        ));
    }
}