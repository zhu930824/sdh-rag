# SDH-RAG 工作流编排功能实现计划

## 一、现状分析

### 1.1 PaiAgent 工作流实现（参考项目）

**前端架构：**
- 使用 React + TypeScript + Ant Design
- 可视化编辑器：基于 `@xyflow/react` 实现流程图编辑
- 状态管理：Zustand (`workflowStore.ts`)
- 核心组件：
  - `EditorPage.tsx` - 工作流编辑器主页面
  - `FlowCanvas.tsx` - 流程图画布（支持节点拖拽、连线）
  - `NodePanel.tsx` - 左侧节点面板
  - `DebugDrawer.tsx` - 调试抽屉

**后端架构：**
- 使用 Spring Boot + MyBatis Plus
- 核心实体：`Workflow` (存储 flowData JSON)
- 数据模型：`WorkflowNode`、`WorkflowEdge`、`WorkflowConfig`
- 执行引擎：
  - `WorkflowExecutor` 接口 - 统一执行接口
  - `WorkflowEngine` - DAG 引擎实现
  - `DAGParser` - 拓扑排序解析器
  - `NodeExecutor` 接口 + `NodeExecutorFactory` - 节点执行器工厂模式
  - 支持多种节点执行器：`InputNodeExecutor`、`OutputNodeExecutor`、`LlmNodeExecutor`、`TTSNodeExecutor` 等
- 支持 SSE 流式执行进度推送

**节点类型：**
- 基础节点：`input`、`output`
- LLM 节点：`llm`、`openai`、`deepseek`、`qwen`、`step`、`zhipu`
- 工具节点：`tts`、`ai_ping`

**关键特性：**
- 节点拖拽创建、连线绑定
- 节点配置面板（动态表单）
- 参数引用机制（`{{参数名}}` 模板替换）
- 引用其他节点输出（`nodeId.paramName` 格式）
- 工作流保存/加载/执行
- SSE 实时执行进度反馈

### 1.2 SDH-RAG 工作流现状

**前端现状：**
- Vue 3 + TypeScript + Ant Design Vue
- 自定义实现的可视化编辑器（SVG 绘制连线）
- 已有基础 UI 框架但功能不完整
- 节点配置面板已有雏形

**后端现状：**
- 基础 CRUD 接口已实现
- `Workflow` 实体存储 `flowData` JSON
- `WorkflowNode` 实体（但未使用，节点数据存在 flowData 中）
- **缺失：执行引擎、节点执行器、流式执行**

---

## 二、实现目标

基于 PaiAgent 的成熟实现，完善 SDH-RAG 的工作流编排功能：

1. **可视化编辑器增强** - 引入 Vue Flow 替代自定义实现
2. **执行引擎实现** - DAG 解析、节点执行器体系
3. **节点类型扩展** - 支持 RAG 场景的检索节点
4. **流式执行反馈** - SSE 实时推送执行进度
5. **调试功能** - 单步调试、变量查看

---

## 三、详细实现计划

### 阶段一：前端可视化编辑器重构（优先级：高）

#### 1.1 引入 Vue Flow 库
```bash
cd rag-frontend
npm install @vue-flow/core @vue-flow/background @vue-flow/controls @vue-flow/minimap
```

#### 1.2 创建/修改核心组件

| 文件路径 | 说明 |
|---------|------|
| `src/views/workflow/components/FlowCanvas.vue` | 基于 Vue Flow 的画布组件 |
| `src/views/workflow/components/NodePanel.vue` | 左侧节点面板（拖拽源） |
| `src/views/workflow/components/ConfigPanel.vue` | 右侧配置面板 |
| `src/views/workflow/components/DebugDrawer.vue` | 调试抽屉组件 |
| `src/stores/workflowStore.ts` | 工作流状态管理（Pinia） |
| `src/utils/workflowNode.ts` | 节点序列化/反序列化工具 |

#### 1.3 节点类型定义

```typescript
// src/types/workflow.ts
type NodeType = 
  | 'start' | 'end'        // 开始/结束
  | 'input' | 'output'     // 输入/输出
  | 'llm'                  // 大模型（通用）
  | 'retrieval'            // 知识检索
  | 'condition'            // 条件分支
  | 'code'                 // 代码执行
  | 'http'                 // HTTP 请求
```

#### 1.4 前端任务清单

- [ ] 安装 Vue Flow 依赖
- [ ] 创建 `workflowStore.ts` (Pinia)
- [ ] 重构 `FlowCanvas.vue` 使用 Vue Flow
- [ ] 实现 `NodePanel.vue` 拖拽功能
- [ ] 实现 `ConfigPanel.vue` 动态配置表单
  - [ ] 输入节点配置
  - [ ] LLM 节点配置（模型选择、提示词模板、参数引用）
  - [ ] 检索节点配置（知识库选择、topK、阈值）
  - [ ] 输出节点配置（响应模板）
- [ ] 实现 `DebugDrawer.vue` 调试功能
- [ ] 实现参数引用 UI（引用其他节点输出）

---

### 阶段二：后端执行引擎实现（优先级：高）

#### 2.1 目录结构

```
rag-backend/src/main/java/cn/sdh/backend/
├── workflow/
│   ├── executor/
│   │   ├── WorkflowExecutor.java          # 执行器接口
│   │   ├── WorkflowEngine.java            # DAG 引擎实现
│   │   ├── NodeExecutor.java              # 节点执行器接口
│   │   └── NodeExecutorFactory.java       # 执行器工厂
│   ├── model/
│   │   ├── WorkflowConfig.java            # 工作流配置
│   │   ├── WorkflowNode.java              # 节点模型
│   │   └── WorkflowEdge.java              # 连线模型
│   ├── dag/
│   │   └── DAGParser.java                 # DAG 解析器（拓扑排序）
│   └── executor/impl/
│       ├── InputNodeExecutor.java         # 输入节点执行器
│       ├── OutputNodeExecutor.java        # 输出节点执行器
│       ├── LlmNodeExecutor.java           # LLM 节点执行器
│       ├── RetrievalNodeExecutor.java     # 检索节点执行器
│       ├── ConditionNodeExecutor.java     # 条件节点执行器
│       └── HttpNodeExecutor.java          # HTTP 节点执行器
```

#### 2.2 核心类设计

**WorkflowExecutor 接口：**
```java
public interface WorkflowExecutor {
    ExecutionResponse execute(Workflow workflow, String inputData);
    ExecutionResponse executeWithCallback(Workflow workflow, String inputData, 
        Consumer<ExecutionEvent> eventCallback);
}
```

**NodeExecutor 接口：**
```java
public interface NodeExecutor {
    Map<String, Object> execute(WorkflowNode node, Map<String, Object> input);
    String getSupportedNodeType();
}
```

#### 2.3 后端任务清单

- [ ] 创建 `workflow` 包结构
- [ ] 实现 `WorkflowConfig`、`WorkflowNode`、`WorkflowEdge` 模型
- [ ] 实现 `DAGParser`（拓扑排序 + 循环检测）
- [ ] 实现 `NodeExecutor` 接口
- [ ] 实现 `NodeExecutorFactory`
- [ ] 实现各节点执行器：
  - [ ] `InputNodeExecutor`
  - [ ] `OutputNodeExecutor`（支持模板变量替换）
  - [ ] `LlmNodeExecutor`（集成 Spring AI）
  - [ ] `RetrievalNodeExecutor`（ES 向量检索）
  - [ ] `ConditionNodeExecutor`
  - [ ] `HttpNodeExecutor`
- [ ] 实现 `WorkflowEngine`
- [ ] 创建执行记录表 `workflow_execution`
- [ ] 添加执行相关 API：
  - [ ] `POST /api/workflow/{id}/execute` - 同步执行
  - [ ] `GET /api/workflow/{id}/execute/stream` - SSE 流式执行
- [ ] 实现参数模板替换工具类

---

### 阶段三：LLM 节点集成（优先级：中）

#### 3.1 LLM 全局配置

参考 PaiAgent 的 `LLMGlobalConfig` 实体：
- 支持多个 LLM 供应商配置
- 节点可引用全局配置或独立配置

#### 3.2 Spring AI 集成

利用项目已有的 Spring AI 依赖：
- 复用现有的 `ChatClient` 配置
- 支持通义千问、DeepSeek 等

#### 3.3 提示词模板

```java
// 支持变量替换
String prompt = "用户问题：{{query}}\n相关知识：{{context}}";
// 替换为实际值
prompt = PromptTemplateService.process(prompt, inputParams, inputData);
```

---

### 阶段四：检索节点实现（优先级：中）

#### 4.1 集成 Elasticsearch 向量检索

复用项目现有的 ES 配置和向量存储：
- `RetrievalNodeExecutor` 调用 `VectorStore` 进行相似度搜索
- 支持 topK、scoreThreshold 配置

#### 4.2 输出格式

```json
{
  "documents": [
    { "content": "文档内容", "score": 0.85, "metadata": {} }
  ],
  "context": "拼接后的上下文文本"
}
```

---

### 阶段五：流式执行与调试（优先级：中）

#### 5.1 SSE 执行进度推送

```java
@GetMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter executeStream(@PathVariable Long id, @RequestParam String inputData) {
    // 推送事件：WORKFLOW_START, NODE_START, NODE_SUCCESS, NODE_ERROR, WORKFLOW_COMPLETE
}
```

#### 5.2 前端 EventSource 接收

```typescript
const eventSource = new EventSource(url)
eventSource.addEventListener('NODE_SUCCESS', (e) => {
  const event = JSON.parse(e.data)
  // 更新 UI 显示执行进度
})
```

---

## 四、数据库变更

### 4.1 工作流表（已存在，确认字段）

```sql
CREATE TABLE workflow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    flow_data TEXT,           -- JSON 存储节点和连线
    status TINYINT DEFAULT 0,
    user_id BIGINT,
    create_time DATETIME,
    update_time DATETIME
);
```

### 4.2 执行记录表（新增）

```sql
CREATE TABLE workflow_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    input_data TEXT,
    output_data TEXT,
    status VARCHAR(20),       -- RUNNING, SUCCESS, FAILED
    node_results TEXT,        -- JSON 存储各节点执行结果
    error_message TEXT,
    duration INT,             -- 执行时长(ms)
    create_time DATETIME,
    INDEX idx_workflow_id (workflow_id)
);
```

---

## 五、API 接口设计

### 5.1 已有接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/workflow/list | 分页查询工作流列表 |
| GET | /api/workflow/{id} | 获取工作流详情 |
| POST | /api/workflow | 创建工作流 |
| PUT | /api/workflow/{id} | 更新工作流 |
| DELETE | /api/workflow/{id} | 删除工作流 |
| PUT | /api/workflow/{id}/status | 切换状态 |

### 5.2 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/workflow/{id}/execute | 同步执行工作流 |
| GET | /api/workflow/{id}/execute/stream | SSE 流式执行 |
| GET | /api/workflow/{id}/executions | 获取执行记录列表 |
| GET | /api/workflow/executions/{executionId} | 获取执行详情 |
| POST | /api/workflow/{id}/debug-node | 调试单个节点 |

---

## 六、实施顺序建议

1. **第一阶段（1-2天）**：前端 Vue Flow 集成 + 基础画布功能
2. **第二阶段（2-3天）**：后端执行引擎框架 + DAG 解析
3. **第三阶段（1-2天）**：Input/Output 节点执行器 + 基础执行流程
4. **第四阶段（2-3天）**：LLM 节点 + Spring AI 集成
5. **第五阶段（1-2天）**：检索节点 + ES 向量检索
6. **第六阶段（1天）**：SSE 流式执行 + 调试功能
7. **第七阶段（1天）**：测试 + 文档

---

## 七、关键代码参考

从 PaiAgent 复用的核心文件：
- `DAGParser.java` - 拓扑排序算法
- `WorkflowEngine.java` - DAG 执行引擎
- `NodeExecutorFactory.java` - 执行器工厂模式
- `AbstractLLMNodeExecutor.java` - LLM 执行器基类
- `OutputNodeExecutor.java` - 模板变量替换
- `workflowStore.ts` - 状态管理模式
- `FlowCanvas.tsx` - 画布交互逻辑

---

## 八、风险与注意事项

1. **Vue Flow 学习曲线**：团队需要熟悉 Vue Flow API
2. **执行引擎复杂性**：需要处理循环依赖检测、异常处理
3. **SSE 连接管理**：需要处理断线重连、认证
4. **参数引用解析**：模板替换需要正确处理嵌套引用
5. **性能优化**：大型工作流的执行效率
