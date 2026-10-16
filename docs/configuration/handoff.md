# 交接单（配置经理）

## 本次交付（课程任务2：配置管理计划 + 建立配置库）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；课程分组任务2（配置管理计划、建立配置库）。
- 交付文件：
  - `docs/configuration/T08_配置管理计划.docx` —— 配置管理计划课程提交版（Word，参照 RUP 模板），含产品目录结构（§3.1.2）、配置项清单（附录 A）、基线清单（附录 B）。
  - `docs/configuration/configuration-management-plan.md` —— 同步更新 v1.1：新增 §4.2 目录结构（产品目录结构），基线清单更新为「已建立」。
  - `docs/configuration/configuration-index.md` —— 登记 CI-DOC-05（课程提交版 Word）；基线清单 `baseline/init-v1.0`、`baseline/task-package-v2.0` 状态更新为「已建立」。
- 配置库（Git 仓库）现状：
  - 产品目录结构已建立并写入配置管理计划（§3.1.2 / §4.2）。
  - 项目相关文档已纳入版本库（docs/ 各岗位目录）。
  - 已定型/已通过评审的产物已标识基线：`baseline/init-v1.0`、`baseline/task-package-v2.0`（tag 已存在）。
- 网络问题：`git fetch/pull` 无法访问 github.com（连接被重置/超时，本机无代理），**未能拉取最新 origin 更新**；本地 `origin/main` 停留在 PR#2 合并（b96e4ca）。待网络恢复后需重新同步 main 再合并。

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
| 后端可编译/运行 | `server/pom.xml` + Maven Wrapper + `com.campus.events.contract` 公共类型 + AccountPort/Clock | 项目经理（冀项阳 55230223） |
| 种子导入器编译/运行 | 依赖 PM pom（JdbcTemplate/TransactionTemplate/BCrypt PasswordEncoder/Jackson）与 contract 类型；DS schema 已到 | 项目经理 |
| compose server/web 构建 | 依赖 PM 后端 pom；web 依赖 UI（杨畅 17231018）`web/package.json` | 项目经理 / 界面设计师 |
| 环境整体启动验证（D-AT-24） | 本机无 Docker，无法 `docker compose up` | 本机环境 |

## 下一接收人及动作

- 测试经理（汤雨润）：确认 `compose.qa.yaml`（8089/5440/8081、固定 APP_TEST_NOW/DEMO_DATE、campus_test 隔离）；Docker 就绪后复验 db 初始化；核对 `check.ps1 -Scope qa` 的 Node ≥22.18 前置条件。
- 项目经理（冀项阳）：交付 pom/Maven Wrapper/contract 后，我方可编译种子导入器并跑 `check.ps1 -Scope change`。
- 界面设计师（杨畅）：交付 `web/package.json` 后 `web/Dockerfile` 才可构建。
- 全体：复制 `.env.example` 为 `.env` 并配合 `scripts/load-env.ps1` 使用。

## 接收确认

尚未接收（仅真实收到反馈后更新）。
