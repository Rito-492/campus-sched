# 配置项索引（Configuration Index）

> 项目：campus-sched　负责人：杨瀚霖（55231031，配置经理）
> 本文是"配置库"交付物（任务包 §5 交付物 10）的索引部分，只登记**真实纳入版本库**的配置项；未产生的内容不预登记。
> 更新：随各模块交付与基线建立持续维护。

## 1. 说明

- 编号前缀见《配置管理计划》§3.1。
- "状态"列：`受控` = 已纳入 Git 且可追溯；`基线` = 已进入某基线（见 §3）。
- 负责人为配置项的产出/维护方，按《04 协作与集成规程》§1 模块所有权划分。

## 2. 配置项登记表

### 2.1 任务包与元文件（CI-PKG / CI-DOC）

| 编号 | 配置项 | 位置 | 负责人 | 状态 |
|---|---|---|---|---|
| CI-PKG-01 | 总开发任务书 v3.0 | `taskbook.md` | 项目经理 | 受控 |
| CI-PKG-02 | 公共契约（01–05）v3.0 | `agents/common/` | 项目经理 | 受控 |
| CI-PKG-03 | 六份个人任务书 | `agents/<分支>/agent.md` | 各岗位 | 受控 |
| CI-DOC-01 | 仓库约定 | `AGENTS.md` | 项目经理 | 受控 |
| CI-DOC-02 | 项目名片 | `README.md` | 项目经理 | 受控 |
| CI-DOC-03 | 仓库结构指南 | `docs/configuration/repo-structure.md` | 配置经理 | 受控 |
| CI-DOC-04 | 配置管理计划 | `docs/configuration/configuration-management-plan.md` | 配置经理 | 受控 |
| CI-DOC-05 | 配置管理计划（课程提交版，Word） | `docs/configuration/T08_配置管理计划.docx` | 配置经理 | 受控 |

### 2.2 课程下发材料（CI-DOC，只读）

| 编号 | 配置项 | 位置 | 负责人 | 状态 |
|---|---|---|---|---|
| CI-DOC-10 | 选题/分工/工具/考核表 | `docs/course/` | 只读，全员 | 受控（不改） |
| CI-DOC-11 | 六份角色确认表 | `docs/course/role-confirmations/` | 只读，全员 | 受控（不改） |

### 2.3 测试文档与支撑（CI-DOC / CI-DATA / CI-SRC）

| 编号 | 配置项 | 位置 | 负责人 | 状态 |
|---|---|---|---|---|
| CI-DOC-20 | 测试计划 | `docs/testing/test-plan.md` | 测试经理 | 受控 |
| CI-DOC-21 | 测试设计 | `docs/testing/test-design.md` | 测试经理 | 受控 |
| CI-DOC-22 | 测试数据设计 | `docs/testing/test-data-plan.md` | 测试经理 | 受控 |
| CI-DOC-23 | 测试 ID 需求清单 | `docs/testing/testid-requirements.md` | 测试经理 | 受控 |
| CI-DOC-24 | 需求覆盖矩阵 | `docs/testing/coverage-matrix.md` | 测试经理 | 受控 |
| CI-DOC-25 | 缺陷登记表 | `docs/testing/defect-register.md` | 测试经理 | 受控 |
| CI-DOC-26 | PR 评审清单 | `docs/testing/pr-review-checklist.md` | 测试经理 | 受控 |
| CI-DATA-01 | 演示数据集 v3.0 | `test-data/demo.json` | 测试经理 | 受控 |
| CI-DATA-02 | 场景数据 | `test-data/scenarios/*.json` | 测试经理 | 受控 |
| CI-SRC-30 | 后端测试支撑 | `server/src/test/.../support/` | 测试经理 | 受控 |
| CI-SRC-31 | 跨模块集成测试 | `server/src/test/.../acceptance/` | 测试经理 | 受控 |
| CI-SRC-32 | E2E 与契约检查工程 | `qa/` | 测试经理 | 受控 |

### 2.4 环境与配置（CI-ENV，配置经理）

| 编号 | 配置项 | 位置 | 负责人 | 状态 |
|---|---|---|---|---|
| CI-ENV-01 | 开发 Compose（db+server+web） | `compose.yaml` | 配置经理 | 受控 |
| CI-ENV-02 | QA/E2E Compose | `compose.qa.yaml` | 配置经理 | 受控 |
| CI-ENV-03 | 环境变量模板 | `.env.example` | 配置经理 | 受控 |
| CI-ENV-04 | 数据库初始化 | `infra/db/init.sql` | 配置经理 | 受控 |
| CI-ENV-05 | 环境加载脚本 | `scripts/load-env.ps1` | 配置经理 | 受控 |
| CI-ENV-06 | 统一检查脚本 | `scripts/check.ps1` | 配置经理 | 受控 |
| CI-ENV-07 | 后端应用配置（base/dev/test/e2e） | `server/src/main/resources/application*.yml` | 配置经理 | 受控 |
| CI-ENV-08 | 后端镜像 | `server/Dockerfile` | 配置经理 | 受控 |
| CI-ENV-09 | 前端镜像 | `web/Dockerfile` | 配置经理 | 受控 |
| CI-ENV-10 | Nginx 反代配置 | `infra/nginx/default.conf` | 配置经理 | 受控 |

### 2.5 后端源码（CI-SRC，配置经理）

| 编号 | 配置项 | 位置 | 负责人 | 状态 |
|---|---|---|---|---|
| CI-SRC-40 | 演示数据种子导入器框架 | `server/src/main/java/com/campus/events/dev/` | 配置经理 | 受控 |

## 3. 基线清单

| 基线 | 内容 | 状态 |
|---|---|---|
| `baseline/init-v1.0` | 任务包并入 + 仓库结构定型 | 已建立 |
| `baseline/task-package-v2.0` | 任务包 v2.0 基线 | 已建立 |
| `baseline/task-package-v3.0` | 任务包 v3.0 基线（含 AN 活动状态机对齐） | 已建立 |

其余基线（requirements / design / release）随对应交付物评审通过后建立并在此登记。
