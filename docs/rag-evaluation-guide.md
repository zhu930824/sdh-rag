# RAG 测评完整指南：手段、方法、指标与数据集

> 一份面向工程的 RAG 评估速查手册，覆盖检索 / 生成 / 端到端三层评估体系，配套指标公式、专用数据集与工具选型。

---

## 目录

1. [评估的三层结构](#一评估的三层结构)
2. [检索质量评估](#二检索质量评估最该先做的)
3. [生成质量评估](#三生成质量评估)
4. [端到端评估](#四端到端评估)
5. [专用测试数据集](#五专用测试数据集)
6. [常用评测工具](#六常用评测工具)
7. [最小可行评估套件（MVP）](#七推荐的最小可行评估套件)
8. [常见陷阱与最佳实践](#八常见陷阱与最佳实践)

---

## 一、评估的三层结构

把 RAG pipeline 拆开看，可以从三个层级独立评估：

| 层级 | 评估对象 | 输入 | 输出 | 解决问题 |
|---|---|---|---|---|
| **① 端到端** | 完整 RAG 系统 | 用户 query | 最终答案 | 系统整体是否可用 |
| **② 生成质量** | LLM 生成器 | query + context | 答案 | 答案是否忠于 context、是否切题 |
| **③ 检索质量** | 召回器 + 重排器 | query | context 文档 | 是否召回了对的内容 |

**工程铁律**：先评估检索（出问题最多），再评估生成（其次），最后才做端到端。检索差，生成神仙难救；检索对了，生成才有意义。

```
评估顺序（推荐）
────────────────────────────────
  ③ 检索 ──► ② 生成 ──► ① 端到端
────────────────────────────────
     ↑           ↑           ↑
   最频繁     中等频率    上线前/定期
```

---

## 二、检索质量评估（最该先做的）

检索是 RAG 的天花板。所有指标都需要**相关性标注**作为 ground-truth。

### 2.1 核心指标

| 指标 | 公式 | 适用场景 |
|---|---|---|
| **Recall@k** | `top-k 中命中 ground-truth 的比例` | 看召回够不够，最常用 |
| **Precision@k** | `top-k 中相关文档占比` | 看噪音多不多 |
| **MRR** | `首个正确答案排名的倒数均值` | 单答案问答 |
| **NDCG@k** | `加权相关性 + 位置折损` | 多相关性等级标注 |
| **Hit Rate** | `top-k 是否至少命中一次` | 粗粒度监控 |
| **MAP** | `所有相关文档平均精度的均值` | 多答案召回 |
| **Context Precision**（RAGAS） | `top-k 中相关片段的比例` | RAG 专用 |
| **Context Recall**（RAGAS） | `答案所需信息是否在 context 里` | RAG 专用 |

### 2.2 指标公式详解

**MRR（Mean Reciprocal Rank）**
$$
MRR = \frac{1}{|Q|} \sum_{i=1}^{|Q|} \frac{1}{rank_i}
$$
其中 `rank_i` 是第 i 个 query 首个相关结果的排名。

**NDCG@k（Normalized Discounted Cumulative Gain）**
$$
NDCG@k = \frac{DCG@k}{IDCG@k}, \quad DCG@k = \sum_{i=1}^{k} \frac{rel_i}{\log_2(i+1)}
$$

**MAP（Mean Average Precision）**
$$
MAP = \frac{1}{|Q|} \sum_{q=1}^{|Q|} AP(q), \quad AP = \sum_{n} (R_n - R_{n-1}) P_n
$$

### 2.3 标注数据的形式

需要以下三类之一：

- **Pointwise**：`(query, doc_id) → 相关性分数（0/1/2/3）`
- **Pairwise**：`(query, doc_a, doc_b) → a 比 b 更相关？`
- **Listwise**：`(query) → 相关 doc_id 排序列表`

---

## 三、生成质量评估

把"检索+重排"冻结，只评估 LLM 生成的部分。

| 指标 | 含义 | 计算方法 |
|---|---|---|
| **Faithfulness（忠实度）** | 答案是否只来自 context，没瞎编 | 把答案拆成 claims，逐条检查能否在 context 找到依据 |
| **Answer Relevancy** | 答案是否切题 | 用答案反向生成 N 个问题，计算与原 query 的相似度 |
| **Context Relevance** | 检索出的 context 是否有用 | 评估每段 context 对回答问题的贡献，剔除低相关片段 |
| **Completeness** | 答案是否完整覆盖问题 | 对照参考答案/拆解出的子问题逐项核查 |
| **BLEU / ROUGE / METEOR** | n-gram 重合度 | 偏传统 NLP，已不太适合开放式生成 |
| **BERTScore** | 语义相似度 | 用 BERT 表征计算，比 n-gram 准很多 |
| **LLM-as-Judge** | 用大模型按 rubric 打 1-5 分 | 最接近人类、成本最低、当前主流 |

### 3.1 LLM-as-Judge Prompt 模板

```markdown
你是一个严格的 RAG 系统评测员。

## 问题
{question}

## 检索到的 Context
{context}

## 待评估答案
{answer}

## 参考答案
{reference}

请从以下维度打分（1-5）：

1. **忠实度**：答案的每一句能否在 context 中找到依据？（不允许编造）
2. **相关性**：答案是否切题，没有答非所问？
3. **完整性**：是否覆盖了问题的所有要点？
4. **流畅性**：语言是否通顺、无语法错误？

输出格式：
- 忠实度: X
- 相关性: X
- 完整性: X
- 流畅性: X
- 总体: X
- 理由: <一句话>
```

### 3.2 LLM-as-Judge 与人工评估的相关性

| 评估方式 | 与人工评估的相关系数 | 成本 | 速度 |
|---|---|---|---|
| GPT-4 打分 | 0.80+ | 中 | 快 |
| Claude 打分 | 0.80+ | 中 | 快 |
| 专家人工 | 1.00（基准） | 高 | 慢 |
| BLEU/ROUGE | 0.30-0.50 | 极低 | 极快 |

---

## 四、端到端评估

把整个 RAG pipeline 当黑盒，喂问题看答案。

### 4.1 评估方式

- **人工评估**：金标准。设计评分表（准确性、流畅度、引用准确性、安全性），3-5 人盲评，取中位数。
- **LLM-as-Judge**：GPT-4 / Claude 用结构化 prompt 打分，成本低 10-100 倍，相关性 0.8+ 与人工。
- **A/B Test**：线上分流，对比两个版本的 CTR、采纳率、点赞率、留存。
- **业务指标**：用户改写率、对话轮次、投诉率。

### 4.2 评分表设计（人工评估用）

| 维度 | 1 分 | 3 分 | 5 分 |
|---|---|---|---|
| **准确性** | 完全错误 | 部分正确 | 完全正确 |
| **忠实度** | 大量编造 | 偶有编造 | 完全忠于 context |
| **相关性** | 答非所问 | 部分切题 | 精准切题 |
| **完整性** | 漏掉核心 | 覆盖主要 | 覆盖全部 |
| **流畅性** | 语句不通 | 基本通顺 | 表达流畅 |
| **引用** | 无引用或错误 | 部分引用 | 准确引用 |

---

## 五、专用测试数据集

### 5.1 综合数据集

| 数据集 | 用途 | 规模 | 特点 |
|---|---|---|---|
| **BEIR** | 检索器跨领域基准 | 18 个子集 | 必跑，包含 bio/finance/news 等 |
| **MS-MARCO** | 段落检索 / QA | 1M+ query | Bing 真实搜索日志，工业标准 |
| **Natural Questions** | 开放域 QA | 300K+ | Google 真实问题 |
| **TriviaQA** | 简答题 | 95K | 答案短、适合评估 |
| **HotpotQA** | 多跳推理 | 113K | 测试需要多文档交叉 |
| **KILT** | 知识密集型任务统一基准 | 11 个子任务 | Wikipedia 全量支持 |
| **ELI5** | 长答案 QA | 270K | 测试长文本生成 |

### 5.2 RAG 专用数据集

| 数据集 | 用途 | 规模 | 特点 |
|---|---|---|---|
| **RGB** | RAG 综合基准（arXiv 2307.16833） | 6 大类任务 | 集成噪声鲁棒性、信息整合、拒答等 |
| **RAGAS Test Set** | RAGAS 自带 demo | 小 | 开箱即用、含 ground truth |
| **CRUD-RAG** | 覆盖 C/R/U/D 四类任务 | 中文为主 | 适合中文场景 |
| **ALCE** | 自动引用评估 | 多个子集 | 专门测"带引用的 QA" |
| **Hugging Face rag-datasets** | 集合入口 | - | 一键加载，常见数据集都在 |

### 5.3 中文场景数据集

| 数据集 | 用途 | 来源 |
|---|---|---|
| **CRUD-RAG** | RAG 四类任务 | 中文为主 |
| **C-Eval** | 综合评测（可改造为 QA） | 上海交大 |
| **DuReader** | 中文 QA | 百度 |
| **CMRC** | 中文机器阅读理解 | 哈工大 |
| **LCSTS** | 摘要 | 哈尔滨工业大学 |

### 5.4 数据集选择建议

| 场景 | 推荐数据集 |
|---|---|
| 通用 RAG | MS-MARCO + Natural Questions |
| 跨领域检索 | BEIR |
| 多跳推理 | HotpotQA |
| 中文 RAG | CRUD-RAG + DuReader |
| 带引用的 QA | ALCE |
| 快速验证 | RAGAS Test Set（几百条） |
| 业务贴近 | 用 LLM 合成（如 ARES）或人工标注 |

---

## 六、常用评测工具

| 工具 | 定位 | 强项 | 适用阶段 |
|---|---|---|---|
| **RAGAS** | RAG 专用，最流行 | Faithfulness / Context Precision/Recall 开箱即用 | 快速上手 |
| **TruLens** | 全链路追踪 | 仪表盘可视化好，支持 feedback function | 全链路观测 |
| **LangSmith** | LangChain 配套 | trace + eval 一体，UI 强 | LangChain 项目 |
| **Phoenix（Arize）** | 开源观测 | 嵌入可视化、向量检索分析 | 检索深度调试 |
| **DeepEval** | 单元测试风格 | 像 pytest 一样写 RAG 测试 | CI 集成 |
| **LlamaIndex Evaluation** | LlamaIndex 配套 | 检索器/生成器解耦评估 | LlamaIndex 项目 |
| **ARES**（Stanford） | 自动构造评测集 | 用 LLM 合成测试数据，省人工标注 | 冷启动 |
| **Llama Guard / NeMo Guardrails** | 安全评估 | 检测有害输出 | 安全合规 |

### 6.1 工具选型决策树

```
你的项目用了什么框架？
├─ LangChain ──► LangSmith
├─ LlamaIndex ──► LlamaIndex Evaluation
└─ 自研 ──► RAGAS + Phoenix

你的评估重点是什么？
├─ 检索质量 ──► BEIR + 自定义 Recall/MRR
├─ 生成质量 ──► RAGAS（Faithfulness + Answer Rel.）
├─ 端到端 ──► LLM-as-Judge + 人工抽样
└─ 安全合规 ──► Llama Guard + NeMo Guardrails

你需要 CI 自动化吗？
├─ 是 ──► DeepEval（pytest 风格）
└─ 否 ──► RAGAS（脚本风格）
```

---

## 七、推荐的最小可行评估套件（MVP）

如果你想快速上手，按这个顺序：

### 7.1 第一周：检索基线

1. 选 **BEIR 的 `fiqa` 或 `scifact` 子集**（几百条就能跑）
2. 计算 **Recall@5 + MRR**
3. 记录 baseline

### 7.2 第二周：生成质量

用 **RAGAS** 跑四个核心指标：

```python
from ragas import evaluate
from ragas.metrics import (
    faithfulness,
    answer_relevancy,
    context_precision,
    context_recall,
)

result = evaluate(
    dataset,
    metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
)
print(result)
```

先覆盖 80% 场景，**不要追求 100% 指标齐全**。

### 7.3 第三周：端到端 + 人工

- 挑 50-100 条**真实用户 query**（不是公开数据集！）
- 人工打 1-5 分（用第四节评分表）
- 建立 baseline

### 7.4 第四周：CI 集成

用 **DeepEval** 的 pytest 风格：

```python
import pytest
from deepeval import assert_test
from deepeval.metrics import FaithfulnessMetric, AnswerRelevancyMetric
from deepeval.test_case import LLMTestCase

def test_rag_faithfulness():
    test_case = LLMTestCase(
        input="什么是 RAG？",
        actual_output=rag_query("什么是 RAG？"),
        retrieval_context=[...],
    )
    assert_test(test_case, [
        FaithfulnessMetric(threshold=0.7),
        AnswerRelevancyMetric(threshold=0.7),
    ])
```

每次改 embedding/分块策略自动跑回归。

---

## 八、常见陷阱与最佳实践

### 8.1 常见陷阱

| 陷阱 | 后果 | 规避方法 |
|---|---|---|
| 只看 BLEU/ROUGE | 与人类判断相关性仅 0.3-0.5 | 用 BERTScore 或 LLM-as-Judge |
| 用公开数据集评估业务系统 | 指标 0.9 但业务只有 0.5 | 必须混入真实业务 query |
| 只做端到端评估 | 不知道问题在检索还是生成 | 分层评估 |
| Faithfulness 不做 | 模型悄悄编造 | 必须有！RAG 的核心价值 |
| 没有 ground truth | 无法算检索指标 | 人工标注或 LLM 合成（ARES） |
| LLM-as-Judge 用小模型 | 打分不稳定 | 用 GPT-4 / Claude / Gemini Pro |
| 评估样本太少（<50） | 统计不显著 | 至少 200 条，最好 500+ |

### 8.2 最佳实践

1. **从检索开始**：检索是 RAG 的天花板，先把检索 Recall 拉到 0.85+ 再谈生成。
2. **业务 query 优先**：公开数据集是辅助，真实业务 query 才是黄金标准。
3. **Faithfulness 必做**：这是 RAG 相对纯 LLM 的核心价值，一旦失守就退化成普通 LLM。
4. **分层评估 + 端到端兜底**：先定位问题层级，最后用线上 A/B 验证。
5. **持续监控**：检索指标上线后要持续监控，文档库更新会让 Recall 退化。
6. **成本权衡**：LLM-as-Judge 一次评估可能要几十美元，先小批量试，确认有效再扩大。
7. **建立 baseline**：每次优化前先记录 baseline，否则不知道改动有没有效。

---

## 附录：RAG 评估速查表

```
┌─────────────────────────────────────────────────────────────┐
│  评估 checklist（每次改 RAG 必跑）                            │
├─────────────────────────────────────────────────────────────┤
│  □ 检索：Recall@5 ≥ baseline + 5%                           │
│  □ 检索：MRR ≥ baseline + 5%                                │
│  □ 生成：Faithfulness ≥ 0.8                                 │
│  □ 生成：Answer Relevancy ≥ 0.8                             │
│  □ 端到端：人工抽样 50 条，无严重退化                         │
│  □ 线上：A/B 实验核心业务指标不退化                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 参考资料

- **RAGAS 论文**：arXiv 2309.15217
- **RGB 基准**：arXiv 2307.16833
- **ARES 论文**：arXiv 2311.09523
- **BEIR 基准**：arXiv 2104.08663
- **HotpotQA 论文**：arXiv 1809.09600
- **LLM-as-Judge 综述**：arXiv 2310.05419
- **TruLens 文档**：https://www.trulens.org/
- **RAGAS 文档**：https://docs.ragas.io/
- **DeepEval 文档**：https://docs.confident-ai.com/
- **Phoenix 文档**：https://docs.arize.com/phoenix

---

*最后更新：2026-09-07*