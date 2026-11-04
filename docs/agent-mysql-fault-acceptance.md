# Agent 真实 MySQL 与故障验收（P0-1）

## 本次交付

新增可重复执行的 `mysql-it` Maven Profile、托管测试数据库进程、独立 JVM 故障 Worker 和 Windows 执行脚本。日常单元测试保持原入口；启用该 Profile 后，数据库条件不满足会明确失败，不会把未执行的真实数据库测试记为通过或跳过。

本机使用 MySQL Community Server **8.4.13** Windows ZIP 包。下载来源是 [MySQL 官方下载页](https://dev.mysql.com/downloads/mysql/8.4.html?os=3)，归档 MD5 与该页面公布的 `7b94489d5524dea9b27097068bea25a8` 一致。没有安装系统服务或连接已有业务数据库。

## 隔离方式

- 每轮测试自行初始化临时数据目录、启动独立 `mysqld`，仅监听 `127.0.0.1`，使用随机端口。
- 首次启动后设置随机 root 密码；密码仅保存在测试进程内存与自建 Worker 的环境中，不写入源码、命令行或验收文档。
- 测试库名称为 `sdh_agent_it_<随机 UUID>`，数据清理只对该连接内的测试表执行。
- 数据库停止和强杀只针对 Java 创建的进程及其子进程，不按进程名称批量结束服务。
- 业务 HTTP 测试服务绑定本机私网 IPv4 和随机端口，使用随机路径。生产 HTTP 网关禁止回环地址，因此没有为了测试放松 SSRF 校验。
- MySQL 退出后保留临时数据和错误日志作为故障证据，位置打印在测试日志中；下载的运行时位于本地忽略目录 `.workbuddy/mysql-p0/`。

## 验收用例

| 用例 | 真实边界与断言 |
|---|---|
| 迁移与升级 | 空库执行 V1–V30，再实际执行 V31–V35；校验 35 条迁移；重复迁移执行数为 0 |
| 场景版本竞争 | 同一父版本并发导入，仅创建一个 v(n+1)，父版本保持不变 |
| 归档竞争与不可变性 | 同一场景和 Run 并发归档返回同一记录；后续修改原回答不影响快照，其他用户无法读取 |
| 旧快照下的归档去重 | 在旧事务快照下重试已由另一连接提交的归档，唯一键冲突后通过当前读返回胜者 |
| 并发运行抢占 | 两个并发执行线程使用独立数据库连接，仅一个获得 Run 租约，执行批次只增加一次 |
| 并发动作去重 | 同工具、发起人、幂等键同时受理，只持久化一个动作和一个执行任务 |
| 旧快照下的任务去重 | 在事务中先建立旧快照，另一连接提交任务；旧事务仍能返回已提交的同一任务，而非 null |
| 旧执行者隔离 | 过期、恢复、新批次完成后，旧执行者不能续约、写检查点或覆盖最终答案 |
| 强杀独立 JVM Worker | 领取真实租约后 `destroyForcibly`；到期前不接管，到期后识别为 INTERRUPTED，历史消息关联仍保留 |
| 数据库崩溃与恢复 | 在真实受保护事务中写入但未提交，强杀 MySQL 及 Worker，再启动数据库；确认写入回滚，随后可识别中断 |
| 下游成功但平台 Worker 死亡 | 实际 HTTP 请求在测试下游独立提交回执后，强杀尚未收到响应的 Worker；动作由 EXECUTING 转 UNKNOWN，旧批次执行与直接重试均不产生第二笔写入 |

到期类测试在进程强杀后显式将数据库租约/动作时间置为过去，验证生产扫描条件，避免每例等待 120 秒或 5 分钟。它们不代表对完整等待时长或计时 SLA 的测量。

## 本次实际修复

真实数据库暴露了 H2 没有揭示的 REPEATABLE READ 行为差异：

1. 两个相同动作请求都读到“无记录”，随后只有一个 INSERT 成功。
2. 另一个请求等到胜者提交后得到唯一键冲突。
3. 原实现再次普通 SELECT，仍读原事务快照，找不到胜者，于是把幂等请求报为数据库错误。

`PlatformJobService.enqueue` 同样可能在这种情况下返回 null。新增的确定性测试先建立旧快照，再由另一连接提交任务，已复现该问题。

两个冲突处理分支现在通过 `FOR SHARE` 当前读返回已提交记录，并禁用对应查询缓存。查询只在唯一键冲突后使用，不在缺少记录时提前加锁；动作仍校验参数一致性。任务冲突若仍找不到胜者则继续抛出原异常，不返回空任务。

这些判断依据来自本轮实际失败测试，也与 [MySQL 一致性读说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html) 和 [锁定读说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) 相符。

## 运行方式

准备 MySQL 8.x 服务端可执行文件、JDK 21 与 Maven。测试需要本机可用私网 IPv4 接口执行生产 HTTP 网关的真实网络测试。

```powershell
./scripts/test-agent-mysql.ps1 `
  -MysqldPath 'C:/tools/mysql/bin/mysqld.exe' `
  -MavenPath 'mvn'
```

可选参数：`-MavenSettings <settings.xml>`、`-Offline`。测试使用 Java 管理服务端，不要求已有 MySQL 服务，也不需要手工传入业务账号或数据库密码。

等价 Maven 入口：

```powershell
$env:SDH_MYSQL_IT_MYSQLD = 'C:/tools/mysql/bin/mysqld.exe'
mvn -pl rag-backend -am -Pmysql-it verify
```

报告：`rag-backend/target/failsafe-reports/`；普通测试报告仍在 `surefire-reports/`。本机完整日志为 `.workbuddy/mysql-p0/mysql-it-final-verify.log`。Failsafe 明确使用 `target/classes`，避免 Spring Boot 重打包后的可执行 JAR 改变测试类路径。

## 验证范围

统一脚本的完整 `verify` 已通过：普通测试 161 项中 160 项通过、1 项按原有环境配置跳过；真实 MySQL 验收 8 项全部通过、无跳过；打包成功，`git diff --check` 通过。运行后检查没有遗留 MySQL 测试进程或故障 Worker。

以上为 P0-1 当次结果。后续 [P0-2 生命周期收敛](agent-run-lifecycle-refactoring.md) 新增真实 MySQL 发布回滚用例，当前 MySQL 用例总数为 9；完整最新结果见 P0-2 报告。

验收使用现有 Flyway 依赖；它会提示 MySQL 8.4 新于其已声明验证的版本。实际迁移与校验通过，但没有因此宣称所有 MySQL 8.x、Flyway 组合均已认证，也没有在本轮混入依赖升级。

授权服务和内部工具派发器在测试上下文中替换为受控桩；SQL、Spring 事务、MyBatis、HTTP 客户端、执行器、检查点存储和故障进程使用真实实现。本轮没有验证生产身份体系、真实模型、真实外部业务系统、长时间高并发压测或多机网络分区，也未部署生产。这个结果不等于跨系统恰好一次。

## 行动记录

| 行动 | 原因与证据 |
|---|---|
| 阅读 ub-mode、POM、迁移和现有测试 | 明确 P0-1 范围，保留工作区已有修改 |
| 检查本机数据库条件 | 未发现 MySQL/Docker，采用官方 ZIP 和私有服务端进程 |
| 编写真实数据库测试并确认环境缺失时失败 | `mysql-it-red.log`，不把缺失环境静默跳过 |
| 校验官方归档并建立可重用执行入口 | 固定本轮可执行版本和校验值；运行时不进入版本管理 |
| 修复 Failsafe 类路径及测试进程生命周期 | 确保可执行 JAR 不遮蔽业务类，MySQL 监控子进程一并收尾 |
| 运行完整迁移及故障测试并保留失败报告 | `mysql-it-acceptance.log` 曾复现动作并发去重失败 |
| 确定性复现任务旧快照问题 | `mysql-job-race-red.log`，期望已有任务而得到 null |
| 修复动作和任务冲突后的当前读 | 复用唯一键约束，不增加第二套幂等表或全局锁 |
| 从统一脚本运行完整 verify | 验证普通测试、打包和真实 MySQL 门禁能够一起执行 |
| 清理已完成下载的 16 个范围片段 | 回收 281,154,912 字节；保留官方归档、可执行文件和故障证据供复查及重跑 |

当前环境未提供 crapper、dryer、mutator，未声称运行。未提交或推送现有混合工作区。
