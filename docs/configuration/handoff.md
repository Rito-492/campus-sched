# 交接单（配置经理）

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
