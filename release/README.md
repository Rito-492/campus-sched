# 发布说明（Release Notes）— campus-sched

> 面向校园活动的多资源协同调度与变更影响分析系统
> 发布版本：v1.0.0　基线：`baseline/release-v1.0.0`（待 PM CCB 授权后打 tag）
> 发布提交：`750763b721ca999c454ac730d9876583b40ad202`
> 编制：杨瀚霖（55231031，配置经理）　日期：2026-11-14

本文是「按发布说明干净启动」的唯一入口，对应验收 **D-AT-24**：干净环境按本文启动后，真实后端/数据库连通，无手改数据库才能继续的步骤，版本、手册、报告一致。本文与 `manifest.json`（发布清单）、`docs/configuration/`（配置计划/索引/结构指南）共同构成配置库交付。

## 1. 前置条件

| 组件 | 版本 | 说明 |
|---|---|---|
| Docker（Compose v2） | 任意近期版 | 编排 db + server + web |
| Java | 21 | 后端构建（由 `server/Dockerfile` 内的 Maven 镜像提供，本机无需装） |
| Node | 22 | 前端构建（由 `web/Dockerfile` 内的 node 镜像提供，本机无需装） |
| PostgreSQL | 17 | 由 `db` 容器提供，本机无需装 |

浏览器访问演示入口即可；本机仅需 Docker 与任意现代浏览器。

## 2. 干净环境启动

```powershell
# 1. 准备环境变量（.env 不入库）
Copy-Item .env.example .env
# 编辑 .env，至少设置演示账号密码：DEMO_PASSWORD=<你的演示密码>（默认 demo123 仅限本地演示）

# 2. 启动（首次构建镜像 + 后台运行）
docker compose up -d --build

# 3. 等待数据库健康（db 容器 healthcheck 通过后 server 自动启动）
docker compose ps
```

首次启动时 `db` 容器执行 `infra/db/init.sql`，创建业务账号 `campus_app` 与 `campus_dev`（开发库）/`campus_test`（测试库）两个库；`server` 以 `dev` profile 启动时由 **Flyway 自动依序应用迁移 V1→V2**，无需任何手改数据库步骤。

## 3. 端口

| 入口 | 地址 | 说明 |
|---|---|---|
| 演示入口（Nginx 前端 + `/api` 反代） | http://127.0.0.1:8088 | 答辩/演示统一入口 |
| 后端 API | http://127.0.0.1:8080 | 前缀 `/api/v1`，健康检查 `/api/v1/health` |
| PostgreSQL | 127.0.0.1:5432 | 库 `campus_dev`，业务账号 `campus_app` |

> 测试环境独立于开发：`docker compose -f compose.qa.yaml up -d`（项目 `campus-events-qa`，宿主 db 5440 / API 8081 / web 8089，独立卷），二者数据与卷完全隔离。

## 4. 演示账号（三角色 × 独立身份）

密码统一为 `.env` 中 `DEMO_PASSWORD`（默认 `demo123`，仅本地演示，勿用于生产）。账号由 `test-data/demo.json` 经 `DemoDataSeeder` 幂等导入。

| 账号 | 身份 | 角色 | 用途 |
|---|---|---|---|
| `applicant_a` | 教师 | 申请人 | 创建/提交活动、发起改期 |
| `applicant_b` | 学生 | 申请人 | 学生身份申请 |
| `approver_a` | 教师 | 批准人 | 审批本组织 VENUE/PROJECTOR 范围 |
| `approver_other` | 教师 | 批准人 | 其他组织批准人（跨组织边界验证） |
| `resource_admin` | 教师 | 管理员 | 资源复核（RESOURCE_MANAGE/RESOURCE_CONFIRM） |
| `account_admin` | 教师 | 管理员 | 账号/授权管理（AUDIT_READ 等） |
| `student_a` / `student_b` | 学生 | 申请人 | 报名 |
| `staff_a` | 学生 | 申请人 | 活动工作人员（改期确认） |

种子导入开关：`APP_DEMO_SEED=true` 且 `server` 以 `dev` profile 运行时导入；`DEMO_DATE` 留空则默认「首次导入当天上海日期 + 7 天」，可用 `yyyy-MM-dd` 覆盖。导入幂等，重复启动不重复造数据。

## 5. 备份、迁移与恢复（恢复演练）

**迁移**：`db/migration/V1__schema.sql`（22 表契约）与 `V2__outbox_claim_token.sql`（发件箱认领所有权）由 Flyway 在启动时自动依序应用；**不通过删除 volume 掩盖迁移失败**（迁移失败应看日志修复，而非 `docker compose down -v` 后重启）。

**备份**（演练步骤，`pg_dump` 逻辑备份）：

```powershell
docker compose exec db pg_dump -U campus_app -d campus_dev -Fc -f /tmp/campus_dev.dump
docker compose cp db:/tmp/campus_dev.dump ./backup-campus_dev.dump
```

**恢复**（演练步骤）：

```powershell
# 恢复到干净库：先清空业务数据，再还原逻辑备份
docker compose exec db dropdb -U postgres --if-exists campus_dev
docker compose exec db createdb -U postgres -O campus_app campus_dev
docker compose cp ./backup-campus_dev.dump db:/tmp/campus_dev.dump
docker compose exec db pg_restore -U campus_app -d campus_dev -c /tmp/campus_dev.dump
```

> 恢复演练的**实际执行与证据**待 QA 复验时在真实 PostgreSQL 上跑通后补记（本说明先固化可复现步骤）；届时与本清单、测试报告、用户手册的版本号一并核对一致。

## 6. 版本一致性

本发布对应提交 `750763b`，其代码/文档/测试版本见 `manifest.json`。用户手册（`docs/ui/user-manual.md`）、软件测试报告与本文须指向同一发布提交；三者版本号或状态不一致时，以 `docs/configuration/configuration-index.md` 的唯一登记为准。

## 7. 验收关联

- **D-AT-24**：干净环境按 §2 启动 → 用 §4 账号换角色走完答辩（创建→审批→资源确认→发布→报名→改期→通知）→ 刷新与重启后数据一致。
- 检查入口：`pwsh -File scripts/check.ps1 -Scope all`（缺测试/0 测试/退出码非 0 一律失败）。
