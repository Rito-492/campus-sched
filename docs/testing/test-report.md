# 测试报告 v0.4（feb0d70基线真实数据库回归）

> 归属：测试经理（55230316）。仅按真实运行且可查证据统计；静态检查、用例列举、代码存在和模块替身测试均不冒充完整跨模块验收。

## 基本信息

- 日期：2026-10-31（Asia/Shanghai）
- QA分支：`work/55230316-qa` @ `a697d90871c6126bf6f42f26c938be9a1e9792cc`
- main基线：`origin/main=feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`；QA分支已变基到该基线。
- PR状态：#8 CLOSED；#10/#11/#12 MERGED。
- 同步前修改和未跟踪文件已备份于`.git/qa-pre-sync-backup-2026-10-31/`，SHA256校验通过；`.review-tmp/`保持未跟踪、未纳入QA改动。
- Maven运行环境：JDK 27.0.1.0.3（Arch Linux/WSL2），项目编译目标Java 21；**与基线JDK 21不一致，发布前仍需在JDK 21复验**。
- 数据库：临时PostgreSQL 17 `campus_test`，本机55439端口、按测试类隔离schema；DB密码及`DEMO_PASSWORD`随机生成于当前进程，未写入源码/日志；测试后按容器名/标签/镜像校验并清理。

## 正式运行结果

| 类别 | 命令/观察 | 结果 | 证据 |
|---|---|---|---|
| 默认Maven验收 | `./server/mvnw -f server/pom.xml verify` | 编译通过；Surefire 70/70通过；Failsafe 120项：93通过、25失败、2错误、0跳过；整体exit 1 | `artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/default-verify.exit`、`failsafe-summary.xml`及逐类XML；原始stdout留在本地工作区 |
| Seeder首次导入/幂等 | `DemoDataSeederIT` | 3/3通过：首次授权、撤权后重导不恢复、删用户重导新授权正确 | `artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-reports/TEST-com.campus.events.dev.DemoDataSeederIT.xml` |
| Seeder失败整体回滚 | QA新增`SeederRollbackIT` | 1/1通过：末尾报名FK故障后组织、用户、角色、资源、范围、活动、版本、预约、报名全部无残留 | `artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-reports/TEST-com.campus.events.acceptance.SeederRollbackIT.xml` |
| DbTestSupport | `DbTestSupportIT` | 1/1通过，真实PostgreSQL/隔离schema；授权、活动版本、jsonb断言通过 | `artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/failsafe-reports/TEST-com.campus.events.support.DbTestSupportIT.xml` |
| 调度算法 | `SchedulingBenchmarkIT` | 1/1通过；8场景×2策略×10次，160条真实测量行，最多检查候选数不超过300 | `artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/scheduling-evaluation.json`及`failsafe-reports/TEST-com.campus.events.acceptance.SchedulingBenchmarkIT.xml` |
| 期望值评测 | `npm --prefix qa run evaluate` | **exit 1**；SCH-01、SCH-04、SCH-08实测代价与场景期望不一致；未修改期望值迎合结果 | `artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/evaluate.log`、`artifacts/evaluation/summary.md`、`results.csv` |
| 模块真库回归 | ResourceSchedulingIT / ActivityRegistrationIT / FoundationIT / SchemaIT | 20/20、33/33、21/21、8/8通过；AN IT使用测试上下文的ChangePort/EventPort替身，只是模块级部分证据 | final run逐类Failsafe XML（结构化结果） |
| 静态支撑 | API合同、数据引用、`npx tsc --noEmit`、`npx playwright test --list` | 通过：40 API/9枚举/15 DTO；数据引用完整；Playwright列出29项/7文件，仅列举未执行 | `artifacts/test-results/qa-2026-10-31/static/` |

## DbTestSupport与Seeder判定

- 对唯一领先提交`0bb9aac`的DbTestSupport修复已复核并基于feb0d70重放：单数据文件事务、资源先于user_resource_scope、LocalTime绑定SQL time、activity_version显式`::jsonb`；真实库DbTestSupportIT通过。
- 首次`verify`因QA Spring验收上下文未激活test profile而出现数据源URL错误；QA commit `98796ed` 增加test profile、按类scoped datasource URL和Flyway schema，之后所有上下文均加载并执行Failsafe。
- PR #10作为唯一Seeder来源已合入，#8已关闭。CM DemoDataSeederIT三项和QA SeederRollbackIT一项均在真实PostgreSQL通过；密码均从运行环境注入，没有源码默认值或日志回显。

## 30场景覆盖统计

- 依据[覆盖矩阵](coverage-matrix.md)：RfT=30；完整通过3项（D-AT-04/05/06，限DS真实PG模块测试范围）、部分覆盖17项、阻塞7项、未执行2项（D-AT-09/23）、失败1项（D-AT-22评测期望不一致）。
- 按“至少有一个目标断言实际运行”口径Tx=21（通过+部分+失败），Ts=3（完整成功且无缺陷）；Tx/RfT=70%，Ts/RfT=10%。阻塞/未执行不计入Tx。
- D-AT-16真实HTTP并发JOIN观察到0/20个200，但ChangePort缺失使请求在`hasPending`前置返回服务不可用，需端口交付后重验，不能据此判容量算法错误。D-AT-21日历offset参数400待DS核实。
- 多个测试虽使用真实PostgreSQL，ActivityRegistrationIT中的CM端口是测试替身；它们只作为部分模块证据，不能推出M0–M4或完整产品已通过。

## 主要未完成项与提供者

1. CM杨瀚霖（55231031）：提供生产EventPort、ChangePort/改期通知API；解除多项真实HTTP验收503/404阻塞，完成后QA回归。
2. DS黄方勇（55230315）：核查API-09带时区offset的`from/to`为何400；与QA共同核查SCH-01/04/08场景几何、算法结果和expected定义，不要先改预期值。
3. UI杨畅（17231018）：当前`web/package.json`缺失且本机没有`docker compose`插件，Playwright真实E2E、视觉验收及D-AT-23/24未执行。
4. CM/全组：SeederIT现有三项加QA新回滚项均已跑；生产端口、干净发布环境及Java 21基线复验仍未完成。

## PR与接收状态

本地QA分支已基于feb0d70并提交QA支撑、评测数据装载和Seeder回滚测试；**未推送、未创建远端PR**。固定远端QA分支仍指向旧历史，更新需要force-with-lease；考虑用户要求保留/不覆盖且未获明确强推确认，先暂停远端操作。接收确认尚未收到。

本报告是2026-10-31执行快照，不是最终课程验收报告。待CM端口、DS日历修复、UI工程及Java 21环境到位后逐项复验coverage-matrix并更新证据。
