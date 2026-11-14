# 课程导出目录清单（Export Manifest）

> 项目：campus-sched　编制：杨瀚霖（55231031，配置经理）　日期：2026-11-14
> 本文是任务 6「配置库」最终课程提交包（.zip，项目经理打包提交学习通）的**可复现导出清单**：把 Git 版本库中的配置项，按编号/版本/路径映射为课程导出目录中的文件名，并给出**可执行、字节保真的生成脚本**（`release/export.py`）。编号与版本以《配置项索引》（`docs/configuration/configuration-index.md`）为唯一登记处。

## 1. 导出目录命名规则

课程导出目录中每个文件命名：`<编号>_v<版本>_<中文名>.<扩展名>`；无独立版本号的单件（如源代码、契约、环境配置）以 Git 提交与基线 tag 追踪，不臆造版本号，导出名省略 `_v<版本>` 段。

| 段 | 规则 | 例 |
|---|---|---|
| 编号 | `CS-CI-<类别缩写>-<序号>`，与索引一致 | `CS-CI-DOC-05` |
| 版本 | 文档/交付包用 `v<主>.<次>`，二进制同其对应 .md 版本 | `v1.7` |
| 中文名 | 索引「配置项」列的中文名 | `配置管理计划` |

## 2. 课程提交主目录（当前版本，按岗位）

| 导出文件名 | 来源路径 | 版本 | 负责人 |
|---|---|---|---|
| `CS-CI-PKG-01_v3.0_总开发任务书.md` | `taskbook.md` | v3.0 | 项目经理 |
| `CS-CI-DOC-04_v1.7_配置管理计划.md` | `docs/configuration/configuration-management-plan.md` | v1.7 | 配置经理 |
| `CS-CI-DOC-05_v1.7_配置管理计划.docx` | `docs/configuration/T08_配置管理计划.docx` | v1.7 | 配置经理 |
| `CS-CI-DOC-06_配置项索引.md` | `docs/configuration/configuration-index.md` | —（持续维护） | 配置经理 |
| `CS-CI-DOC-03_v0.2_仓库结构指南.md` | `docs/configuration/repo-structure.md` | v0.2 | 配置经理 |
| `CS-CI-REL-01_v1.0.0_发布说明.md` / `…_发布清单.json` / `…_导出清单.md` | `release/README.md`、`release/manifest.json`、`release/export-manifest.md` | v1.0.0 | 配置经理 |
| `CS-CI-DOC-30_v0.9_初始项目计划.md` | `docs/management/initial-project-plan.md` | v0.9 | 项目经理 |
| `CS-CI-DOC-31_v3.1.4_详细项目计划.md` | `docs/management/detailed-project-plan.md` | v3.1.4 | 项目经理 |
| `CS-CI-DOC-32_决策记录.md` / `CS-CI-DOC-33_风险清单.md` / `CS-CI-DOC-34_集成记录.md` | `docs/management/decision-log.md`、`risks.md`、`integration-log.md` | — | 项目经理 |
| `CS-CI-DOC-35_软件开发计划.docx` | `docs/management/T08_软件开发计划.docx` | — | 项目经理 |
| `CS-CI-DOC-40_v1.1_软件需求规约.md` 等需求族 | `docs/requirements/*.md`（规约/用例/业务规则/追踪/补充规约） | v1.1 | 系统分析员 |
| `CS-CI-DOC-45_软件需求规约.docx` | `docs/requirements/T08_软件需求规约.docx` | — | 系统分析员 |
| `CS-CI-DOC-50_v0.2_系统设计.md` / `CS-CI-DOC-51_数据库设计.md` / `CS-CI-DOC-52_迁移登记.md` | `docs/design/system-design.md`、`database-design.md`、`migration-register.md` | v0.2 / — / — | 设计师 |
| `CS-CI-DOC-53_软件设计.md/.docx/.pdf` | `docs/design/software-design-task4.{md,docx,pdf}` | — | 设计师 |
| `CS-CI-MODEL-01_v1.0_首次资源确认活动图.drawio/.png` | `models/activity/首次资源确认活动图.*` | v1.0 | 设计师 |
| `CS-CI-MODEL-02_v1.0_首次活动申请与发布用例图.drawio/.png` | `models/use-case/首次活动申请与发布用例图.*` | v1.0 | 设计师 |
| `CS-CI-DOC-60_v0.2_用户手册.md` | `docs/ui/user-manual.md` | v0.2 | 界面设计师 |
| `CS-CI-DOC-64_v0.2_用户手册.pdf` | `docs/ui/user-manual.pdf`（PR #27 成品） | v0.2 | 界面设计师 |
| `CS-CI-DOC-61_v1.1_界面说明.md` | `docs/ui/interface-description.md` | v1.1 | 界面设计师 |
| `CS-CI-DOC-62_v0.1_UI接口接入矩阵.md` | `docs/ui/api-matrix.md` | v0.1 | 界面设计师 |
| `CS-CI-DOC-63_用户界面说明文档.docx` | `docs/ui/T08_用户界面说明文档.docx` | — | 界面设计师 |
| `CS-CI-DOC-20_v0.2_测试计划.md` | `docs/testing/test-plan.md` | v0.2 | 测试经理 |
| `CS-CI-DOC-21_v0.2_测试设计.md` | `docs/testing/test-design.md` | v0.2 | 测试经理 |
| `CS-CI-DOC-22_v0.2_测试数据设计.md` | `docs/testing/test-data-plan.md` | v0.2 | 测试经理 |
| `CS-CI-DOC-23_测试ID需求清单.md` / `CS-CI-DOC-26_PR评审清单.md` | `docs/testing/testid-requirements.md`、`pr-review-checklist.md` | — | 测试经理 |
| `CS-CI-DOC-24_v3.0_需求覆盖矩阵.md` | `docs/testing/coverage-matrix.md` | v3.0 | 测试经理 |
| `CS-CI-DOC-25_v0.4_缺陷登记表.md` | `docs/testing/defect-register.md` | v0.4 | 测试经理 |
| `CS-CI-DOC-27_软件测试计划.docx` | `docs/testing/T08_软件测试计划.docx` | — | 测试经理 |
| `CS-CI-DOC-28_v1.0_软件测试报告.docx` | `docs/testing/T08_软件测试报告.docx` | v1.0 | 测试经理 |
| `CS-CI-DOC-29_v1.1_软件测试报告.md` | `docs/testing/test-report.md` | v1.1 | 测试经理 |
| `CS-CI-DATA-01_v3.0_演示数据集.json` | `test-data/demo.json` | v3.0 | 测试经理 |
| 源代码 / 契约 / 环境配置（`CS-CI-SRC-*` / `CS-CI-DOC-70` / `CS-CI-ENV-*` / `CS-CI-DATA-02` / `CS-CI-ART-01`） | `server/`、`web/`、`qa/`、`contracts/openapi.yaml`、`infra/`、`scripts/`、`compose*.yaml`、`test-data/scenarios/`、`artifacts/` | 以 Git/基线 tag 追踪 | 各模块负责人 |

> **用户手册 PDF 说明**：`docs/ui/user-manual.pdf`（PR #27 已合入成品）为当前 v0.2，导出以此为源；`docs/ui/T08_用户手册.pdf`（课程提交版导出，缺「报名参加」表述）已过时，待界面设计师重导后替换，详见索引 §2.6 注。

## 3. 可复现生成（执行脚本，非手工打包）

生成脚本 `release/export.py`，一条命令产出课程导出目录（当前版本 + 历史版本 + 核验报告）：

```powershell
python release/export.py --commit <提交> --out <导出目录>
```

脚本行为（全部经 Git 还原、**字节保真**）：

1. **提交锁定**：`--commit`（默认 `HEAD`）先经 `git rev-parse <ref>^{commit}` 解析为**唯一提交 SHA**；归档、当前工件读取与报告记录均用该 SHA，不在同一导出目录混入不同提交。
2. **整库快照**：`git archive --format=tar <SHA>` 抽出完整源码与工程树（`campus-sched-完整快照/`），与提交字节一致，可复现。
3. **文档重命名**：按 §2 映射，逐件 `git show <SHA>:<路径>` 取原始字节写为 `配置项文档/<编号>_v<版本>_<中文名>.<扩展名>`；二进制（docx/pdf/drawio/png）不经文本转码，与版本库 blob 字节一致。
4. **历史版本同目录**：对存在历史版本的文档，从显式 commit 另取旧版写入同一目录，以 `_v<版本>` 区分。
5. **任务 5 正式交付物核验**：按 §4.2 精确文件路径逐件核验，缺项打印明细并**返回退出码 2**（不「假通过」）。
6. **发布清单哈希校验**：读取同一候选 SHA 的 `release/manifest.json`，逐件按清单 `file` 字段核对散列文件路径，任何一件**文件缺失或哈希不匹配即返回退出码 3**（不再只比两个写死值）。
7. **输出目录安全**：`--out` 若指向已存在的非空目录或文件，直接拒绝退出（不递归删除），请另选唯一输出目录。

退出码：`1`=致命错误（提交无法解析 / 输出目录冲突 / 清单无法解析）；`2`=任务 5 正式交付物缺失；`3`=发布清单哈希不匹配。

二进制逐件核验示例（等价于脚本内部实现，可直接单独复现）：

```powershell
git show HEAD:docs/configuration/T08_配置管理计划.docx > CS-CI-DOC-05_v1.7_配置管理计划.docx
git show HEAD:docs/ui/user-manual.pdf            > CS-CI-DOC-64_v0.2_用户手册.pdf
git show HEAD:models/activity/首次资源确认活动图.drawio > CS-CI-MODEL-01_v1.0_首次资源确认活动图.drawio
```

## 4. 历史版本与任务 5 正式交付物

### 4.1 历史版本对照（工件 / 版本 / 来源提交 / 同目录输出）

历史版本不靠手工另存，而是从 Git 逐件还原（见 §3 第 4 步），与当前版**同目录**并存，版本↔commit 以各文档头部「修订历史」为准（`git` 逐件核实，不臆造）：

| 工件（配置项） | 历史版本 | 来源 commit | 同目录输出文件名（`编号_v版本_名称.扩展名`） |
|---|---|---|---|
| 配置管理计划 Markdown（CS-CI-DOC-04，当前 v1.7） | v1.0 | `bfa2cd6` | `CS-CI-DOC-04_v1.0_配置管理计划.md` |
| 同上 | v1.1 | `5d07897` | `CS-CI-DOC-04_v1.1_配置管理计划.md` |
| 同上 | v1.4 | `298780f` | `CS-CI-DOC-04_v1.4_配置管理计划.md` |
| 同上 | v1.5 | `1382623` | `CS-CI-DOC-04_v1.5_配置管理计划.md` |
| 同上 | v1.6 | `2981c37` | `CS-CI-DOC-04_v1.6_配置管理计划.md` |
| 配置管理计划 Word（CS-CI-DOC-05，课程提交版，当前 v1.7） | v1.0 | `5d07897` | `CS-CI-DOC-05_v1.0_配置管理计划.docx` |
| 同上 | v1.4 | `298780f` | `CS-CI-DOC-05_v1.4_配置管理计划.docx` |
| 同上 | v1.5 | `1382623` | `CS-CI-DOC-05_v1.5_配置管理计划.docx` |
| 同上 | v1.6 | `2981c37` | `CS-CI-DOC-05_v1.6_配置管理计划.docx` |
| 总开发任务书（CS-CI-PKG-01，当前 v3.0） | v2.0 | `9a80fa3` | `CS-CI-PKG-01_v2.0_总开发任务书.md` |
| 详细项目计划（CS-CI-DOC-31，当前 v3.1.4） | v3.0 | `b30c0f2` | `CS-CI-DOC-31_v3.0_详细项目计划.md` |
| 同上 | v3.1 | `a5e0724` | `CS-CI-DOC-31_v3.1_详细项目计划.md` |
| 同上 | v3.1.1 | `1fc3dd9` | `CS-CI-DOC-31_v3.1.1_详细项目计划.md` |
| 同上 | v3.1.2 | `2055c63` | `CS-CI-DOC-31_v3.1.2_详细项目计划.md` |
| 同上 | v3.1.3 | `4f0f304` | `CS-CI-DOC-31_v3.1.3_详细项目计划.md` |
| 测试计划（CS-CI-DOC-20，当前 v0.2） | v0.1 | `b5b1db3` | `CS-CI-DOC-20_v0.1_测试计划.md` |
| 系统设计（CS-CI-DOC-50，当前 v0.2） | v0.1 | `f6bd329` | `CS-CI-DOC-50_v0.1_系统设计.md` |

> 未独立入库的工作稿按实说明：配置管理计划 **v1.2** 未独立形成 Git 提交（§5.2 增补「基线授权」的修订内容随 v1.4 入库），**v1.3** 未形成独立版本，故二者不单独列出、不臆造 commit；其余文档若历史版本号早于上表所列最早版本，即为「工作区/未独立入库」状态，不纳入课程导出历史副本。

### 4.2 任务 5 正式交付物核验（精确文件路径）

任务 5（测试）正式交付物按下列**精确文件路径**核验，缺项返回退出码 2：

| 交付物 | 路径 | 状态（截至 main） |
|---|---|---|
| 测试计划 Word | `docs/testing/T08_软件测试计划.docx` | 已入库 |
| 测试设计 Markdown v1.0 | `docs/testing/test-design-v1.0.md` | **待 PR #23 合入** |
| 测试设计 Word | `docs/testing/T08_任务5_软件测试设计.docx` | **待 PR #23 合入** |
| 结构化用例 JSON | `test-data/test-design-cases-v1.0.json` | **待 PR #23 合入** |
| 测试数据设计 | `docs/testing/test-data-plan.md` | 已入库 |
| 需求覆盖矩阵 | `docs/testing/coverage-matrix.md` | 已入库 |
| 缺陷登记表 | `docs/testing/defect-register.md` | 已入库 |
| 测试报告 Word | `docs/testing/T08_软件测试报告.docx` | 已入库 |
| 测试报告 Markdown | `docs/testing/test-report.md` | 已入库 |

> 说明：任务 5「软件测试设计」三件正式交付物（Markdown v1.0、Word、结构化用例 JSON）已由测试经理在 `work/55230316-qa`（提交 `26c5dad`，PR #23）产出，尚未合入 main，故当前导出核验会因缺项返回退出码 2。属测试经理/项目经理交付依赖，配置经理不代产、不代合。

## 5. 与发布清单/索引的关系

- 编号、版本、路径、负责人以 `docs/configuration/configuration-index.md` 为唯一登记处；本清单是它在「课程导出目录」上的落点映射，不作第二事实来源。
- 发布内容与校验值见 `release/manifest.json`；干净启动步骤见 `release/README.md`。
- 导出目录中的历史版本副本一律由 §3 脚本从 Git 还原生成，**不**在版本库中重复存副本（避免与 Git 历史双写不一致）；生成脚本 `release/export.py` 本身纳入版本库（并入 CS-CI-REL-01）。
