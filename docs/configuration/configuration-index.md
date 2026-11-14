# 配置项索引（Configuration Index）

> 项目：campus-sched　负责人：杨瀚霖（55231031，配置经理）
> 本文是"配置库"交付物（任务包 §5 交付物 10）的索引部分，是配置项与基线状态的**唯一登记处**（single source of truth）：编号、名称、版本、状态均以此为准；只登记**真实纳入版本库**的配置项，未产生的内容不预登记。《配置管理计划》§5.2 与 T08 Word 附录B 的基线表为课程/计划用的**同步视图**，随本索引同步维护，不作为第二事实来源。
> 更新：随各模块交付与基线建立持续维护；最近一次 2026-11-14 同步 main（cbf4945）：登记两份设计图源（CS-CI-MODEL-01/02）、用户手册 v0.1→v0.2；前次已登记缺陷登记表 v0.4、软件测试报告 test-report.md v1.1（CS-CI-DOC-29）、配置管理计划 v1.6。

## 修订历史

| 日期 | 修订人 | 修订说明 |
|---|---|---|
| 2026-10-13 | 杨瀚霖 | 初版（随 CM-01 配置库交付） |
| 2026-10-16 | 杨瀚霖 | 补录 QA 测试文档与演示/场景数据 |
| 2026-10-30 | 杨瀚霖 | 修正 init-v1.0 基线内容、索引任务书路径、同步视图说明 |
| 2026-11-13 | 杨瀚霖 | 任务 6 补全六人模块源码与各岗位文档登记、基线状态 |
| 2026-11-14 | 杨瀚霖 | 登记发布说明/清单（CS-CI-REL-01）、软件测试报告（CS-CI-DOC-28）、用户手册 PDF（CS-CI-DOC-64） |
| 2026-11-14 | 杨瀚霖 | §3 基线清单补 requirements/design 两条基线并补真实建立时间（2026-10-13，已打 tag）；与计划 §5.2 同步 |
| 2026-11-14 | 杨瀚霖 | 同步 main（46df56d）：缺陷登记表 v0.1→v0.4，登记软件测试报告 test-report.md v1.1（CS-CI-DOC-29） |
| 2026-11-14 | 杨瀚霖 | 登记两份设计图源（CS-CI-MODEL-01/02）、用户手册 v0.1→v0.2（同步 main cbf4945，PR #31 图源合入） |

## 1. 说明

- 编号前缀见《配置管理计划》§3.1；"版本"列为该配置项当前版本，以各文档头部/文件内标注为准，由对应负责人按《配置管理计划》§5.3 版本号规则维护。
- "状态"列：`受控` = 已纳入 Git 且可追溯；`基线` = 已进入某基线（见 §3）。
- 负责人为配置项的产出/维护方，按《04 协作与集成规程》§1 模块所有权划分。
- `—` 表示该配置项尚未在索引中显式登记版本（如无版本头的单件、或待对应负责人补充），不臆造版本号。
- 源代码以 Git 提交与基线 tag 追踪，不设独立文档版本号；"版本"列对源码项标 `—`。

## 2. 配置项登记表

### 2.1 任务包与元文件（CS-CI-PKG / CS-CI-DOC）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-PKG-01 | 总开发任务书 v3.0 | `taskbook.md` | v3.0 | 项目经理 | 受控 |
| CS-CI-PKG-02 | 公共契约（01–05）v3.0 | `agents/common/` | v3.0 | 项目经理 | 受控 |
| CS-CI-PKG-03 | 六份个人任务书 | `agents/<学号>-<模块>/agent.md` | v3.0 | 各岗位 | 受控 |
| CS-CI-DOC-01 | 仓库约定 | `AGENTS.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-02 | 项目名片 | `README.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-03 | 仓库结构指南 | `docs/configuration/repo-structure.md` | v0.2 | 配置经理 | 受控 |
| CS-CI-DOC-04 | 配置管理计划 | `docs/configuration/configuration-management-plan.md` | v1.6 | 配置经理 | 受控 |
| CS-CI-DOC-05 | 配置管理计划（课程提交版，Word） | `docs/configuration/T08_配置管理计划.docx` | v1.6 | 配置经理 | 受控 |
| CS-CI-DOC-06 | 配置项索引（本索引） | `docs/configuration/configuration-index.md` | — | 配置经理 | 受控 |

### 2.2 课程下发材料（CS-CI-DOC，只读）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-10 | 选题/分工/工具/考核表 | `docs/course/` | — | 只读，全员 | 受控（不改） |
| CS-CI-DOC-11 | 六份角色确认表 | `docs/course/role-confirmations/` | — | 只读，全员 | 受控（不改） |

### 2.3 管理文档（CS-CI-DOC，项目经理）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-30 | 初始项目计划 | `docs/management/initial-project-plan.md` | v0.9 | 项目经理 | 受控 |
| CS-CI-DOC-31 | 详细项目计划 | `docs/management/detailed-project-plan.md` | v3.1.3 | 项目经理 | 受控 |
| CS-CI-DOC-32 | 决策记录 | `docs/management/decision-log.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-33 | 风险清单 | `docs/management/risks.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-34 | 集成记录 | `docs/management/integration-log.md` | — | 项目经理 | 受控 |
| CS-CI-DOC-35 | 软件开发计划（课程提交版，Word） | `docs/management/T08_软件开发计划.docx` | — | 项目经理 | 受控 |

### 2.4 需求文档（CS-CI-DOC，系统分析员）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-40 | 软件需求规约 | `docs/requirements/software-requirements.md` | v1.1 | 系统分析员 | 受控 |
| CS-CI-DOC-41 | 用例文档 | `docs/requirements/use-cases.md` | v1.1 | 系统分析员 | 受控 |
| CS-CI-DOC-42 | 业务规则 | `docs/requirements/business-rules.md` | v1.1 | 系统分析员 | 受控 |
| CS-CI-DOC-43 | 需求追踪 | `docs/requirements/traceability.md` | v1.1 | 系统分析员 | 受控 |
| CS-CI-DOC-44 | 补充规约 | `docs/requirements/supplementary-specification.md` | v1.1 | 系统分析员 | 受控 |
| CS-CI-DOC-45 | 软件需求规约（课程提交版，Word） | `docs/requirements/T08_软件需求规约.docx` | — | 系统分析员 | 受控 |

### 2.5 设计文档（CS-CI-DOC，设计师）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-50 | 系统设计 | `docs/design/system-design.md` | v0.2 | 设计师 | 受控 |
| CS-CI-DOC-51 | 数据库设计 | `docs/design/database-design.md` | — | 设计师 | 受控 |
| CS-CI-DOC-52 | 迁移登记 | `docs/design/migration-register.md` | — | 设计师 | 受控 |
| CS-CI-DOC-53 | 软件设计（课程提交版） | `docs/design/software-design-task4.md`（+ `.docx`/`.pdf`） | — | 设计师 | 受控 |

### 2.6 界面文档与用户手册（CS-CI-DOC，界面设计师）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-60 | 用户手册 | `docs/ui/user-manual.md` | v0.2 | 界面设计师 | 受控 |
| CS-CI-DOC-61 | 界面说明 | `docs/ui/interface-description.md` | v1.1 | 界面设计师 | 受控 |
| CS-CI-DOC-62 | UI 接口接入矩阵 | `docs/ui/api-matrix.md` | v0.1 | 界面设计师 | 受控 |
| CS-CI-DOC-63 | 用户界面说明文档（课程提交版，Word） | `docs/ui/T08_用户界面说明文档.docx` | — | 界面设计师 | 受控 |
| CS-CI-DOC-64 | 用户手册（课程提交版，PDF） | `docs/ui/T08_用户手册.pdf` | v0.2 | 界面设计师 | 受控 |

### 2.7 测试文档与支撑（CS-CI-DOC / CS-CI-DATA / CS-CI-SRC，测试经理）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-20 | 测试计划 | `docs/testing/test-plan.md` | v0.2 | 测试经理 | 受控 |
| CS-CI-DOC-21 | 测试设计 | `docs/testing/test-design.md` | v0.2 | 测试经理 | 受控 |
| CS-CI-DOC-22 | 测试数据设计 | `docs/testing/test-data-plan.md` | v0.2 | 测试经理 | 受控 |
| CS-CI-DOC-23 | 测试 ID 需求清单 | `docs/testing/testid-requirements.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-24 | 需求覆盖矩阵 | `docs/testing/coverage-matrix.md` | v3.0 | 测试经理 | 受控 |
| CS-CI-DOC-25 | 缺陷登记表 | `docs/testing/defect-register.md` | v0.4 | 测试经理 | 受控 |
| CS-CI-DOC-26 | PR 评审清单 | `docs/testing/pr-review-checklist.md` | — | 测试经理 | 受控 |
| CS-CI-DOC-27 | 软件测试计划（课程提交版，Word） | `docs/testing/T08_软件测试计划.docx` | — | 测试经理 | 受控 |
| CS-CI-DOC-28 | 软件测试报告（课程提交版，Word） | `docs/testing/T08_软件测试报告.docx` | v1.0 | 测试经理 | 受控 |
| CS-CI-DOC-29 | 软件测试报告 | `docs/testing/test-report.md` | v1.1 | 测试经理 | 受控 |
| CS-CI-DATA-01 | 演示数据集 v3.0 | `test-data/demo.json` | v3.0 | 测试经理 | 受控 |
| CS-CI-DATA-02 | 场景数据 | `test-data/scenarios/*.json` | — | 测试经理 | 受控 |
| CS-CI-SRC-30 | 后端测试支撑 | `server/src/test/.../support/` | — | 测试经理 | 受控 |
| CS-CI-SRC-31 | 跨模块集成测试 | `server/src/test/.../acceptance/` | — | 测试经理 | 受控 |
| CS-CI-SRC-32 | E2E 与契约检查工程 | `qa/` | — | 测试经理 | 受控 |

### 2.8 后端源码（CS-CI-SRC，按模块）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-SRC-50 | 基础工程与公共类型 | `server/`（foundation/auth/contract + pom/mvnw/.mvn） | — | 项目经理 | 受控 |
| CS-CI-SRC-60 | 活动与报名模块 | `server/.../activity`、`.../registration` | — | 系统分析员 | 受控 |
| CS-CI-SRC-70 | 资源与调度模块 | `server/.../resource`、`.../scheduling`、`server/src/main/resources/db/migration/` | — | 设计师 | 受控 |
| CS-CI-SRC-40 | 改期/通知/开发支撑模块（含审计事件） | `server/.../change`、`.../notification`、`.../dev`（含 ChangePort/EventPort、审计/outbox 与种子导入器） | — | 配置经理 | 受控 |

### 2.9 前端源码（CS-CI-SRC，界面设计师）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-SRC-80 | 前端工程（Vue 3 / TypeScript） | `web/` | — | 界面设计师 | 受控 |

### 2.10 环境与配置（CS-CI-ENV，配置经理）

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

### 2.11 接口契约、发布与验收证据（CS-CI-DOC / CS-CI-REL / CS-CI-ART）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-DOC-70 | OpenAPI 契约（40 接口） | `contracts/openapi.yaml` | — | 项目经理 | 受控 |
| CS-CI-ART-01 | 验收证据（截图/测试结果） | `artifacts/`（screenshots、test-results） | — | 产出者所有 | 受控 |
| CS-CI-REL-01 | 发布说明与发布清单 | `release/README.md`、`release/manifest.json` | v1.0.0 | 配置经理 | 受控 |

### 2.12 模型图（CS-CI-MODEL，设计师）

| 编号 | 配置项 | 位置 | 版本 | 负责人 | 状态 |
|---|---|---|---|---|---|
| CS-CI-MODEL-01 | 首次资源确认活动图（UC-08） | `models/activity/首次资源确认活动图.drawio`（+ `.png`） | — | 设计师 | 受控 |
| CS-CI-MODEL-02 | 首次活动申请与发布用例图（UC-05~09） | `models/use-case/首次活动申请与发布用例图.drawio`（+ `.png`） | — | 设计师 | 受控 |

### 2.13 未产生/待补内容（不预登记）

- **项目考核评估表**（任务 6 交付物，项目经理）：课程提交物，由项目经理负责，不属配置库源码/文档项。

## 3. 基线清单

| 基线 | 内容 | tag commit 时间 | 授权（CCB） | 状态 |
|---|---|---|---|---|
| `baseline/init-v1.0` | 任务包并入 + 仓库结构定型 | 2026-09-25 22:15:33（commit=tag） | 待核实（无追溯 CCB 记录） | 已建立（tag 存在） |
| `baseline/task-package-v2.0` | 任务包 v2.0 基线 | 2026-09-26 19:59:40（commit=tag） | 待核实（无追溯 CCB 记录） | 已建立（tag 存在） |
| `baseline/requirements-v1.0` | 需求规范评审通过（需求文档族 v1.1，v3.0 对齐） | 2026-10-13（7fb247b） | 项目经理（CCB）· 冀项阳（2026-11-14 授权） | 已建立（tag 存在） |
| `baseline/design-v1.0` | 设计文档评审通过（设计文档族 v0.2，v3.0 22 表） | 2026-10-13（bbeb984） | 项目经理（CCB）· 冀项阳（2026-11-14 授权） | 已建立（tag 存在） |
| `baseline/task-package-v3.0` | 任务包 v3.0 基线（含 AN 活动状态机对齐） | commit 2026-10-14 15:44:47 / tag 15:54:57 | 待核实（无追溯 CCB 记录） | 已建立（tag 存在） |
| `baseline/release-v1.0.0` | 任务 6 最终交付（发布说明/清单 v1.0.0） | 2026-11-14（最终交付） | 项目经理（CCB）· 冀项阳（2026-11-14 授权） | 待建立（release/ 待 PR #29 合入 main 后打 tag） |

时间口径与授权规则同《配置管理计划》§5.2：tag commit 时间（git 可查）与 CCB 授权时间不可混同。requirements / design 的内容于 2026-10-13 冻结入库（v3.0 协调对齐），CCB 授权于 2026-11-14 补授（非倒填）；release 的建立时间为最终交付日 2026-11-14，待 release/ 内容随 PR #29 合入 main 后打 tag。历史基线无追溯 CCB 记录者标"待核实"，不补造、不倒造审批。
