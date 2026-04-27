# Memory System Integration Guide

## 后端集成状态

记忆系统的后端组件已完整实现：
- ✅ 核心类型和实体 (`memory/core/`)
- ✅ 配置类 (`MemoryConfig`)
- ✅ 五层记忆实现 (`memory/layer/`)
- ✅ 记忆提取器 (`memory/extractor/`)
- ✅ 语义边界检测 (`memory/boundary/`)
- ✅ 编排器和组装器 (`memory/orchestrator/`)
- ✅ MemoryController REST API
- ✅ MemoryAbstract 实体和 Mapper
- ✅ 数据库迁移脚本 V13

### ChatService 集成

由于后端 Java 文件被加密，以下是集成代码片段供参考：

#### Step 1: 在 ChatService 中注入 MemoryOrchestrator

```java
// 在 ChatServiceImpl 中添加
@Autowired
private MemoryOrchestrator memoryOrchestrator;
```

#### Step 2: 在 AskRequest 中添加 memoryEnabled 字段

```java
public class AskRequest {
    private String question;
    private String sessionId;
    private Long knowledgeId;
    private Long modelId;
    
    /** 是否启用记忆增强 */
    private Boolean memoryEnabled = true;
}
```

#### Step 3: 在 ask 方法中集成记忆

```java
public Flux<StreamEvent> ask(AskRequest request) {
    Long userId = UserContext.getCurrentUserId();
    String sessionId = request.getSessionId();
    String question = request.getQuestion();

    // 1. 组装记忆上下文（仅当 memoryEnabled 为 true）
    MemoryContext memoryContext = null;
    if (request.getMemoryEnabled() == null || request.getMemoryEnabled()) {
        memoryContext = memoryOrchestrator.assembleContext(userId, sessionId, question);
    }

    // 2. 构建系统提示词
    StringBuilder systemPrompt = new StringBuilder();
    if (memoryContext != null && memoryContext.hasMemory()) {
        systemPrompt.append(memoryContext.buildSystemPrompt());
    }

    // 3. 调用 LLM（使用现有的调用逻辑）
    // 将 systemPrompt 添加到系统消息中
    // ... existing LLM call code ...

    // 4. 异步处理记忆
    String finalAnswer = lastAnswer; // 保存回复内容
    response.doOnComplete(() -> {
        if (request.getMemoryEnabled() == null || request.getMemoryEnabled()) {
            memoryOrchestrator.processAndStore(userId, sessionId, question, finalAnswer);
        }
    });

    return response;
}
```

## 前端集成状态

前端已完成记忆增强集成：
- ✅ AskRequest 接口添加 memoryEnabled 字段
- ✅ Chat Store 添加 memoryEnabled 状态
- ✅ ChatInput 组件添加记忆增强开关
- ✅ 聊天页面传递 memoryEnabled 参数

### 使用方式

在聊天页面底部工具栏，用户可以通过灯泡图标旁的开关控制记忆增强功能：
- 开启时（默认）：AI 会记住用户偏好、历史对话等
- 关闭时：AI 不使用记忆系统，每次对话独立进行

## 配置说明

在 `application.yml` 中可以调整记忆系统参数：

```yaml
memory:
  enabled: true                              # 是否启用记忆系统
  working-window-size: 10                    # 工作记忆窗口大小
  working-memory-ttl-hours: 1                # 工作记忆过期时间(小时)
  semantic-similarity-threshold: 0.75        # 语义记忆相似度阈值
  semantic-dedup-threshold: 0.9              # 语义记忆去重阈值
  pattern-min-observations: 3                # 模式记忆最小观察次数
  preference-importance-threshold: 5         # 偏好提取重要性阈值
  boundary-detection-window-size: 3          # 语义边界检测窗口
  summary-max-messages: 20                   # 摘要最大消息数阈值
  decay-cron: "0 0 3 * * ?"                  # 记忆衰减任务Cron
  memory-index-name: "sdh-memory-index"      # ES记忆索引名称
  working-memory-key-prefix: "memory:working:" # Redis工作记忆Key前缀
```

## 测试建议

1. **单元测试**: 使用 Mock 测试各 MemoryLayer 实现
2. **集成测试**: 使用 Testcontainers 测试 ES、Neo4j、Redis
3. **端到端测试**: 发送多轮对话，验证记忆提取和召回

## 注意事项

1. 记忆系统是增强功能，任何一层失败都不应阻塞正常对话
2. 提取器异步执行，不应影响响应延迟
3. 向量检索可能增加约 50ms 延迟，可在配置中调整阈值优化
4. 用户可通过前端开关关闭记忆增强功能

## 记忆管理页面

访问 `/memory` 路径可进入记忆中心页面，功能包括：
- 查看和删除用户偏好
- 查看知识事实
- 查看行为模式
- 查看对话摘要
- 手动添加偏好
- 清除所有记忆