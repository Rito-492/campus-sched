# 缺陷登记 v0.3

> 归属：测试经理（55230316）。格式依据课程课件《6.2 缺陷记录与测试评估》：缺陷标识、用例标识、测试人员、缺陷等级、打开日期、状态 + 概要/再现步骤/隔离。
> 等级：A（关键，严重影响系统运行）/B（高，影响系统运行）/C（中，不影响运行但必须修改）/D（低，所提建议）。状态流转：open→assign→test→close（修复后回归通过才 close）。
> **如实登记**：只记录实际发现的问题与复验。Seeder编译缺陷曾于2026-10-26真实复现，PR #10已作为唯一来源合入；本轮默认Maven已在真实PostgreSQL上运行，逐项区分支撑配置问题、产品集成失败和未执行场景。未运行的产品场景不作为缺陷。

## 缺陷清单

| 缺陷标识 | 用例标识 | 测试人员 | 等级 | 打开日期 | 状态 |
|---|---|---|---|---|---|
| DEF-QA-2026-01 | 默认compile / SeederIT前置编译 | 汤雨润（55230316） | B | 2026-10-26 | close（编译复验通过） |
| DEF-QA-2026-02 | 默认verify / Spring Failsafe数据源初始化 | 汤雨润（55230316） | C | 2026-10-31 | close（profile/schema绑定复验通过） |
| DEF-QA-2026-03 | D-AT-22 SchedulingBenchmarkIT 场景装载 | 汤雨润（55230316） | C | 2026-10-31 | close（最终verify通过该IT） |
| DEF-QA-2026-04 | D-AT-02/03/07/08/16/20、N-AT提交阻塞 / EventPort | 汤雨润（55230316） | B | 2026-10-31 | assign（CM） |
| DEF-QA-2026-05 | D-AT-02/10-17/19/21、N-AT候选/待处理阻塞 / ChangePort | 汤雨润（55230316） | B | 2026-10-31 | assign（CM） |
| DEF-QA-2026-06 | D-AT-21 calendar URI偏移编码 | 汤雨润（55230316） | C | 2026-10-31 | close（QA修正URI编码，真实PG子用例通过） |
| DEF-QA-2026-07 | D-AT-22 demo资源扩展污染候选集合 | 汤雨润（55230316） | C | 2026-10-31 | close（benchmark参考集隔离后复验通过） |

## 明细

### DEF-QA-2026-01
- 概要：旧main的CM Seeder `seed()` 调用了声明 `throws Exception` 的 `seedActivity()`，导致默认构建编译失败。
- 再现步骤：在2026-10-26旧基准运行 `./server/mvnw -f server/pom.xml -DskipTests compile`。
- 隔离：Linux；无需数据库/密码；旧基准稳定复现；历史证据见 `artifacts/test-results/qa-2026-10-26/default-compile.log`。
- 预期 vs 实际：旧main报未处理受检异常，exit 1；PR #10合并后本轮在feb0d70的默认Maven `verify`编译主源码102个和测试源码28个成功。
- 证据：旧复现日志`artifacts/test-results/qa-2026-10-26/default-compile.log`；最终真实PG Failsafe报告`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-reports/TEST-com.campus.events.dev.DemoDataSeederIT.xml`（3/3通过），以及默认verify完整编译成功记录。
- 责任接收人：CM 杨瀚霖（55231031）；修复已随PR #10合入。QA复验：2026-10-31 compile/testCompile成功，缺陷关闭。

PR #8/#10的重复Seeder源码已由PR #10作为唯一权威合并，PR #8关闭；不对重复副本重复登记。CM `DemoDataSeederIT`三项与QA新增`SeederRollbackIT`失败回滚项均在最终默认verify的真实PostgreSQL下通过。

### DEF-QA-2026-02
- 概要：首次真实库`verify`中QA `@SpringBootTest`验收上下文无Datasource URL，Failsafe 119项中出现33个context Errors。
- 再现步骤：测试环境设置`TEST_DATABASE_URL`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`、`DEMO_PASSWORD`并运行`./server/mvnw -f server/pom.xml verify`。
- 隔离：初次运行基线`a48367e861dfc06ec99e8da35fbff89b44926636`、PostgreSQL 17.11；相关Acceptance测试未激活`test` profile，`DbTestSupport`创建schema但未将Spring datasource/Flyway绑定到该schema。
- 预期 vs 实际：初次运行因`Failed to determine suitable jdbc url`造成33个上下文错误；QA支撑加入test profile、scoped datasource URL及Flyway schema后，最终commit `a697d90` 默认verify中所有测试上下文均能启动，Failsafe 120项实际运行、0跳过；本轮最终仍有25 failures/2 errors，但均为业务/场景断言或评测数据问题，不再是本缺陷。
- 证据：`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-profile-fix.md`记录初次错误及修复；最终结构化结果为同目录`failsafe-summary.xml`及逐类`TEST-*.xml`。复验源码commit=`a697d90`。
- 责任接收人：QA汤雨润（55230316）；修复和profile/schema绑定已在真实PG全套verify中验证，DEF-QA-2026-02关闭。

### DEF-QA-2026-03
- 概要：D-AT-22的SchedulingBenchmarkIT合成场景装载违反`activity`到`activity_version`延迟外键约束，评测在策略调用前即中止。
- 再现步骤：设置真实PostgreSQL测试环境，运行`./server/mvnw -f server/pom.xml -Dit.test=SchedulingBenchmarkIT verify`。
- 隔离：原运行源码基于feb0d70的QA提交`a48367e`；PostgreSQL 17.11；错误在`loadScenario`插入activity时发生。事务外每条JDBC更新自动提交，使activity先于version提交。
- 预期 vs 实际：预期8场景分别运行FIRST_FIT/RANKED并输出原始JSON；实际报`fk_activity_current_version`，策略评测未开始。
- 修复：QA commit `98796ed` 将每场景activities/versions/reservations/faults放入同一事务，并显式`?::jsonb`。
- 证据：复现与根因摘要`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/scheduling-benchmark-loader-repro.md`；修复后`failsafe-reports/TEST-com.campus.events.acceptance.SchedulingBenchmarkIT.xml`（1/1通过，生成160测量行）。
- 责任接收人：QA汤雨润（55230316）；修复已在commit `98796ed`合入并于最终默认verify真实PG复验通过，DEF-QA-2026-03关闭。策略期望对照不一致另由DEF-QA-2026-07跟踪。

### DEF-QA-2026-04
- 概要：生产EventPort未就绪，活动提交/审批/资源确认相关真实HTTP请求返回503 `INTERNAL_ERROR`（“活动事件端口尚未就绪”），多个验收场景在目标断言前停止。
- 再现步骤：用真实测试库运行默认`verify`，例：PermissionsIT提交活动、BookingConcurrencyIT准备并发资源确认、IdentityEligibilityIT提交活动。
- 隔离：feb0d70产品基线，PostgreSQL 17.11；失败不是Mock/隔离测试；ActivityService取得不到EventPort时按设计fail-closed返回503。
- 预期 vs 实际：预期真实活动事件写入并继续工作流；实际EventPort无生产Bean，HTTP 503。受影响尝试包括D-AT-07/08/20和N-AT-01/02/04/05/06等；这些场景未到业务目标断言，不记业务规则失败或通过。
- 证据：`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-reports/TEST-com.campus.events.acceptance.BookingConcurrencyIT.xml`、`TEST-com.campus.events.acceptance.IdentityEligibilityIT.xml`、`TEST-com.campus.events.acceptance.PermissionsIT.xml`。
- 责任接收人：CM杨瀚霖（55231031，EventPort生产实现）；回归待实现后由QA执行。

### DEF-QA-2026-05
- 概要：生产ChangePort/改期API尚未就绪；活动详情等读取在ChangePort缺失时返回503“改期端口尚未就绪”，新建改期端点返回404；报名冻结路径因此也无法完成。
- 再现步骤：默认verify运行ChangeRollbackIT、CancellationRaceIT、NotificationConsistencyIT或RegistrationCapacityIT。
- 隔离：feb0d70产品基线，PostgreSQL 17.11；活动显示/报名冻结需要change查询，缺失时按fail-closed行为停止。
- 预期 vs 实际：预期改期详情/确认工作流及冻结检查可用；实际相关测试在准备阶段遇503或创建改期遇404。D-AT-02、10-15、17、19/21等跨模块观察点未完成；D-AT-16诊断复验commit `37a1ac1`确认20/20 JOIN均为503 `INTERNAL_ERROR`，ActivityService在容量判断前因ChangePort缺失无法读取hasPending，需端口就绪后复测，不归因为容量算法。
- 证据：`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-reports/TEST-com.campus.events.acceptance.ChangeRollbackIT.xml`、`TEST-com.campus.events.acceptance.RegistrationCapacityIT.xml`、`TEST-com.campus.events.acceptance.CancellationRaceIT.xml`、`TEST-com.campus.events.acceptance.NotificationConsistencyIT.xml`；D-AT-16分布诊断见`artifacts/test-results/qa-2026-10-31/d-at16-diagnostic/TEST-com.campus.events.acceptance.RegistrationCapacityIT.xml`。
- 责任接收人：CM杨瀚霖（55231031，ChangePort/改期API）；回归待实现后由QA执行。

### DEF-QA-2026-06
- 概要：D-AT-21 calendar HTTP用例起初带`+08:00`偏移的`from/to`返回400 `VALIDATION_ERROR`；根因是QA测试把含原始`+`的String交给RestTemplate URI模板处理，服务器表单解码将`+`解释为空格，并非API拒绝合法offset。
- 再现步骤：旧PermissionsIT.tc2101使用`UriComponentsBuilder...build().encode().toUriString()`后以String调用`rest.exchange`；日志中可见原始`+08:00`。
- 隔离：feb0d70、PostgreSQL 17；改为URI变量严格编码`+`为`%2B`，再通过绝对URI对象发送，避免RestTemplate二次模板编码。
- 预期 vs 实际：原测试URL返回400；QA commit `514b8dc`加入URI编码断言并用URI重载发送后，`tc2101_calendarRedactsUnauthorizedActivityTitles`真实PG通过，日历脱敏断言到达。PermissionsIT其余7项仍因EventPort/ChangePort返回503阻塞。
- 证据：旧失败URL及400响应见`artifacts/test-results/qa-2026-10-31/d-at21-url-diagnostic/TEST-com.campus.events.acceptance.PermissionsIT.xml`；修复后真实PG结果见`artifacts/test-results/qa-2026-10-31/d-at21-uri-object-rerun/TEST-com.campus.events.acceptance.PermissionsIT.xml`和`context.txt`。
- 责任接收人：QA汤雨润（55230316）；这是测试客户端编码缺陷，非DS API缺陷。已修复并复验，关闭。

### DEF-QA-2026-07
- 概要：原D-AT-22评测从canonical `demo.json`加载所有资源，教师申请人可用的N-AT扩展资源3111/3112/3211/3212也进入CandidateSearch候选集合，超出`scheduling.json`只描述的3101/3102/3201/3202几何，造成SCH-01/04/08与expected不一致。
- 再现步骤：原配置真实PG Failsafe `SchedulingBenchmarkIT`后执行`npm --prefix qa run evaluate`，产生SCH-01/04/08 mismatch；随后将Benchmark reference数据隔离为基线资源集重跑。
- 隔离：旧结果commit a697d90、PostgreSQL 17；`CandidateSearch.search`从`resources.all()`枚举所有符合identity的资源。并非生产策略算法改动。
- 预期 vs 实际：旧fixture下expected与实测不同；QA新增benchmark-only `scheduling-reference.json`（仅3101/3102/3201/3202，resource_admin仅此4项scope，不是canonical demo用户快照），不改expected。commit `7ad9cfe`真实PG BenchmarkIT 1/1且evaluate exit0，commit `c3a4a2c`进一步记录reference/scenario路径及SHA-256，8场景×2策略×10测量仍全部一致。
- 证据：旧mismatch快照`artifacts/test-results/qa-2026-10-31/benchmark-reference-rerun/evaluation-prior-mismatch/summary.md`；带SHA-256的复验`artifacts/test-results/qa-2026-10-31/benchmark-hash-rerun/scheduling-evaluation.json`、`evaluate.log`；最终`artifacts/evaluation/summary.md`与`results.csv`。
- 责任接收人：QA汤雨润（55230316）隔离评测fixture；DS黄方勇（55230315）可复核候选语义。复验通过，缺陷关闭；未修改任何expected或生产代码。

```markdown
### DEF-xxx
- 概要（Summary）：……
- 再现步骤（Steps to Reproduce）：1、… 2、…
- 隔离（Isolation）：环境/版本/数据/是否可稳定复现/临时规避方式
- 预期 vs 实际：……
- 证据：日志/截图路径（artifacts/…）
- 责任接收人：……；回归记录：……
```
