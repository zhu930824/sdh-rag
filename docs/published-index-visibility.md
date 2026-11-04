# 已发布索引可见性与固定检索版本

本阶段承接 V37 的检索证据追溯，解决重建索引时新旧片段同时参与检索的问题。V38 引入 SQL 发布指针、独立物理写入批次，以及一次检索共用的不可变代际集合。

## 写入与发布协议

| 领域对象 | 含义 |
|---|---|
| `index_revision` | 文档关联的逻辑重建版本，重新入队时递增 |
| `index_generation` | 一次实际写入的物理批次；同一 Job 重试也生成不同值 |
| `published_generation` | 当前线上可见的完整批次，由 SQL 事务更新 |
| `index_write_generation` | 当前允许发布的写入批次，防止旧执行发布 |
| `index_source_sha256` | 当前逻辑版本解析所得内容的固定指纹；源内容变化须新建版本 |
| `publicationVersion` | 按知识库 ID 和已发布批次集合计算的 SHA-256 |

1. 在短事务中验证 Job 租约、关联版本与任务 ID，创建 `STAGING` 批次。
2. 以批次标识参与生成向量 ID。过期执行与重试执行不会覆盖同一组向量。
3. 写入所有片段并刷新 ES。写入或刷新失败时保留原发布指针。
4. 在短事务中再次验证租约、关联版本和物理批次，将批次标记 `PUBLISHED`，同时更新关联发布指针、片段数及成功状态。
5. 摘要以同一批次为 ID 写入并刷新，然后标记 `summary_ready`。不完整的摘要集合不参与粗筛，级联检索走既有完整混合检索回退。

Job 领取批次使用新的租约标识，防止同一调度器中迟到的旧执行借用重试租约。发布事务内的心跳 UPDATE 持有 Job 行锁至事务结束，然后锁定文档关联；事务不跨越文档解析、向量写入或模型调用。

## 检索协议

公开 RAG 搜索、向量搜索和摘要粗筛入口自动建立读作用域。一次 RAG 级联中的改写查询、向量/关键词、指定文档、热/冷及摘要检索复用同一个已发布集合；嵌套调用不会重新读取发布指针。作用域退出后清理线程状态，不持有长数据库事务。

`knowledge_index_read_snapshot` 按内容指纹保存完整集合，运行证据保存其指纹和简要信息。即使未召回片段，也记录本次查询使用的版本。Agent 执行批次和评测归档复制该版本；归档的版本依据为 `INDEX_GENERATION_MANIFEST`。历史记录缺少固定版本时仍保留 `UNPINNED` / `UNKNOWN`。

ES 的过滤条件放在原生 `knn.filter` 内，关键词查询使用必选过滤，不采用召回后剔除。依据见 [Elasticsearch filtered kNN](https://www.elastic.co/docs/solutions/search/vector/knn/filtered-knn-search)。未发布集合为空时使用 `match_none`，不会退回不受约束的查询。

## 旧数据与上线步骤

V38 仅为迁移时 `process_status=2` 的已完成关联登记 `legacy:<relation>:<revision>`。旧向量需要同时满足文档 ID、逻辑版本且没有新批次字段。旧格式缺少版本字段只对明确登记为旧版本 0 的关联解释为 0；没有已完成关联的孤立向量不获发布资格。旧内容指纹不补造，旧摘要不参与粗筛。

旧格式过滤使用 Painless 的 doc values；字段缺失先检查映射与值长度，参考 [Elasticsearch script fields](https://www.elastic.co/docs/explore-analyze/scripting/modules-scripting-fields)。实际节点验收覆盖该脚本在原生 kNN 与关键词查询中的执行。

部署按以下顺序：

1. 停止旧索引 Worker，等待或中止正在执行的旧协议任务，并暂停领取任务。
2. 执行 V38 迁移。迁移只登记数据库已完成状态，无法追认升级前已经损坏或混合的旧索引。
3. 部署本版本后恢复 Worker。新写入前补充 keyword 批次映射；映射或刷新失败应阻断发布。
4. 对没有已完成发布头的旧关联重新入队，观察成功发布后再恢复其查询使用。

不支持新旧写入协议混跑。不要在新协议已运行后直接回滚到会按逻辑版本删除旧向量的代码。

## 保证范围与后续顺序

V38 保留旧物理批次以保护读取。后续 V39 已补上数据库读租约、保留窗口、精确批次删除、失败恢复和墓碑复扫，详见 [检索读租约与索引批次回收](index-lifecycle-and-retention.md)。自动删除默认关闭；尚无按历史版本重新执行查询的 API。显式解绑/删除知识库仍按删除流程执行，不承诺删除后旧读请求继续得到内容。

V38 的集合标识描述索引发布集合。V40 已进一步衔接 [业务草稿门禁与业务发布批次](knowledge-publication-gate.md)；文档管理的物理片段预览/统计仍不等同于在线检索可见集合。

下一步按以下顺序推进：

1. V39 已实现读者租约、过期保护、保留策略与摘要/片段批次回收；后续补清理状态管理界面及审计元数据归档策略。
2. V40 已衔接草稿候选验证与索引发布，让门禁失败的批次不能推动线上发布头；后续完善完整配置与场景回归门槛。
3. 场景回归绑定固定版本和配置，建立可比较的评测基线，再接入成本与可观测性。

## 验证入口与行动记录

- `PublishedIndexServiceTest`：暂存不可见、嵌套固定视图、重试物理批次隔离、旧任务拒绝、源内容变化拒绝、发布幂等。
- `PublishedIndexQueryTest`：实际 Spring AOP 代理建立/关闭作用域，并验证各路径的 kNN 前置过滤。
- `DocumentProcessServiceImplTest`：写入—刷新—发布的先后关系，以及刷新失败时不发布、不删除旧向量。
- `PlatformJobDispatcherTest`：先复现同调度器复用租约标识，再验证每次领取隔离。
- `AgentMysqlIT`：V1—V38 真实 MySQL 迁移，过期执行拒绝、发布指针与批次状态、读视图跨发布保持不变。
- `PublishedIndexElasticsearchIT`：独立 ES 8.18.5 节点，实际执行旧格式脚本、新批次向量/关键词/混合/指定文档/冷索引及摘要检索。标准分词和二维合成向量验证发布协议，不覆盖业务 IK 分词、真实嵌入或召回质量。

本机复现使用 `scripts/test-agent-mysql.ps1` 和 `scripts/test-published-index-es.ps1`，分别传入 MySQL 可执行文件和 ES 8.18.5 解压目录。测试创建私有数据目录和随机本机端口，退出后关闭自建进程，证据目录保留。ES 发行包来自 Elastic 官方，使用官方 SHA-512 校验。

行动日志保存在 `.workbuddy/publication-fence-red.log`、`.workbuddy/published-index-*.log`。按 ub-mode 先复现行为失败再修复；当前环境没有 crapper、dryer、mutator，未执行这些工具。线上迁移与生产部署不在本轮验收范围内。

2026-11-04 验证结果：

- 普通测试 240 项，239 项通过，1 项应用环境测试沿用原配置跳过；生产制品打包成功。
- 真实 MySQL 8.4.13 验收 16 项全部通过，包括 V1—V38 迁移与重复迁移、旧执行拒绝、注入发布失败后状态与指针共同回滚。
- 真实 Elasticsearch 8.18.5 验收通过，覆盖旧格式脚本、发布前后固定视图、全部查询路径及摘要过滤；测试节点已退出。
- 发布指针复用测试、映射失败阻断测试均先观察到预期失败，修复后通过。最终日志为 `.workbuddy/published-index-mysql-verify.log` 与 `.workbuddy/published-index-final-es-recheck.log`。
- `git diff --check` 通过。本阶段没有前端改动，未重复前端检查；上一阶段的全局类型检查 320 条诊断仍是待治理项。
