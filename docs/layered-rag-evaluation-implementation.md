# 分层 RAG 评测功能说明

本平台已按照 `rag-evaluation-guide.md` 把评测拆为检索、生成和完整评测三种运行模式。建议先用固定数据集建立检索基线，再开启生成评审，最后在发布前做完整回归。

## 1. 评测模式

| 模式 | 计算内容 | 适用时机 |
|---|---|---|
| RETRIEVAL | Recall@K、Precision@K、MRR、NDCG@K、MAP、命中率和负样本错误命中率 | 日常调整切分、Embedding、检索和重排后 |
| GENERATION | 冻结本次检索 Context，生成答案并用 LLM-as-Judge 计算 Context Precision/Recall、Faithfulness、Answer Relevancy、Completeness、Fluency | 调整生成模型或 Prompt 后 |
| FULL | 同时执行检索与生成评测 | 上线前、版本发布和定期回归 |

生成与完整评测会产生模型调用成本。评审模型留空时使用平台默认模型。

## 2. 数据集标注格式

最小 JSON 数据集是一个数组，每项可包含：

```json
[
  {
    "question": "如何申请退款？",
    "expectedAnswer": "进入订单详情，在可退款期限内提交退款申请。",
    "expectedDocumentIds": [1001, 1002],
    "relevanceJudgments": {"1001": 3, "1002": 1},
    "category": "business_process",
    "difficulty": "MEDIUM",
    "businessLine": "售后服务",
    "userRole": "一线客服",
    "isNegative": false
  }
]
```

- `expectedDocumentIds` 用于二元相关性与 Recall、Precision、MRR、MAP。
- `relevanceJudgments` 是 Pointwise 的 0-3 级相关性标注，用于 NDCG；大于 0 也视为相关。
- `expectedAnswer` 是生成质量和完整性评审的参考答案。
- `category`、`difficulty`、`businessLine`、`userRole` 用于按场景、难度、业务线和用户角色切片分析。
- `isNegative=true` 表示知识库不应给出有依据的答案；完整评测仍会检查拒答质量。

Excel 导入支持同名列：`question`、`expected_answer`、`expected_document_ids`（逗号分隔）、`relevance_judgments`（JSON）、`category`、`difficulty`、`business_line`、`user_role`、`is_negative`。

## 3. 内置 MVP 测试数据集

`RAG 分层评测 MVP 数据集` 包含 24 条用例，其中 20 条正样本、4 条负样本，覆盖语义检索、排序、多跳、忠实度、引用、业务问题和拒答。

内置数据集不预设某个知识库的文档 ID。直接运行可评估生成质量；要计算严格的经典检索指标，应复制为自定义数据集，并把每个问题绑定到当前知识库的文档 ID 与相关性等级。

生产数据集建议逐步扩充到 200 条以上，并优先吸收真实用户问题、失败会话和高频业务流程。

## 4. 基线与回归

1. 选择固定数据集、评测层级和 Top K，首次运行不选择基线。
2. 评测完成后，在下一次运行中将该任务选为基线。
3. 平台要求基线属于同一知识库、已完成、评测层级一致且 Top K 一致。
4. 当前版本比较 Recall@K、MRR、Faithfulness 和 Answer Relevancy；任一有值的基线指标下降，回归结论为 `FAILED`。
5. Excel 报告包含任务级分层指标、回归结论以及逐题检索与生成得分，便于复核失败样本。

## 5. 推荐落地节奏

- 每次变更切分、Embedding、检索参数或重排模型：运行 RETRIEVAL。
- 每次变更生成模型、System Prompt 或 Context 拼装：运行 GENERATION。
- 发布前和知识库大批量更新后：运行 FULL 并对比固定基线。
- 自动评审分低或回归失败的样本进入人工抽检；评审模型分数不能替代业务专家验收。

## 6. 质量运营闭环

- 自定义数据集按版本管理。增删、编辑和批量导入会递增版本，评测任务会保存运行时的数据集版本和 QA 副本。
- 标注弹窗可以直接选择目标知识库中的文档和来源分块，并为多篇相关文档设置 1-3 级相关性。
- 逐题结果会输出失败归因和建议，包括检索遗漏、Context 噪声、幻觉、不完整和拒答失败。
- 问答评价中的点踩记录可以直接回流到指定自定义数据集，同一反馈不会重复进入同一数据集。
- 知识草稿配置回归问题后，索引完成会自动执行 Top-5 发布门禁；失败时状态为 `GATE_FAILED`，并自动移除检索关联和向量。

## 7. 实验、持续回归与人工校准

- “评测实验室”可把同一知识库、同一版本数据集同时绑定到多套变体，统一查看 Recall@K、MRR、忠实度、总体分和基线回归状态。
- 实验支持手动与 Spring Cron 定时运行，非检索变体按数据集样本量预估 LLM 评审调用；超过单批预算会直接阻断。
- 任务失败可自动重试，重试耗尽触发 `evaluation.run_failed`；任一变体相对基线下降触发 `evaluation.regression`。
- 逐题结果可按场景、难度、业务线和用户角色聚合，避免总体平均分掩盖关键业务薄弱点。
- 自动评审样本进入人工复核队列。人工评分按 1-5 星录入，平台归一化为 0-1，并统计各维度 MAE 与“相差不超过一档”的比例。

完整实施状态与后续阶段见 `rag-quality-operations-roadmap.md`。
