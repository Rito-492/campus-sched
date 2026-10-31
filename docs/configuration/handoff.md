# 交接单（配置经理）

## 评审整改（2026-10-31）：P1 改期目标未来复核已关闭；P2 发件箱租约所有权/恢复修复

独立 AI 交叉评审（非自审）对 PR #18 提出「必须改」，经两轮整改，本轮仅补完剩余一项（租约所有权/恢复），不扩大整改范围，重新推送待复审：

- P1（改期目标未来复核）：已关闭。`ChangeService.resourceDecision` 资源 APPROVE 分支在最终检查/激活前，按当前 Clock 重查 `targetFuture(change.targetPlan())`（与写入同事务）。原活动仍在未来但改期目标在复核时已过去，改为 409 `INVALID_STATE`，不再把有效版本/预约回写到过去；REJECT 分支不受此约束。依据 01 §3「改期目标须在未来」。
- P2（发件箱认领互斥 → 租约所有权/恢复）：
  - 第一轮（短期竞态，评审已认可）：`EventRepository.claimNext` 由「SELECT FOR UPDATE」改为原子 `UPDATE…RETURNING`，在同一语句把 `next_attempt_at` 推到未来作为持久化认领租约（60s，超时/崩溃自动恢复可重认领）；`markDone`/`recordFailure` 加 `status='PENDING'` 守卫，晚到结果不得覆盖 DONE/FAILED 终态。
  - 第二轮（本轮：租约过期所有权/恢复）：新增 `claim_token uuid` 列（Flyway `V2__outbox_claim_token.sql`；`outbox_event` 写入方为 CM，非跨模块共享表，无需 DS/PM 改表协调）。`claimNext` 每次认领以 `gen_random_uuid()` 换发唯一令牌并随 `OutboxRow` 返回；`markDone(id, token)`/`recordFailure(id, token, error)` 的 WHERE 增加 `claim_token=?`，仅对仍持有本次令牌的 PENDING 事件记账，并返回是否实际生效（boolean）。租约过期被他人重认领后，旧认领者的晚到成功/失败记账因令牌不匹配而影响 0 行，不再篡改新认领者的状态与重试预算；`NotificationConsumer` 以「0 行 = 丢失所有权」处理——成功路径回滚通知写入、失败路径静默放弃，不把成功投递与无效 markDone 一起提交，也不把旧认领者的失败计到新认领者头上。依据 01 §5 / 02 §5。

- 回归与验证（真实执行，本机便携 PostgreSQL 17.4 + jdk-23 --release 21）：
  - `ChangeServiceTest`（1 用例）：资源 APPROVE 目标已过去 → 409 INVALID_STATE，且未进入调度锁检查。
  - `OutboxClaimIT`（4 用例，新增 1）：认领租约使第二消费者无法重复认领同一事件；markDone 后 recordFailure 不覆盖 DONE、不重复递增 attempts；失败按指数退避并保持 PENDING；**旧租约→重认领→旧晚到失败→新成功**，旧认领者 recordFailure 返回 false 且不改状态/不消耗预算，新认领者 markDone 正常置 DONE。
  - 单元测试 71/71 通过；OutboxClaimIT 4/4 通过；`SchemaIT` 8/8（验证 V2 迁移与 22 表断言）、`FoundationIT` 21/21、`DemoDataSeederIT` 3/3、`ResourceSchedulingIT` 30（跳过 1）、`DsStrategyBenchmarkIT` 2 均通过。
  - 全量 `verify`（77 运行 / 0 失败 / 9 错误 / 跳过 1）仍余 9 个 QA 测试支撑错误：8 个验收 IT 因 `DbTestSupport` 资源范围先于资源插入的外键缺陷在 beforeAll 装载阶段报错，`ActivityRegistrationIT` 因测试侧 mock 的 `EventPort` bean 与生产 `EventPortAdapter` 双 bean 冲突（缺 `@Primary`）——均非本租约所有权修复引入，属 QA 测试支撑待修项（见下「本轮工作」QA 需修清单），不据此打回本项修复。

## 本轮工作（2026-10-31）：交付改期/通知/审计/发件箱生产实现 + 便携 PostgreSQL 复跑 SeederIT 并定位改期/通知验收阻塞

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；CM 改期 API-26～36 与事件/通知基础设施交付（续 PR #10 合入后）。
- main 基准：`origin/main` 现为 `c4f3d41`（PR #15 调度合并）；PR #10（Seeder + 配置文档）已于 `feb0d70` 合入。本分支 `work/55231031-change` 已提交并推送（`a924d0c` feat + `ad70129` docs），交付见 **PR #18**（base main=c4f3d41，2 commit / 14 文件 / +1081，含 13 生产文件 + 1 交接单）。
- 本轮交付（生产实现，非测试替身；统一锁序 receipt→activity→change→user→resource，单事务 MANDATORY）：
  - `server/.../change/`：ChangeService（API-26 预览/27 创建/28 列表/29 详情/30 人员确认/31 批准/32 资源复核原子切换/33 撤回）、ChangeController、ChangeRepository、ChangePortAdapter。
  - `server/.../notification/`：EventRepository（audit_log + outbox_event）、EventPortAdapter、NotificationRepository、NotificationService（API-34 列表/35 读取/36 审计）、NotificationController、NotificationConsumer（@Scheduled 2s，claim FOR UPDATE SKIP LOCKED，指数退避 2/4/8/16/32s，5 次 FAILED）、NotificationConfiguration（@EnableScheduling）、NotificationDeliveryProbe + Noop 实现。
  - 逐字段核对 V1__schema.sql 与 02 契约记录构造顺序（change_request/change_confirmation/audit_log/outbox_event/notification 及 Impact/EventDraft/ChangeView/DecisionRecord/ChangeCreate/ChangePreview），SQL 与记录字段序全部一致，无字段改名或越过端口直写。
- 过期配置状态更新：`server/pom.xml` + Maven Wrapper（mvnw.cmd）已随 PM 进入 main，本机可用 `D:\Java\jdk-23` + `--release 21` 编译运行，上轮「后端工程未就绪（缺 pom/Maven Wrapper）」旧条目已作废；契约要求 Java 21，本机仅 1.8/23，用 jdk-23 --release 21 满足（非阻塞）。
- 实际检查（真实结果，均本机便携 PostgreSQL 17.4 独立 campus_test + 安全 DEMO_PASSWORD 环境变量）：
  - `compile` + `test-compile`：✅ BUILD SUCCESS（28 个测试源文件编译通过）。
  - 单元测试（Surefire *Test）：✅ 70/70 通过（ActivityService 18 / ActivityStateMachine 7 / AuthorizationPolicy 8 / ContractValidation 6 / CanonicalJson 6 / Responses 2 / CandidateRanking 3 / CapacitySweep 6 / ScheduleQueryService 4 / StrategyEvaluator 6 / TimeInterval 4）。
  - `DemoDataSeederIT`：✅ 3/3 通过（首次 12 项授权 / 撤销不重放 / 删除重建后 12 项），Flyway 迁移 + 演示数据导入在独立 schema t_<32hex> 完成。
  - `ChangeRollbackIT` / `NotificationConsistencyIT`：❌ 阻塞于 QA 测试支撑 `DbTestSupport.insertReferenceTables` 装载顺序缺陷——`user_resource_scope`（managedResourceIds）先于 `resource` 插入，触发 `fk_user_resource_scope_resource` 外键违约（resource_id=3101 不存在），发生在 beforeAll 数据装载阶段、本模块业务代码尚未执行；非本模块生产代码问题。
- 复跑方式（本机无 Docker/psql，已用便携二进制）：下载 PostgreSQL 17.4 Windows 二进制至 `D:\campus-pg-tmp\`（未装系统服务），`initdb -A trust` 后 `pg_ctl start`，建 `campus_test`；env：`TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/campus_test`、`TEST_DB_USERNAME=postgres`、`TEST_DB_PASSWORD=postgres`、`DEMO_PASSWORD=demo123`、`JAVA_HOME=D:/Java/jdk-23`；命令 `./mvnw.cmd "-Dit.test=DemoDataSeederIT" verify`。验证结束已 `pg_ctl stop` 干净关闭。
- QA 需修（本 agent 不越权改 QA 测试代码，仅如实记录）：
  1. `DbTestSupport.insertReferenceTables`：`managedResourceIds`→`user_resource_scope` 需移到 `resource` 建表之后（同 PR #8 对生产 `DemoDataSeeder` 的修复，测试支撑未同步）。
  2. `NotificationConsistencyIT`：声明嵌套接口 `NotificationDeliveryProbe` 并以 `@Primary` 注册该嵌套类型 bean，未覆盖生产 `com.campus.events.notification.NotificationDeliveryProbe`，消费者仍走 Noop、attempts 恒 0；应改为 import 生产接口。
  3. `NotificationConsistencyIT` 读取通知调用传 `null` `Idempotency-Key`，而 `POST /notifications/{id}/read` 按契约要求该头（BearerFilter 仅豁免 login/logout 与 schedule-preview/alternatives/change-preview），预期 400；应补传 UUID。
  4. `ActivityRegistrationIT`：测试侧 mock 的 `EventPort` bean 与生产 `EventPortAdapter` 形成双 bean，Spring 注入 `NoUniqueBeanDefinitionException`（`expected single matching bean but found 2: eventPortAdapter,eventPort`）；应给测试 mock 加 `@Primary` 或改用 `@MockBean`（同第 2 项的生产接口覆盖问题，随生产端口实现交付而显现）。
- 未完成项/外部依赖：改期回滚（D-AT-14）与通知一致性（D-AT-19）验收 IT 需 QA 先修上述 1（及 2/3）后复跑；交付已通过 PR #18 提交，合并待评审授权（本 agent 不自行批准/合并）。
- 下一接收人：测试经理（汤雨润，55230316）修复 `DbTestSupport` 装载顺序并复审 NotificationConsistencyIT 两处；项目经理（冀项阳，55230223）评审 PR #18（改期/通知/审计/发件箱生产交付）并授权合并。

## 本轮工作（2026-10-30）：基于当前 main 重基线 + 补全 T08 DOCX 三口径 + 修复 SeederIT 口令与过期表述

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；CM-01 收尾 + PR #8/#10 评审整改（续）。
- main 基准：VPN 恢复后 `git fetch` 成功；`origin/main` 现为 `c9c757b`（PR #12 合并，2026-10-29），已远超任务起点 34dc4d5。本分支 `work/55231031-change` 已 rebase 到 `c9c757b`（无冲突）。
- PR 状态（GitHub API 在线核对，开始整改时）：PR #8 `fix/demodataseeder-118`（head ee76c2a / base 2a9e941，非固定分支）与 PR #10 `work/55231031-change`（head a6d734c / base 34dc4d5）均 CHANGES_REQUESTED；本轮整改后 PR #10 已推至 head 81a40f5 / base c9c757b。DS（黄方勇）2026-10-26 复现证据：main=34dc4d5 默认全量构建（compile/test/verify）均退出 1，首个错误 `DemoDataSeeder.java:118 seedActivity() throws Exception` 未捕获，96 个生产源文件编译停止——即 main 上 Seeder 仍是旧坏版本，修复被卡在 PR #8/#10，本分支的 seedActivity 修复正对其。
- 复核勘误（对上轮 2026-10-26 记录的修正）：`T08_配置管理计划.docx` v1.5 同步不完整——上轮漏了三处：①§3.1.1 未补"DATA/MODEL 为英文整词（4/5 位），与三位缩写并存"；②编号解剖示例未补 `CS-CI-DATA-01` / `CS-CI-MODEL-01` 实例；③§3.1.2 仅"建立时间/授权时间"二口径，未区分 commit / tag / 授权三口径。
- 本轮改动（已提交并推送到 PR #10，work/55231031-change → 37e8b15）：
  - `DemoDataSeederIT.java`：移除硬编码 `demo123`，改读必需环境变量 `DEMO_PASSWORD`（缺失抛 `IllegalStateException`）；修正过期的"待 PM pom 进入主线"运行说明。
  - `DemoDataSeeder.java`：JavaDoc 移除过期的"server/pom.xml 尚未交付"表述（pom 已随 PR #7 进入 main）。
  - `docs/configuration/T08_配置管理计划.docx`（二进制，按配置规程保留版本记录，仍为 v1.5 补全同一轮整改，不静默覆盖）：补齐上述三处，使 DOCX 与 Markdown 计划/索引一致。
  - `docs/configuration/repo-structure.md`：`server/` 根包笔误 `com.campus/events` → `com.campus.events`。
  - 三口径时间按 `git` 核实：`baseline/init-v1.0` commit=tag=2026-09-25 22:15:33、`baseline/task-package-v2.0` commit=tag=2026-09-26 19:59:40、`baseline/task-package-v3.0` commit 2026-10-14 15:44:47 / tag 15:54:57（注解 tag，commit≠tag），与计划/索引一致，未臆造、未倒造审批。
- 实际检查（真实结果）：
  - rebase：✅ `git rebase origin/main` 无冲突，本分支已基于 `c9c757b`（merge-base 由 2a9e941 前移）。
  - 默认全量构建 / `DemoDataSeederIT`：❌ 本机无 Java 21（仅 1.8/23）、无 Maven、无 Docker、无 PostgreSQL，无法本地复跑；DS 已用仓库 mvnw+Java21 复现 main 编译错误，本分支 seedActivity 修复正对该错误；真库 SeederIT 三条场景此前已在 PM 集成日志记录 24 项真 PostgreSQL IT 通过（含 DemoDataSeederIT 3 项，隔离运行，非 PR CI）。
  - DOCX 校验：✅ `python-docx` 可打开（126 段 / 10 表）、`word/document.xml` 良构、zip 完整，新增内容逐项核验通过。
  - 密钥/日志：✅ 源码无 `demo123` 硬编码、不打印口令（仅 warn 未配置 / info 计数）。
  - 推送与 PR 更新：✅ `--force-with-lease` 强推成功（work/55231031-change → 81a40f5，base c9c757b）；PR #10 标题/正文已更新（列全量 diff、说明取代 PR #8）；PR #8/#10 已逐条回复评审并请求复审。PR #8 关闭与合并待 PM 确认（本 agent 不自行批准/合并）。
- 未完成项/外部依赖：本机缺 Java 21/Docker/PostgreSQL → 真库 SeederIT 需在集成环境（PM/QA）复跑并保存真实报告；单一 PR 与 PR #8 关闭方案待 PM 确认。
- 下一接收人：项目经理（确认单一 PR 与 PR #8 关闭方案、组织集成环境复跑构建+真库 IT）；测试经理（复审 SeederIT 与配置文档变更）。

## 本轮工作（2026-10-26）：修复 SeederIT 口令硬编码 + PR #10 文档逐项清阻塞

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；CM-01 收尾 + 配置文档（PR #8/#10 评审整改）。
- main 基准：远程 `origin/main` 已推进至 `34dc4d5`；本机 `git fetch` 失败（github.com:443 无法连接），本地 main 停留 `b994ad3`、无 `server/pom.xml`。PR #8 `fix/demodataseeder-118`（head ee76c2a / base 2a9e941）与 PR #10 `work/55231031-change`（head a6d734c / base 34dc4d5）二者的 DemoDataSeeder.java / DemoDataSeederIT.java 内容完全相同，均 CHANGES_REQUESTED。
- 本轮改动：
  - `server/src/test/java/com/campus/events/dev/DemoDataSeederIT.java`：移除第 111 行硬编码 `demo123`，改读必需环境变量 `DEMO_PASSWORD`（新增 `requireDemoPassword()`，缺失即抛 `IllegalStateException`，与 QA 侧 acceptance IT 同款约定）。验证：`DemoDataSeeder` 生产代码不硬编码口令、不打印口令（仅 warn 未配置 / info 计数）；`compose.qa.yaml`/`.env.example` 中 `demo123` 为演示默认值（模板标注“示例非秘密值”），非泄漏。
  - `docs/configuration/configuration-management-plan.md` v1.4→v1.5：①§3.1 类别缩写“三位大写字母”→“2–5 位大写字母”（DATA=4 位/MODEL=5 位为英文整词）；②编号解剖示例 `CS-CI-DOC-04 v1.2`→`v1.5` 并补 DATA/MODEL 实例；③§5.2 基线清单区分 tag commit 时间 / tag 时间 / 授权时间，三条历史基线授权标“待核实（无追溯 CCB 记录）”，不补造、不倒造审批。
  - `docs/configuration/configuration-index.md`：登记表新增“版本”列；顶部明示本索引为配置项与基线的“唯一登记处”；基线清单同步“待核实”与时间口径。
  - `docs/configuration/T08_配置管理计划.docx` v1.4→v1.5：同步上述三处（类别缩写、v1.2 示例、历史基线授权“待核实”+时间口径），按配置规程以版本号留痕，不静默覆盖。
- 实际检查（真实结果，非通过）：
  - `git fetch origin`：❌ 失败（github.com:443 无法连接），未能取得 main=34dc4d5 与 PR 最新状态。
  - 默认全量构建 `./server/mvnw test` / `verify`：❌ 无法执行——本分支与本地 main 均无 `server/pom.xml`、无 `mvnw*`、无 Maven（`mvn` 未安装）、无 Docker、无 PostgreSQL（psql）、无 pwsh；`DemoDataSeederIT` 无法运行，证据待后端工程（pom/Maven Wrapper，PM）就绪后补跑并保存报告。
  - 契约约束（03 §1）：`DEMO_PASSWORD` 从环境变量读取、不写 JSON、不打印——已核对通过（源码 grep 无 `demo123` 硬编码于 Java 测试，仅模板默认值）。
- PR 协调（需先与 PM 确认，本 agent 不自行批准/合并）：
  - 建议：代码/IT 以 `work/55231031-change` 基于当前 main（34dc4d5）保留为**单一 PR**；PR #8 `fix/demodataseeder-118`（旧 base 2a9e941、非固定分支）作为重复 PR 关闭；配置文档变更纳入同一 PR 或拆分 PR，由 PM 裁定范围。待 PM 确认后再更新 PR 标题/描述并请求 PM/QA 复审。
- 未完成项/外部依赖：后端工程（`server/pom.xml` + Maven Wrapper + contract 公共类型）由项目经理交付后方可编译运行 SeederIT；本机网络与 Docker/PostgreSQL 缺失需具备后补跑全量构建与真实库 IT。
- 下一接收人：项目经理（冀项阳，55230223）确认单一 PR 与 PR #8 关闭方案、交付后端工程；测试经理（汤雨润，55230316）复审 SeederIT 与配置文档变更。

## 本次交付（课程任务2：配置管理计划 + 建立配置库）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；课程分组任务2（配置管理计划、建立配置库）。
- 交付文件：
  - `docs/configuration/T08_配置管理计划.docx` —— 配置管理计划课程提交版（Word，参照 RUP 模板），含产品目录结构（§3.1.2）、配置项清单（附录 A）、基线清单（附录 B）。
  - `docs/configuration/configuration-management-plan.md` —— 同步更新 v1.1：新增 §4.2 目录结构（产品目录结构），基线清单更新为「已建立」。
  - `docs/configuration/configuration-index.md` —— 登记 CS-CI-DOC-05（课程提交版 Word）；基线清单 `baseline/init-v1.0`、`baseline/task-package-v2.0` 状态更新为「已建立」。
- 配置库（Git 仓库）现状：
  - 产品目录结构已建立并写入配置管理计划（§3.1.2 / §4.2）。
  - 项目相关文档已纳入版本库（docs/ 各岗位目录）。
  - 已定型/已通过评审的产物已标识基线：`baseline/init-v1.0`、`baseline/task-package-v2.0`（tag 已存在）。
- 网络：2026-10-19 已恢复，`git fetch` 成功；`origin/main` 已推进到 `2a9e941`（PR#5 QA 测试文档 + PR#6 DS 调度纯逻辑已合并），本分支已合并同步（见「本轮工作」）。

## 本轮工作（2026-10-19）：同步 main + 修复种子导入器编译错误与导入顺序

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；CM-01 种子导入器收尾 + 配置库同步。
- main 基准：合并 `origin/main` `2a9e941`（PR#5 QA 测试文档、PR#6 DS 调度纯逻辑 `CandidateRanking`/`CapacitySweep`/`TimeInterval` 及单测）；本分支现领先 origin/main 3 个提交（2 个文档提交 + 1 个 merge）。
- 本轮改动：
  - `server/src/main/java/com/campus/events/dev/DemoDataSeeder.java`：`seedActivity` 移除 `throws Exception`，`objectMapper.writeValueAsString` 的 `JsonProcessingException` 包装为 `UncheckedIOException` 向上抛（事务内回滚），解除默认构建 `DemoDataSeeder.java:118` 编译错误。
  - `server/src/main/java/com/campus/events/dev/DemoDataSeeder.java`（PR #8 评审要求，两轮）：①导入顺序——先建组织/用户基础资料（角色/权限/审批范围）/资源，再插入 `managedResourceIds`（`user_resource_scope` 对 `resource` 有即时外键，原顺序在干净库触发外键错误、整批回滚）；②幂等——`seedUserBase` 返回是否新建，`seedUserResourceScopes` 仅对本轮新建用户调用，已有用户整体跳过、不重放已撤销的资源范围授权。已推 `fix/demodataseeder-118`（commit 52f3736），PR #8 待复审；空库 + 真实 PostgreSQL 导入回归已固化为 `DemoDataSeederIT`，待后端工程就绪后运行。
  - `server/src/test/java/com/campus/events/dev/DemoDataSeederIT.java`（新增）：真实 PostgreSQL 回归（DbTestSupport + 反射装配 seeder），固化三条场景——首次 12 项、撤销后重导保留、删除用户重建后 12 项。已推 `fix/demodataseeder-118`（commit ee76c2a）；**未运行**（base main 无 pom.xml，本机无 Maven/PostgreSQL/Docker，待 PM 工程进 main 后执行并记录证据）。
  - `docs/configuration/repo-structure.md`：`openapi.yaml` 接口数由「38 个」更正为「40 个」（PR #7 评审要求，QA 侧同步 `check-contract.ts`）。
  - `docs/configuration/T08_配置管理计划.docx`：恢复为 ddec7df 提交版本；工作区曾出现的无前缀重生成版 `配置管理计划.docx` 已移出仓库至 `.tmp-gen/word-backup/` 待确认（未删除）。
- 工作区既有未提交改动（本轮保留，未覆盖）：`configuration-management-plan.md` v1.2（§5.2 增补「基线授权」说明，小版本递增）、`handoff.md`（本轮记录）、`repo-structure.md`（接口数 38→40）。
- 一致性核对：配置管理计划 §5.2 基线清单与配置项索引 §3 基线清单一致（`init-v1.0` / `task-package-v2.0` / `task-package-v3.0` 已建立；requirements / design / release 待建立）；计划 §1 与索引 CS-CI-DOC-05 均指向 `T08_配置管理计划.docx`，恢复后一致。
- 实际检查（真实结果）：
  - `git fetch` + `git merge origin/main`：✅ 成功，无冲突。
  - `scripts/load-env.ps1`：✅ 加载成功，无异常。
  - `scripts/check.ps1 -Scope change`：❌ 1 项失败，退出码 1 ——「change：后端工程未就绪（缺 server/pom.xml 或 Maven Wrapper），依赖项目经理交付」。
  - 后端编译 / 单元 / 集成测试：**未执行**（缺 `server/pom.xml`，main 无 Maven 工程）。
  - `compose.yaml` / `compose.qa.yaml` / `infra/db/init.sql`：**待验证**（本机未安装 Docker）。
- 未完成项 / 外部依赖：同下表；核心阻塞仍是项目经理后端工程（pom / Maven Wrapper / contract 公共类型 / AccountPort / Clock，现处 PR #7 未合并）。
- 下一接收人：项目经理（交付 pom 后我方可编译验证种子导入器与调度类）；QA / PM（交叉评审本轮配置文档变更）。

## 历史交付（CM-01：M0 环境、配置计划、种子导入框架）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 CM-01（M0 环境、配置计划、种子导入框架）
- 基线：v3.0（2026-10-13）；基准 commit：`ed8c3bc`（main 最新：PR#1 DS-01 V1 schema 升级 v3.0）；本人分支 `work/55231031-change`（已从 origin/main 快进合并，未 reset，保留原未提交工作）
- 交付文件与接口：
  - `compose.yaml` —— 开发环境编排：db（PostgreSQL17，5432）+ server（dev profile，8080）+ web（Nginx，5173）
  - `compose.qa.yaml` —— E2E 独立测试编排：项目 `campus-events-qa`、独立卷、宿主 db 5440 / API 8081 / web 8089、后端 `dev,e2e` profile 只连 `campus_test`、固定 `APP_TEST_NOW=2030-10-19T00:00:00Z` 与 `DEMO_DATE=2030-10-20`、禁止接真实部署库
  - `server/src/main/resources/application.yml` / `application-dev.yml` / `application-test.yml` / `application-e2e.yml`
  - `.env.example`（新增 `BASE_URL`）、`scripts/load-env.ps1`（白名单新增 `BASE_URL`）、`scripts/check.ps1`（新）
  - `infra/db/init.sql`（建 `campus_dev`/`campus_test` 与业务账号 `campus_app`）、`infra/nginx/default.conf`（新）
  - `server/Dockerfile`、`web/Dockerfile`（新，构建上下文 = 仓库根）
  - `server/src/main/java/com/campus/events/dev/DemoData.java`、`DemoDataSeeder.java`（种子导入器框架，按 test-data-plan §5 派生规则实现）
  - `docs/configuration/configuration-index.md`（登记新增配置项）

## 接收 QA 测试数据确认

- ✅ **已接收** 测试经理（汤雨润，55230316）的测试数据：`test-data/demo.json` **v3.0**（三角色 identity/roles/permissions/approvalScopes/managedResourceIds、资源资格 allowTeacher/allowStudent、活动/报名派生字段），`docs/testing/test-data-plan.md` §3/§5（派生规则），`docs/testing/test-plan.md` §3（E2E 环境规格）。
- 种子导入器 `DemoDataSeeder` 已按 §5 实现：password_hash=BCrypt(DEMO_PASSWORD)、enabled=true、roles/permissions/approvalScopes/managedResourceIds 逐表展开、activity 版本/有效版本/lock_version、activity_version、reservation（SCHEDULED/OPEN 派生 ACTIVE 预约、区间 [start−setup, end+teardown)）、报名；幂等（已存在同 id 校验后跳过，不覆盖）；仅 dev profile 且 `APP_DEMO_SEED=true` 运行。

## 实际运行命令与结果

- ✅ `scripts/load-env.ps1`：实测通过。`DB_NAME=campus_dev`、`DEMO_PASSWORD=demo123`、`APP_DEMO_SEED=false`、`BASE_URL=http://localhost:8089`、`TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/campus_test` 均正确加载。
- ✅ `scripts/check.ps1`：脚本解析与运行正确（`-Scope` 参数、退出码透传）。修复过一处缺陷：PowerShell 5.1 需 UTF-8 BOM，否则中文字符串导致解析错误（已为 load-env.ps1 / check.ps1 补 BOM）。
- ❌ `scripts/check.ps1 -Scope all`：**8 项失败，退出码 1**（诚实记录，非通过）：
  - foundation / activity / scheduling / change：缺 `server/pom.xml` 与 Maven Wrapper，无法编译/测试（依赖项目经理后端工程）。
  - ui：缺 `web/package.json`，前端工程未就绪（依赖界面设计师）。
  - qa：`npm ci` 成功（Node 22.17.0 < 契约要求 ≥22.18.0，仅告警）；`test:contract` 与 `evaluate` 失败（`node scripts/*.ts` 报 `ERR_UNKNOWN_FILE_EXTENSION`，Node 22.17 无法直接运行 .ts，需 ≥22.18 或 tsx 装载器）；`test:e2e` 已收集用例但 `BASE_URL=http://localhost:8089` 无后端服务，全部失败。
- ⬜ `compose.yaml` / `compose.qa.yaml` / `infra/db/init.sql`：本机未安装 Docker，**未验证** `docker compose config` 与数据库初始化。
- ⬜ `DemoDataSeeder`：编译/运行阻塞于 `server/pom.xml` 未交付（无 Maven 工程，无 contract 公共类型），**未编译未运行**。
- 本机运行时差异（04 规程 §5）：无 Docker；Java `23.0.2`（契约要求 21）；Maven 未安装；Node `22.17.0`（契约要求 ≥22.18）；无 pwsh（用 Windows PowerShell 5.1）。

## 未完成项 / 外部依赖

| 项 | 具体缺口 | 提供方 |
|---|---|---|
| PR #7 阻塞：`server/mvnw` 权限 | Git 模式 100644，Linux/macOS 无法执行标准入口 `./server/mvnw`，须改 100755 | 项目经理（冀项阳 55230223） |
| PR #7 阻塞（P1）：并发死锁 | `CommandExecutor.java:28-33` 回执外键 `fk_command_receipt_actor`（KEY SHARE）与 `AccountRepository` 用户 `FOR UPDATE` 锁序冲突，同用户不同键并发可 `40P01` 死锁 | 项目经理 |
| PR #7 集成阻塞：调度验收 | 主线验收 `SchedulingBenchmarkIT` 依赖的 `StrategyEvaluator`/`StrategyResult` 未交付 | 设计师（黄方勇） |
| 后端可编译/运行 | `server/pom.xml` + Maven Wrapper + `com.campus.events.contract` 公共类型 + AccountPort/Clock | 项目经理（冀项阳 55230223） |
| 种子导入器编译/运行 | 依赖 PM pom（JdbcTemplate/TransactionTemplate/BCrypt PasswordEncoder/Jackson）与 contract 类型；DS schema 已到 | 项目经理 |
| 种子导入空库验证（PR #8 要求） | 已固化 `DemoDataSeederIT`（三条场景：首次 12 项 / 撤销后重导保留 / 删除用户重建后 12 项）；待 PM pom 进 main 后运行并记录证据；本机无 Maven/PostgreSQL/Docker | 配置经理（待后端工程） |
| compose server/web 构建 | 依赖 PM 后端 pom；web 依赖 UI（杨畅 17231018）`web/package.json` | 项目经理 / 界面设计师 |
| 环境整体启动验证（D-AT-24） | 本机无 Docker，无法 `docker compose up` | 本机环境 |

## 下一接收人及动作

- 测试经理（汤雨润）：确认 `compose.qa.yaml`（8089/5440/8081、固定 APP_TEST_NOW/DEMO_DATE、campus_test 隔离）；Docker 就绪后复验 db 初始化；核对 `check.ps1 -Scope qa` 的 Node ≥22.18 前置条件；复审 PR #7 与 PR #8（后者已按评审修复种子导入顺序，待空库验证）。
- 项目经理（冀项阳）：改 `server/mvnw` 为 100755；修复 `CommandExecutor` 回执外键与用户锁的并发死锁（P1）；交付 pom/Maven Wrapper/contract 后，我方可编译种子导入器、跑空库导入验证并跑 `check.ps1 -Scope change`。
- 设计师（黄方勇）：交付 `StrategyEvaluator`/`StrategyResult`，解除主线验收 `SchedulingBenchmarkIT` 编译依赖。
- 界面设计师（杨畅）：交付 `web/package.json` 后 `web/Dockerfile` 才可构建。
- 全体：复制 `.env.example` 为 `.env` 并配合 `scripts/load-env.ps1` 使用。

## 接收确认

尚未接收（仅真实收到反馈后更新）。
