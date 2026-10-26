# 配置项索引（Configuration Index）

> 项目：campus-sched　负责人：杨瀚霖（55231031，配置经理）
> 本文是"配置库"交付物（任务包 §5 交付物 10）的索引部分，是配置项与基线状态的**唯一登记处**（single source of truth）：编号、名称、版本、状态均以此为准；只登记**真实纳入版本库**的配置项，未产生的内容不预登记。
> 更新：随各模块交付与基线建立持续维护。

## 1. 说明

- 编号前缀见《配置管理计划》§3.1；"版本"列为该配置项当前版本，以各文档头部/文件内标注为准，由对应负责人按《配置管理计划》§5.3 版本号规则维护。
- "状态"列：`受控` = 已纳入 Git 且可追溯；`基线` = 已进入某基线（见 §3）。
- 负责人为配置项的产出/维护方，按《04 协作与集成规程》§1 模块所有权划分。
- `—` 表示该配置项尚未在索引中显式登记版本（如无版本头的单件、或待对应负责人补充），不臆造版本号。

## 2. 配置项登记表

### 2.1 任务包与元文件（CS-CI-PKG / CS-CI-DOC）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-PKG-01 | 总开发任务书 v3.0 | `taskbook.md` | v3.0 | 项目经理 | 受控 |
| CS-CI-PKG-02 | 公共契约（01–05）v3.0 | `agents/common/` | v3.0 | 项目经理 | 受控 |
| CS-CI-PKG-03 | 六份个人任务书 | `agents/<分支>/agent.md` | v3.0 | 各岗位 | 受控 |
| CS-CI-DOC-01 | 仓库约定 | `AGENTS.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-02 | 项目名片 | `README.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-03 | 仓库结构指南 | `docs/configuration/repo-structure.md` | v0.2 | 配置经理 | 受控 |
| CS-CI-DOC-04 | 配置管理计划 | `docs/configuration/configuration-management-plan.md` | v1.5 | 配置经理 | 受控 |
| CS-CI-DOC-05 | 配置管理计划（课程提交版，Word） | `docs/configuration/T08_配置管理计划.docx` | v1.5 | 配置经理 | 受控 |

### 2.2 课程下发材料（CS-CI-DOC，只读）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-10 | 选题/分工/工具/考核表 | `docs/course/` | — | 只读，全员 | 受控（不改） |
| CS-CI-DOC-11 | 六份角色确认表 | `docs/course/role-confirmations/` | — | 只读，全员 | 受控（不改） |

### 2.3 测试文档与支撑（CS-CI-DOC / CS-CI-DATA / CS-CI-SRC）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-20 | 测试计划 | `docs/testing/test-plan.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-21 | 测试设计 | `docs/testing/test-design.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-22 | 测试数据设计 | `docs/testing/test-data-plan.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-23 | 测试 ID 需求清单 | `docs/testing/testid-requirements.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-24 | 需求覆盖矩阵 | `docs/testing/coverage-matrix.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-25 | 缺陷登记表 | `docs/testing/defect-register.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-26 | PR 评审清单 | `docs/testing/pr-review-checklist.md` | — | 测试经理 | 受控 |
| CS-CI-DATA-01 | 演示数据集 v3.0 | `test-data/demo.json` | v3.0 | 测试经理 | 受控 |
| CS-CI-DATA-02 | 场景数据 | `test-data/scenarios/*.json` | — | 测试经理 | 受控 |
| CS-CI-SRC-30 | 后端测试支撑 | `server/src/test/.../support/` | — | 测试经理 | 受控 |
| CS-CI-SRC-31 | 跨模块集成测试 | `server/src/test/.../acceptance/` | — | 测试经理 | 受控 |
| CS-CI-SRC-32 | E2E 与契约检查工程 | `qa/` | — | 测试经理 | 受控 |

### 2.4 环境与配置（CS-CI-ENV，配置经理）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-ENV-01 | 开发 Compose（db+server+web） | `compose.yaml` | — | 配置经理 | 受控 |
| CS-CI-ENV-02 | QA/E2E Compose | `compose.qa.yaml` | — | 配置经理 | 受控 |
| CS-CI-ENV-03 | 环境变量模板 | `.env.example` | — | 配置经理 | 受控 |
| CS-CI-ENV-04 | 数据库初始化 | `infra/db/init.sql` | — | 配置经理 | 受控 |
| CS-CI-ENV-05 | 环境加载脚本 | `scripts/load-env.ps1` | — | 配置经理 | 受控 |
| CS-CI-ENV-06 | 统一检查脚本 | `scripts/check.ps1` | — | 配置经理 | 受控 |
| CS-CI-ENV-07 | 后端应用配置（base/dev/test/e2e） | `server/src/main/resources/application*.yml` | — | 配置经理 | 受控 |
| CS-CI-ENV-08 | 后端镜像 | `server/Dockerfile` | — | 配置经理 | 受控 |
| CS-CI-ENV-09 | 前端镜像 | `web/Dockerfile` | — | 配置经理 | 受控 |
| CS-CI-ENV-10 | Nginx 反代配置 | `infra/nginx/default.conf` | — | 配置经理 | 受控 |

### 2.5 后端源码（CS-CI-SRC，配置经理）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-SRC-40 | 演示数据种子导入器框架 | `server/src/main/java/com/campus/events/dev/` | — | 配置经理 | 受控 |

## 3. 基线清单

| 基线 | 内容 | tag commit 时间 | 授权（CCB） | 状态 |
|---|---|---|---|---|
| `baseline/init-v1.0` | 任务包并入 + 仓库结构定型 | 2026-09-25 22:15:33（commit=tag） | 待核实（无追溯 CCB 记录） | 已建立（tag 存在） |
| `baseline/task-package-v2.0` | 任务包 v2.0 基线 | 2026-09-26 19:59:40（commit=tag） | 待核实（无追溯 CCB 记录） | 已建立（tag 存在） |
| `baseline/task-package-v3.0` | 任务包 v3.0 基线（含 AN 活动状态机对齐） | commit 2026-10-14 15:44:47 / tag 15:54:57 | 待核实（无追溯 CCB 记录） | 已建立（tag 存在） |

其余基线（requirements / design / release）随对应交付物评审通过后建立并在此登记。时间口径与授权规则同《配置管理计划》§5.2：tag commit 时间（git 可查）与 CCB 授权时间不可混同；历史基线无追溯 CCB 记录者标"待核实"，不补造、不倒造审批。
