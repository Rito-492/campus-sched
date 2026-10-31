# AN 模块验证记录（2026-10-25）

成员：冯玉琪（22220314）。main 基准：`34dc4d5`，验证代码提交：`9b76b2e`。测试源码位于 server/src/test/java/com/campus/events/activity；本次不修改状态机生产实现。

## 真实结果及范围

| 实际入口 | 结果 |
|---|---|
| 公共 server/pom.xml 的模块 test | 退出 1；CM DemoDataSeeder.java:118 受检异常未处理，默认测试没有执行 |
| 临时隔离 POM 的 ActivityStateMachineTest | 7 项，0 失败/错误/跳过 |
| 临时隔离 POM 的 ActivityServiceTest | 11 项，0 失败/错误/跳过 |
| 临时隔离 POM 的 ActivityRegistrationIT | 17 项，0 失败/错误/跳过；PostgreSQL 17.6 经 Flyway 真实迁移 |
| 隔离 verify | BUILD SUCCESS；最终运行 44.087 秒 |

真实集成使用 PM 的 AccountPort/认证/CommandExecutor、DS 的 SchedulingPortAdapter 和本模块生产代码。只有未交付的 CM ChangePort/EventPort 在测试配置中提供替身：测试替身读写随机测试 schema 中的 change_request/audit_log/outbox_event，用于验证 AN 的事务参与与回滚，不代表 CM 的业务规则或通知消费实现通过。没有 H2、生产替身、跳过测试或伪成功。

本机 portable JDK 21.0.12.1、Maven Wrapper 3.9.11、PostgreSQL 17.6 均在 server/target 中。仅使用本机 campus_test 和随机 an_<UUID> schema，不连接开发数据库；测试类结束清理自身 schema，本轮结束停止测试实例。

## 可复现入口

CM 编译问题修复后，标准命令（先按仓库规则加载真实测试库环境）是：

```powershell
./server/mvnw.cmd -B -ntp -f server/pom.xml `
  '-Dtest=ActivityStateMachineTest,ActivityServiceTest' `
  '-Dit.test=ActivityRegistrationIT' verify
```

当前 baseline 需临时隔离已知 CM 编译问题。实际采用的 POM 是公共 server/pom.xml 的复制品，放在 server/target/an-build/pom.xml（不入库），仅做以下构建配置调整：

1. build.sourceDirectory 指向 server/src/main/java 的本机绝对路径，testSourceDirectory 指向 server/src/test/java，resources 指向 server/src/main/resources。
2. 在 maven-compiler-plugin 中加入 excludes `com/campus/events/dev/DemoDataSeeder.java`；testIncludes 只包含 `com/campus/events/activity/**/*.java`、`com/campus/events/registration/**/*.java`。
3. 其余依赖、Java 版本、Surefire/Failsafe 和应用源码沿用公共 POM；默认编译仍单独实际执行并报告失败，没有用隔离结果替代它。

实际命令：

```powershell
# JAVA_HOME 指向本机 Java21；三个 TEST_DB 环境变量必须由本机安全配置提供，不在文档中存凭据。
./server/mvnw.cmd -B -ntp -f server/target/an-build/pom.xml `
  '-Dtest=ActivityStateMachineTest,ActivityServiceTest' `
  '-Dit.test=ActivityRegistrationIT' verify
```

本轮另传 `-Dmaven.repo.local=<本机server/target/maven-repository>`，MAVEN_USER_HOME 指向 server/target/maven-home，仅用于缓存位置。原始日志在本机 server/target/an-default-test.log、an-initial-unit-failure.log、an-before-race-fix.log、an-verify.log；最终 XML/TXT 报告位于 server/target/an-build/target/surefire-reports 和 failsafe-reports。

## 能发现错误的回归

- 初始单测实际发现旧 ActivityStateMachineTest 的撤回断言对象错误；改为检查新草稿后通过，状态机生产实现未改。
- 初始真库测试实际复现 3 次取消/改期竞争中的 2 次失败。活动 Repository 已改成先锁主行再读版本，最终 3 次竞争均通过。
- waitingActivityLockReadsVersionCommittedWhileItWasWaiting：用数据库 pg_stat_activity 确认第二事务已在等待活动锁，再允许第一事务提交新版本，断言等待者读到新版本。没有固定长 sleep 掩盖顺序。
- onlyOneConcurrentStudentGetsLastPlace：20 个独立线程/事务，CountDownLatch 同时起跑；1 个成功、19 个 CAPACITY_FULL，数据库 ACTIVE 数为 1、锁版本只增加 1。
- completeHttpWorkflowUsesAllActivityRoutes：真实令牌和数据库，覆盖 API-12～23 全部路由及统一响应；学生读取名单得到 403。
- 当前身份/资格变化、完整审批类别、自审回避、不可变版本、旧锁版本、同键创建重放、重复 JOIN/WITHDRAW、改期冻结分别有断言。
- 事件写入后注入异常，核对活动/预约/报名/改期/审计/outbox 及 receipt 整体回滚；改期 activatePlan 后故障核对版本指针与 ACTIVE 报名版本回滚。

上述为模块证据。真实 CM 端口、通知消费者、跨模块改期业务、应用重启恢复、QA 全套验收和 E2E 尚未完成。

## PR #11 评审修复复验（2026-10-29）

实际基准仍为 main `34dc4d5`（本轮fetch完成，无新main）；被修复提交 `9b76b2e`、`0886c8b`。采用上述同一便携JDK/PG17.6和隔离POM，没有更改公共构建或生产端口替身约束。

- 先在旧实现运行新增回归：25项单测中2失败、2错误、0跳过，暴露mine角色、取消报名详情、非所有者提前锁人员和发布额外改期门禁；保留[修复前日志](../../artifacts/test-results/an-2026-10-29/an-review-before.log)。
- 修复后同一隔离verify：**25项单测（ActivityServiceTest 18、原状态机7）+22项真实PostgreSQL IT，0失败/错误/跳过**；日志BUILD SUCCESS。新增mine的所有者/工作人员/历史并集去重、状态筛选和非申请人403；无工作人员关系的报名者在取消后读详情/本人列表、名单仍403；Service层已开始/错误状态/资源风险发布负向；HTTP失败信封及资源冲突details；待改期发布成功且报名继续冻结，public/review HTTP可见。
- 默认公共POM模块test本轮仍退出1：CM DemoDataSeeder.java:118受检异常未处理；默认测试未运行，不计通过。见[默认日志](../../artifacts/test-results/an-2026-10-29/an-review-default.log)。
- [最终verify日志](../../artifacts/test-results/an-2026-10-29/an-review-verify.log)、三份最终JUnit XML/TXT、实际隔离POM及源码SHA-256均归档在[本轮AN证据目录](../../artifacts/test-results/an-2026-10-29/)。原始统计由JUnit报告产生；检查证据未包含本轮数据库凭据。测试结束清理独立schema并停止本轮便携实例。

没有重复运行未变化的全项目/E2E或声称真实CM端口通过。评审响应、仍需PM定口径的建议项见[交叉评审修复说明](activity-review-response-v1.0.md)。

## 2026-10-29 后续边界与幂等回归

新增 ActivityRegistrationIT 四项测试：其余写路由同键重放（编辑、提交、撤回、审批、确认、发布、报名/退出、取消），同键换请求体/路由拒绝，容量0/-1/大于预计人数时创建与编辑拒绝且无部分写入，过去安排在首次资源确认时拒绝且不写预约/事件。重放核对完整原响应信封，最终核对版本、审批、预约、报名及取消事件数量。取消/改期竞争由3次扩至10次；重复次数不等同于独立场景数。

实际执行沿用上节隔离POM命令（只排除CM种子编译阻塞，未修改公共POM）：`./server/mvnw.cmd -B -ntp -f server/target/an-build/pom.xml -Dtest=ActivityStateMachineTest,ActivityServiceTest -Dit.test=ActivityRegistrationIT verify`，使用本机target Maven缓存和环境变量。2026-10-29 21:23退出0，25单测+33真实PostgreSQL IT，0失败/错误/跳过。测试schema清理完成，便携数据库已停止。原始日志、XML/TXT、隔离POM与源码SHA256见[本轮证据](../../artifacts/test-results/an-2026-10-29-followup/)。本轮没有重跑默认构建；此前CM种子编译阻塞仍是已知限制，CM两个端口仍为测试替身。

## 2026-10-31 标准POM复验

基线feb0d70已包含Seeder修复。本轮使用标准server/pom.xml执行AN测试集合，25单测+35真实PostgreSQL IT通过，0失败/错误/跳过。此前隔离POM记录仅为历史证据。CM真端口仍缺失，测试替身仍保留；完整命令、覆盖与阻塞见[接入检查](activity-cm-integration-v1.0.md)，证据见[an-2026-10-31](../../artifacts/test-results/an-2026-10-31/)。
