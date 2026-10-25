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
