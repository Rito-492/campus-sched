# 课程导出目录清单（Export Manifest）

> 项目：campus-sched　编制：杨瀚霖（55231031，配置经理）　日期：2026-11-14
> 本文是任务 6「配置库」最终课程提交包（.zip，项目经理打包提交学习通）的**可复现导出清单**：把 Git 版本库中的配置项，按编号/版本/路径映射为课程导出目录中的文件名，并给出可复现的生成命令。编号与版本以《配置项索引》（`docs/configuration/configuration-index.md`）为唯一登记处。

## 1. 导出目录命名规则

课程导出目录中每个文件命名：`<编号>_v<版本>_<中文名>.<扩展名>`；无独立版本号的单件（如源代码、契约、环境配置）以 Git 提交与基线 tag 追踪，不臆造版本号，导出名省略 `_v<版本>` 段或标注 `—`。

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
| `CS-CI-REL-01_v1.0.0_发布说明.md` / `…_发布清单.json` | `release/README.md`、`release/manifest.json` | v1.0.0 | 配置经理 |
| `CS-CI-DOC-30_v0.9_初始项目计划.md` | `docs/management/initial-project-plan.md` | v0.9 | 项目经理 |
| `CS-CI-DOC-31_v3.1.3_详细项目计划.md` | `docs/management/detailed-project-plan.md` | v3.1.3 | 项目经理 |
| `CS-CI-DOC-35_软件开发计划.docx` | `docs/management/T08_软件开发计划.docx` | — | 项目经理 |
| `CS-CI-DOC-40_v1.1_软件需求规约.md` 等需求族 | `docs/requirements/*.md` | v1.1 | 系统分析员 |
| `CS-CI-DOC-45_软件需求规约.docx` | `docs/requirements/T08_软件需求规约.docx` | — | 系统分析员 |
| `CS-CI-DOC-50_v0.2_系统设计.md` / 设计族 | `docs/design/*.md` | v0.2 | 设计师 |
| `CS-CI-DOC-53_软件设计.docx/.pdf` | `docs/design/software-design-task4.{md,docx,pdf}` | — | 设计师 |
| `CS-CI-MODEL-01_v1.0_首次资源确认活动图.drawio/.png` | `models/activity/首次资源确认活动图.*` | v1.0 | 设计师 |
| `CS-CI-MODEL-02_v1.0_首次活动申请与发布用例图.drawio/.png` | `models/use-case/首次活动申请与发布用例图.*` | v1.0 | 设计师 |
| `CS-CI-DOC-60_v0.2_用户手册.md` | `docs/ui/user-manual.md` | v0.2 | 界面设计师 |
| `CS-CI-DOC-64_v0.2_用户手册.pdf` | `docs/ui/T08_用户手册.pdf` | v0.2 | 界面设计师 |
| `CS-CI-DOC-61_v1.1_界面说明.md` | `docs/ui/interface-description.md` | v1.1 | 界面设计师 |
| `CS-CI-DOC-63_用户界面说明文档.docx` | `docs/ui/T08_用户界面说明文档.docx` | — | 界面设计师 |
| `CS-CI-DOC-20/21/22_v0.2_测试计划/设计/数据设计.md` | `docs/testing/*.md` | v0.2 | 测试经理 |
| `CS-CI-DOC-28_v1.0_软件测试报告.docx` | `docs/testing/T08_软件测试报告.docx` | v1.0 | 测试经理 |
| `CS-CI-DOC-29_v1.1_软件测试报告.md` | `docs/testing/test-report.md` | v1.1 | 测试经理 |
| `CS-CI-DOC-25_v0.4_缺陷登记表.md` | `docs/testing/defect-register.md` | v0.4 | 测试经理 |
| `CS-CI-DOC-24_v3.0_需求覆盖矩阵.md` | `docs/testing/coverage-matrix.md` | v3.0 | 测试经理 |
| 源代码 / 契约 / 环境配置（`CS-CI-SRC-*` / `CS-CI-DOC-70` / `CS-CI-ENV-*`） | `server/`、`web/`、`qa/`、`contracts/`、`infra/`、`scripts/`、`compose*.yaml` | 以 Git/基线 tag 追踪 | 各模块负责人 |

## 3. 可复现生成（不靠手工打包）

**整库快照（当前 main 基线）**：以下命令产出与提交一致的目录树，导出目录据此重命名即可复现：

```powershell
git archive --format=zip -o campus-sched-source.zip <release-tag>     # 全量源码+文档
git archive --format=zip -o campus-sched-config.zip <release-tag> docs/configuration/ release/ models/
```

**历史版本副本（同目录）**：任何一份已入库文件的历史版本均可从 Git 逐件还原，不需要在提交包里另存：

```powershell
# 例：配置管理计划 v1.6（上一版）与 v1.7（当前）并存
git show <v1.6对应commit>:docs/configuration/configuration-management-plan.md > CS-CI-DOC-04_v1.6_配置管理计划.md
git show <v1.7对应commit>:docs/configuration/configuration-management-plan.md > CS-CI-DOC-04_v1.7_配置管理计划.md
```

各文档版本↔Git commit 的对应关系见各文档头部「修订历史」表（如配置管理计划 v1.6 = `2981c37`、v1.7 = 本轮提交）；二进制（docx/pdf/drawio）同理用 `git show <commit>:<path>` 还原。

## 4. 与发布清单/索引的关系

- 编号、版本、路径、负责人以 `docs/configuration/configuration-index.md` 为唯一登记处；本清单是它在「课程导出目录」上的落点映射，不作第二事实来源。
- 发布内容与校验值见 `release/manifest.json`；干净启动步骤见 `release/README.md`。
- 历史版本**不**在版本库中重复存副本（避免与 Git 历史双写不一致）；导出目录中的历史版本副本一律由 §3 命令从 Git 还原生成。
