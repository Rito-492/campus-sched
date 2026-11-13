# 调度模块真实集成接收与验证 v1.0

日期：2026-11-02；成员：黄方勇 55230315；任务：DS-01～05 续验，重点 DS-02/04/05 的真实调用链。开发任务包仍为 v3.0。

本轮从本人分支 `work/55230315-scheduling` 的 `7521511` 快进到最新 `origin/main=b8be7e48c227627c2c6e24aee534cf727200faf6`。未 reset，保留开工时未跟踪的旧证据和文档。Git 身份沿用 Kimcra1；本轮改动未提交、未推送。

## 接收结论

AN 的 ActivityPort、CM 的 ChangePort/EventPort/通知消费者和 UI 已进入 main，前次交接中的“真实端口尚未交付”已不再是当前阻塞。既有 DS-01～05 实现继续使用；本轮通过真实后端 HTTP 适配器和 PostgreSQL 验证端口组合，没有新增生产替身或改动公共契约。

最终标准 Maven Wrapper/原始 pom：**77 项单元测试、44 项 DS 数据库 IT 通过，失败/错误/跳过均 0**。全量后端基准仍失败，不能据此登记 M4 或全部 D-AT/N-AT 验收完成。未执行本轮浏览器/E2E/前端检查，未修改课程 Word/PDF。

## 已完成的可执行交付

新增 `server/src/test/java/com/campus/events/resource/ResourceWorkflowIT.java`，仅以 SQL 装载测试账号/授权，通过真实 API 创建资源和活动，并执行提交、审批、预约、发布、报名及改期。全部业务端口、幂等/认证、审计、outbox 和通知消费者使用生产实现；测试只提供固定 Clock（2030-10-19T00:00:00Z）。取消该测试上下文的后台定时触发，显式调用真实 consumer.drain，避免依赖 sleep 或异步时序。

| 用例 | 真实断言 | 验收关联 |
|---|---|---|
| firstConfirmationReplaysSameKeyAndRejectsChangedRequestWithoutExtraWrites | 首次确认成功；相同 key/body 重放相同信封；异体复用返回 409；预约、审计/outbox 不重复 | D-AT-03/08 |
| twoRealConfirmRequestsProduceOneWinnerWithoutPartialReservations | 两个已审批活动的真实 HTTP 请求经栅栏同时起跑；恰 200/409 各一个；输方仍未预约且无部分占用 | D-AT-07 |
| conflictPreviewReturnsJointAlternativeWithoutWritingBusinessState | 原演示几何：场地冲突、设备需求总量 3/库存 2，缺 1；首选同时间换场地和同类设备；版本/预约/改期/事件未变 | D-AT-09 |
| realChangeSwitchesReservationsRegistrationAndDeliversVersionedNotifications | 工作人员确认、批准人同意、资源复核后 APPLIED；排除自身旧设备占用；旧预约 RELEASED、新预约 ACTIVE、报名均 v2；审计/outbox 关联 v2；真实投递给所有者/工作人员/报名人各一次；已读不改确认结果 | D-AT-10/13/15/19 |
| lateResourceCompetitionCommitsConflictAndKeepsRiskyOriginalArrangement | 预览可行后另一活动抢占目标，最终 409 且持久化 CONFLICTED；原 v1/报名/预约保留，原设备故障的 AT_RISK 仍实时可见 | D-AT-12/18 |
| finalConfirmationUsesLatestEligibilityAndLeavesOriginalReservations | 改期审批后通过 API-40 收紧目标设备资格，最终 403 RESOURCE_INELIGIBLE；原安排不变、改期仍待复核、无成功回执 | N-AT-03/04 |
| reservationInsertFailureRollsBackWholeChangeAndSameKeyCanRetry | activatePlan 后，在预约插入触发器注入数据库异常；500 时版本指针/版本行、报名、全部预约、改期、审计/outbox、回执均恢复；撤掉测试触发器后用同 key/body 重试成功 | D-AT-14 |

这些是 DS 接收真实模块后的回归断言，覆盖所列场景的一部分或主线，不替代 QA 的完整验收清单、通知重试/取消竞争及浏览器检查。

同步更新 ResourceSchedulingIT：删除已不适用的“缺少 ChangePort”历史测试；真实切换缺端口时直接失败，不再跳过；故障 HTTP 必须通过真实 EventPort 返回 201，移除允许 503 也通过的兼容路径。原先隔离空 EventPort、异常事件回滚的防护测试仍保留。

迁移方面，本轮不改 V1/V2；在 migration-register.md 补登 main 已有的 V2__outbox_claim_token.sql。空 schema 应用 V1→V2、现有唯一/外键/不可变约束及真实通知投递通过；未进行已有生产数据升级演练。

## 实际命令与结果

本机 Java 21.0.6 / Maven Wrapper 3.9.11 / PostgreSQL 17.6。仅使用 127.0.0.1:5440/campus_test，每类随机测试 schema；未访问开发库。证据位于 `artifacts/test-results/ds-2026-11-02/`。

```powershell
. ./scripts/load-env.ps1
$env:JAVA_HOME='D:/Java_jdk'
$env:MAVEN_USER_HOME=(Join-Path (Get-Location) 'server/target/maven-home')
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5440/campus_test'
./server/mvnw.cmd -B -ntp -f server/pom.xml `
  '-Dmaven.repo.local=C:/Users/35049/Desktop/campus-sched/server/target/maven-repository' `
  '-Dit.test=ResourceWorkflowIT,ResourceSchedulingIT,SchemaIT' `
  '-Dtest.output.dir=C:/Users/35049/Desktop/campus-sched/artifacts/test-results/ds-2026-11-02' verify
```

JDK 和缓存路径为本机路径，接收人按实际环境调整；数据库账号/密码从本地环境加载，不写入证据。

| 运行 | 退出及实际结果 | 证据 |
|---|---|---|
| 同步 main 后的原始 DS 检查 | 0；77 单测通过，38 IT 中 37 通过、1 跳过（旧缺依赖场景） | baseline-wrapper.log |
| 原始 main 全量 verify（未指定 test/it.test） | 1；77 单测通过；85 项 IT 中 9 错误、1 跳过，其余 75 通过；独立策略评测 2 项也实际完成 | full-baseline.log、full-baseline-suites.json |
| 新增测试初次编译 | 1；初次缺 login 辅助方法，补齐后暴露 Role 通配导入歧义，均已修复 | workflow-initial.log、workflow-first.log |
| 新增测试首次运行 | 1；7 项中 3 通过、1 失败、3 错误。修正测试 SQL 将 UUID key 与 text 列比较的问题，以及设备 required/available 总量断言 | workflow-run.log |
| 最终 DS verify | 0；77 单测 + 44 IT 全通过，0 失败/错误/跳过 | ds-final-verify.log、ds-final-suites.json、unit-suites.json、三份测试文本报告 |

完整 main 基准命令与上方相同，但移除 `-Dit.test`，将 `-Dtest.output.dir` 改为 `server/target/ds-nov02-full-baseline`，另加 `-Dtest.data.dir=C:/Users/35049/Desktop/campus-sched/test-data`。没有隔离 pom、编译排除或修改共享测试配置；指定 IT 的结果只称为 DS 范围通过。

日志保留真实失败与测试故障注入堆栈；最终日志中的 DS预约写入失败为 D-AT-14 测试主动注入，最终 JUnit 结果为通过。日志仅规范行尾空白、检查已知本地口令，替换计数为 0；未导出包含环境属性的 Maven XML。证据目录保存精简结果及源码 SHA-256。

## 全量基准的具体阻塞与下一接收人

1. **QA 汤雨润 / PM 冀项阳**：`support/DbTestSupport.java:236` 在 resource 创建前写 user_resource_scope，触发 fk_user_resource_scope_resource。8 个 acceptance 测试类在装载阶段失败，SchedulingBenchmarkIT 没有产生本轮正式评测结果。已 fetch 的 QA 分支 `origin/work/55230316-qa-feb0d70` 顶端为 `19ae94b`，含装载器及验收修复，尚未合入本轮 main；由所有者交叉评审接收后重新跑全量，不能把分支历史报告当作 main 通过。
2. **AN 冯玉琪**：`activity/ActivityRegistrationIT.java` 的测试 Ports.eventPort 与生产 eventPortAdapter 同时存在，ChangeService 初始化抛 NoUniqueBeanDefinitionException；需要所有者更新模块测试上下文。DS 没有改他人测试或用覆盖 Bean 参数掩盖问题。
3. **QA / CM / UI**：依据本轮真实服务回归继续完整取消/并发/通知重试/浏览器验收。正式 D-AT-22 待 QA 修复接收后重跑；本轮未新造 results.csv。

DS 已在本地执行上述端口组合接收验证；下游对本轮新测试与文档的接收反馈：**尚未取得**。没有自动发消息、创建/合并 PR、推送分支或登记发布基线。课程平台是否提交仍无回执，本轮未作提交动作。
