# 仓库结构与目录指南（repo-structure）

> **用途**：回答"什么东西放在哪里"。每个目录的定位、放什么/不放什么、命名规则，以及一张"该放哪"路由表。
> **读者**：全体成员（人类）与 AI agent。人类从 `README.md` 进入，agent 从 `AGENTS.md` §1 进入，都指向本文件。
> **维护**：配置经理（杨瀚霖）维护。**新增/移动/重命名/删除任何目录都是配置项变更**：走变更流程 → 更新本指南 → 同步 `AGENTS.md` §1 总览树。
>
> 版本：v0.2（2026-09-26，按开发任务包 v2.0 重组目录结构）

## 1. 设计原则

1. **任务包优先**：目录布局以 `agents/common/04-协作与集成规程.md` §1 的模块所有权表和 `agent.md` §6 的交付物路径为准；本指南与其一致，冲突时以任务包为基准。
2. **单一职责**：每个目录有明确的"放什么/不放什么"，见 §3。
3. **目录英文、文件名按归属定**：目录名英文小写 kebab-case；课程下发文档保留中文原文件名；任务包文件（`agent.md`、`agents/**`）不改名；课程交付文档使用任务包规定的英文 kebab-case `.md` 文件名（见 §5）。
4. **源与产物分离**：可编辑源文件（`.drawio`）放 `models/`，随文档评审的导出产物与文档放 `docs/` 对应目录；Figma 源在仓库外，只导出到 `docs/ui/prototype/`。
5. **活文档与冻结文档分离**：`.md` 是持续维护的"活文档"（可 diff、可改）；`.docx/.xlsx` 是评审后冻结的课程材料（改版本 = 另存新文件名）。
6. **骨架先行**：未启用的目录用 `.gitkeep` 占位；代码目录的内部结构由各模块负责人在 M0 按任务包文件清单创建。

## 2. 总览

```text
campus-sched/
├── AGENTS.md / README.md / .gitignore   # 仓库元文件（仅此三件）
├── agent.md                     # 开发任务包总说明（固定文件）
├── agents/                      # 任务包：common/ 公共契约 + 六份个人任务书（固定结构）
├── docs/                        # 文档区（见 §3.1）
├── server/                      # Java 后端（见 §3.2）
├── web/                         # Vue 前端
├── qa/                          # E2E 与契约检查工程
├── test-data/                   # 演示与测试数据
├── contracts/                   # OpenAPI 规范
├── infra/                       # db 初始化、Nginx 配置
├── scripts/                     # 环境与检查脚本
├── release/                     # 发布包与发布说明
├── artifacts/                   # 验收证据
└── models/                      # Drawio UML 源文件
```

## 3. 目录详解

### 3.1 `docs/` — 文档区

**总规则**：只放文档与文档伴生的导出产物；不放源代码（→ `server/` 等）、不放 `.drawio` 源文件（→ `models/`）。每个岗位目录内维护本人的 `handoff.md` 交接单（格式见 04-协作规程 §4）。

| 目录 | 定位 | 对应交付物 | 负责人 |
|---|---|---|---|
| `docs/course/` | 课程下发的管理文档 | —（过程依据，只读） | 只读，全员 |
| `docs/course/role-confirmations/` | 六份成员角色确认表 | — | 只读，全员 |
| `docs/management/` | 计划族：初始/详细项目计划、decision-log、risks、integration-log | 交付物 1、4 | 项目经理 |
| `docs/requirements/` | 需求族：需求规范、用例、业务规则、需求追踪 | 交付物 2 | 系统分析员 |
| `docs/design/` | 设计族：system-design、database-design、migration-register 及图源 | 交付物 7 | 设计师 |
| `docs/ui/` | 界面族：`prototype/`（原型导出）、interaction-spec、user-manual | 交付物 3、12 | 界面设计师 |
| `docs/testing/` | 测试族：test-plan、test-design、test-report、策略评测说明 | 交付物 5、8、9 | 测试经理 |
| `docs/configuration/` | 配置族：configuration-management-plan、configuration-index、本指南 | 交付物 6、10（索引） | 配置经理 |

各目录细则：

**`docs/course/`**
- 放：课程下发的选题、分工、工具、考核等管理文档
- 不放：任何成员自产文档；**不改内容、不改文件名**（红线，见 `AGENTS.md` §2）

**`docs/management/`**
- 放：`initial-project-plan.md`（含交付物清单与排期状态）、`detailed-project-plan.md`、`decision-log.md`、`risks.md`、`integration-log.md`
- 不放：他人模块的进度记录（各岗位写自己的 handoff.md）

**`docs/requirements/`**
- 放：`software-requirements.md`、`use-cases.md`、`business-rules.md`、`traceability.md`
- 不放：用例图 `.drawio` 源文件（→ `models/use-case/`）

**`docs/design/`**
- 放：`system-design.md`、`database-design.md`、`migration-register.md`、图源导出图
- 不放：`.drawio` 源文件（→ `models/` 对应子目录）；数据库迁移 SQL（→ `server/src/main/resources/db/migration/`，设计师所有）

**`docs/ui/`**
- 放：`prototype/`（原型导出 png/pdf，按迭代分子目录）、`interaction-spec.md`、`user-manual.md`
- 不放：Figma 源文件（在仓库外）；前端代码（→ `web/`）

**`docs/testing/`**
- 放：`test-plan.md`、`test-design.md`、`test-data-plan.md`、`test-report.md`、`strategy-evaluation.md`
- 不放：可执行测试代码（→ `qa/`、`server/src/test/`）；测试数据 JSON（→ `test-data/`）

**`docs/configuration/`**
- 放：`configuration-management-plan.md`、`configuration-index.md`（配置项/基线清单索引）、`repo-structure.md`（本指南，活文档例外）
- 不放：其他岗位文档"暂存"（各归各位）

### 3.2 代码区（所有权细分见 04-协作规程 §1）

| 目录 | 定位 | 所有者 |
|---|---|---|
| `server/` | Java 21 / Spring Boot 单体后端，根包 `com.campus/events`（foundation、auth、contract、activity、registration、resource、scheduling、change、notification、audit、dev）；`pom.xml`、`mvnw*`、`.mvn/` 由项目经理所有；`db/migration/` 由设计师所有 | 按包归属各成员 |
| `web/` | Vue 3 / TypeScript 前端全部（含 package.json 及锁文件、测试、样式、API 客户端） | 界面设计师 |
| `qa/` | Playwright E2E、契约检查、策略评测脚本工程 | 测试经理 |
| `test-data/` | `demo.json`、`scenarios/*.json`（测试经理编写格式，配置经理实现导入） | 测试经理 |
| `contracts/` | `openapi.yaml`（OpenAPI 3.0.3，38 个接口） | 项目经理 |
| `infra/` | `db/init.sql`、`nginx/default.conf` | 配置经理 |
| `scripts/` | `load-env.ps1`、`check.ps1`、`retry-notifications.ps1` 等 | 配置经理 |
| `release/` | 发布包、`README.md`（发布说明）、`manifest.json` | 配置经理 |
| `artifacts/` | 验收证据：`screenshots/`、`test-results/`、`evaluation/`（真实运行产物才放这里） | 产出者所有 |

- 通用：不放 IDE 生成物、依赖目录（`node_modules/`）、构建产物（`target/`、`dist/`）、`.env`（`.gitignore` 已排除，别手动提交）
- `server/src/test/java/com/campus/events/acceptance/` 与 `support/` 归测试经理；其余模块测试目录与生产包镜像对应，归各模块负责人

### 3.3 `models/` — 模型区（Drawio UML 源文件）

| 子目录 | 图类型 | 主要产出阶段 | 主要负责人 |
|---|---|---|---|
| `use-case/` | 用例图 | 需求 | 系统分析员 |
| `class/` | 类图 | 设计 | 设计师 |
| `sequence/` | 时序图（用例实现/改期时序） | 设计 | 设计师 |
| `activity/` | 活动图（业务流程） | 需求–设计 | 系统分析员 / 设计师 |
| `state/` | 状态图（活动/改期状态机） | 设计 | 设计师 |
| `deployment/` | 部署图 | 设计–开发 | 设计师 |

子目录**按需创建**：首次产出某类图时 `mkdir` 即可；当前已建 `use-case/`、`activity/`，其余待首次产出时创建。

- 放：`.drawio` 可编辑源文件 + **同名导出图片**（如 `state/活动状态机.drawio` + `state/活动状态机.png`）
- 不放：只给某份文档用的临时截图（随文档放 `docs/` 对应目录）；Mermaid 图直接内嵌设计文档，不放这里
- 命名：`<主题>.drawio`，主题用中文短语；导出图片与源文件同基名

### 3.4 根目录

只放仓库级元文件：`AGENTS.md`（agent 约定）、`README.md`（项目名片）、`.gitignore`，以及**固定文件** `agent.md` 与 `agents/`（开发任务包，见 §1 原则 1）。
**不新建任何顶层目录或顶层散文件**——有需求先查 §4 路由表，找不到再找配置经理走变更。

## 4. "该放哪"路由表（速查）

| 你手里的东西 | 去处 |
|---|---|
| 课程下发的表/模板/说明 | `docs/course/`（原文件名） |
| 项目计划、决策记录、风险清单 | `docs/management/` |
| 需求规范、用例、业务规则、需求追踪 | `docs/requirements/` |
| 用例图/类图/时序图/活动图/状态图/部署图的 `.drawio` | `models/<图类型>/` |
| 设计文档、数据库设计、迁移登记 | `docs/design/` |
| 数据库迁移 SQL | `server/src/main/resources/db/migration/` |
| Figma 原型导出、交互规范、用户手册 | `docs/ui/`（原型入 `docs/ui/prototype/<迭代名>/`） |
| 测试计划、测试设计、测试报告 | `docs/testing/` |
| 可执行测试：后端单元/集成（模块内）、跨模块验收 | `server/src/test/` 对应包（模块测试 / `acceptance/`） |
| E2E 与契约检查代码 | `qa/` |
| 演示/测试数据 JSON | `test-data/` |
| OpenAPI 规范 | `contracts/openapi.yaml` |
| 环境脚本、检查脚本、Nginx/db 配置 | `scripts/` / `infra/` |
| 发布包与发布说明 | `release/` |
| 截图、测试结果、策略评测等验收证据 | `artifacts/<子目录>/` |
| CM 计划、配置项索引、基线/变更记录、结构指南 | `docs/configuration/` |
| 拿不准 | 先查本表 → 再查 `agent.md` §6 交付物对照 → 仍不确定就问配置经理，**不要自创目录** |

## 5. 命名规范速查

| 对象 | 规范 | 示例 |
|---|---|---|
| 目录 | 英文小写 kebab-case | `docs/configuration/` |
| 课程下发文档 | 原文件名，不改 | `项目组选题说明.docx` |
| 任务包文件 | 固定名，不改不拆 | `agent.md`、`agents/common/01-开发基线.md` |
| 课程交付文档（.md） | 任务包规定的英文 kebab-case 名 | `test-plan.md`、`system-design.md` |
| 其他新增文档 | `<类型>-<主题>-v<主>.<次>.<扩展名>` | `变更记录-2026-10.md` |
| 原型导出 | `docs/ui/prototype/<迭代名>/` | `iter-1/资源日历.png` |
| Drawio | `<主题>.drawio` + 同名 `.png` | `state/活动状态机.drawio` |
| 分支 / tag / 提交信息 | 见 `AGENTS.md` §2（单一来源，本指南不重复） | `work/55230223-foundation` |

## 6. 结构变更流程

1. 提出变更（飞书或 GitHub Issue）→ 配置经理受理并登记变更记录；
2. 评审（涉及交付物目录的需项目经理参加；涉及任务包契约的走 02 契约 §7 规程）；
3. 通过后：配置经理执行目录调整 → 更新本指南版本号 → 同步 `AGENTS.md` §1 总览树 → 打基线 tag。
