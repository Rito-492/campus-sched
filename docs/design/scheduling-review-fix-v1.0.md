# PR #9 资源隐私评审修复 v1.0

日期：2026-10-21；设计师黄方勇（55230315）；任务 DS-02/03，任务包 v3.0。main 基准 `4fbda0e`；修复前分支提交 `622d6b8`。本说明追加记录，不覆盖已有设计基线。

## 原因与处理

[2026-10-20 评审](https://github.com/Rito-492/campus-sched/pull/9#pullrequestreview-5587757375) 明确要求修改两项隐私缺陷。查询 PR 顶端提交时未返回 CI status 或 workflow run；不能将本次未通过解释为 CI 测试失败。

| 评审项 | 处理与验证 |
|---|---|
| API-11 只有资源管理权也能获取关联活动 ID、标题、版本和风险 | 在构造 AffectedActivity 前，复用现有 canRead 逐活动过滤；外层资源范围检查及影响清单采用同一最新 Actor。HTTP 测试覆盖仅管理、仅完整覆盖一项活动、完整覆盖两项活动、撤销复核权限及无管理权限 403。 |
| API-09 隐藏活动 ID/标题后仍泄露 AT_RISK | activityId=null 的占用条目统一返回 NORMAL 占位，不读取该活动实际风险。有权查看时仍返回真实 AT_RISK；资源 FAULT 条目仍为 AT_RISK。测试同时验证三个占用分段、故障分段以及所有者可见信息。 |
| API-10 复用同一影响清单 | 返回值同样按活动查看权限过滤；内部事件从完整影响清单生成，不因调用者看不到活动而漏发。使用仅测试中的 EventPort 记录器验证活动 ID、版本、收件人与持久化故障/预约，不冒充真实 outbox 链路通过。 |
| 列表与日历排序建议 | 核对当前 ResourceRepository：资源和故障列表已按 created_at DESC、id ASC；日历按源记录 created_at DESC、id ASC，再按 starts_at、segment 排同一预约的分段。新增真库用例以时间并列、反向时间顺序和混合故障/预约验证，保持 SQL 不变。 |
| IT 环境配置建议 | common/03 §3 标准入口先点加载 scripts/load-env.ps1。ResourceSchedulingIT 现在一次检查 TEST_DATABASE_URL、TEST_DB_USERNAME、TEST_DB_PASSWORD、DEMO_PASSWORD，缺配置时在创建 schema 前列出缺失变量和执行入口。依照“缺真实库不能绿色通过”的约定，明确失败，不跳过。 |

HTTP 字段/枚举、公共端口和数据库迁移未改变。NORMAL 仅是被遮蔽占用的固定风险占位；消费者需结合 activityId=null 和“已占用”展示，不将其解释为该活动无风险。

## 真实验证

环境：Java 21.0.6、Maven Wrapper 3.9.11、PostgreSQL 17.6，独立本机 `5440/campus_test`，每类随机 schema。复用先前 target 下的隔离 pom，仅排除尚不能编译的 CM dev 包，仍编译所有测试源码；共享 pom 未改动。该范围不是全工程或 M2/M3/E2E。

| 执行 | 结果 |
|---|---|
| 先加回归测试、保持旧生产代码，执行三项隐私用例 | 3 项失败，0 错误/跳过，分别捕获两条未授权活动、泄露 AT_RISK、故障登记响应未裁剪。 |
| 修复后 DS 模块单测 | 23 项通过，0 失败/错误/跳过。 |
| 修复后 ResourceSchedulingIT + SchemaIT | 28 项通过，0 失败/错误/跳过，包含隐私、稳定排序、并发、回滚、资格、幂等与迁移。 |
| 标准 server/pom.xml test 与 verify | 退出码 1，均在 CM DemoDataSeeder.java:118 未处理 Exception 的生产编译阶段停止，未执行默认测试。 |
| 清除四个环境变量，单独调用已编译的 Failsafe 回归 | 预期非零退出，缺配置明确阻塞；不是业务回归通过。 |

本次 API-11 测试中的两项活动均通过真实 SchedulingPort 创建预约，查询后仍有 1 条故障、4 条预约且活动风险 AT_RISK；证明只裁剪返回数据，不改变业务安排。旧实时风险测试补授 RESOURCE_CONFIRM，使其检验合法查看者的完整影响清单。

## 复现命令与证据

标准入口（在仓库根目录执行，先配置本机专用测试数据库与 .env）：

```powershell
. ./scripts/load-env.ps1
./server/mvnw.cmd -f server/pom.xml test
./server/mvnw.cmd -f server/pom.xml verify
```

本机 Windows Wrapper 未命中既有 Unix Wrapper 缓存并尝试下载，因此实际运行采用已缓存的 Git Bash Wrapper；最初的沙箱启动/数据库连接失败不计通过。以下是修复后实际执行的隔离验证命令：

```powershell
. ./scripts/load-env.ps1
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5440/campus_test'
$env:JAVA_HOME='/d/Java_jdk'
$env:MAVEN_USER_HOME='/c/Users/35049/Desktop/campus-sched/server/target/maven-home'
& 'D:/Git/Git/bin/bash.exe' ./server/mvnw -B -ntp `
  -f server/target/ds-build/pom.xml `
  '-Dmaven.repo.local=C:/Users/35049/Desktop/campus-sched/server/target/maven-repository' `
  '-Dtest=CandidateRankingTest,CapacitySweepTest,TimeIntervalTest,StrategyEvaluatorTest,ScheduleQueryServiceTest' `
  '-Dit.test=ResourceSchedulingIT,SchemaIT' verify
```

标准 test/verify 实测使用同一 Wrapper/缓存参数，但 `-f server/pom.xml` 且不限定测试；均复现编译错误。隔离 pom 见证据中的 isolated-pom.xml，接收人需调整本机绝对路径。

修复前复现时，将上述 `-Dit.test` 改为：

```text
-Dit.test=ResourceSchedulingIT#faultHttpListFiltersEachActivityAndRechecksCurrentScope+calendarSegmentsAreRedactedAndHttpUsesCommonEnvelope+faultResponseRedactionDoesNotSuppressInternalActivityEvents
```

缺配置检查是在当前 shell 清除上述四个环境变量后，使用同一隔离 pom 和 Wrapper 执行：

```text
-Dit.test=ResourceSchedulingIT#faultHttpListFiltersEachActivityAndRechecksCurrentScope failsafe:integration-test failsafe:verify
```

该命令复用此前已经编译的测试类，用于单独核实缺配置错误，不替代完整 verify。

[证据目录](../../artifacts/test-results/ds-2026-10-21/) 包括成功运行日志、7 份 Surefire/Failsafe 文本报告、修复前失败摘要、默认构建失败、缺环境变量诊断、隔离 pom 和源码哈希。修复前原始 MockMvc 失败输出含请求会话信息，归档仅抽取测试结果及失败断言行；其他归档文本仅清除行尾空白。未提交密码、令牌、XML 环境属性、数据库或构建缓存。

## 交接限制

两个必须改项已有代码及回归证据，是否放行仍由 PM/QA 交叉复审；PR 保留 Draft，不自审自批、不合并 main。默认工程仍需 CM 修复编译；API-10 成功全链路仍需真实 EventPort。其他历史集成缺口见 handoff，未在本轮复跑，不重复登记为新结果。
