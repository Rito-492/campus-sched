# 交接单（配置经理）

## 本轮工作（2026-11-14）：发布基线建立（打 tag + 登记「已建立」）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」最终发布基线建立。
- 前置（真实核实，不臆造）：PR #29（release/ 可执行导出脚本与清单）已合入 main；PR #23（任务 5「软件测试设计」三件正式交付物）已合入 main（`567c1fb`）；CCB 冀项阳（项目经理）2026-11-14 口头授权打 tag（经配置经理转达）。
- 已执行（真实执行）：
  1. **打 tag**：`git tag -a baseline/release-v1.0.0 567c1fb` 并推送 GitHub（`git rev-parse baseline/release-v1.0.0^{commit}` = `567c1fb3c70d286b8dac230a723df0664705cc40`，main 顶点，含 PR #29 + PR #23）。
  2. **登记「已建立」**：`configuration-index.md` §3 release 行「待授权/待建立」→「已建立（tag 存在）」；《配置管理计划》§5.2 release 行同步（文档 v1.7→v1.8）；`manifest.json` `baselineStatus`→已建立、`commit`→tag 解析 SHA `567c1fb`、`commitNote` 更新；`release/README.md` 发布提交→`567c1fb`；`export-manifest.md` §5 发布提交口径→已建立。
  3. **历史版本补 v1.7**：配置管理计划 Markdown v1.7（`7726193`）因 v1.8 成为当前版而补入 `HIST`（34→35 件），`export-manifest.md` §4.1 表同步。
  4. **任务 5 三件状态更正**：`configuration-index.md` §2.7 与 `export-manifest.md` §4.2 由「待 PR #23 合入」更正为「已合入（PR #23，`567c1fb`）」，导出核验退出码 2→0。
- 实际运行结果（真实执行）：`python release/export.py --commit baseline/release-v1.0.0` 实跑，任务 5 三件齐备，退出码 0，字节保真逐件通过，六项散列匹配；报告见 `export-report.json`（附于本轮）。
- 未决 / 交接：
  - 任务 5「软件测试设计」三件（`test-design-v1.0.md`、`T08_任务5_软件测试设计.docx`、`test-design-cases-v1.0.json`）的**版本/编号口径**由测试经理在其任务 5 文档中登记；配置经理未代改内容、未代分 CI 编号。
  - 《配置管理计划》Word（CS-CI-DOC-05，课程提交版）仍为 v1.7，.md 已至 v1.8；课程提交 .zip 打包时如需 Word 对齐 v1.8，由配置经理重导（另行处理）。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（按标签重跑完整导出留存报告并组织打包提交）；测试经理（确认任务 5 三件正式交付物版本/编号口径）。

## 本轮工作（2026-11-14 六轮复审整改）：更正发布清单测试归属

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」第六轮复审「必须改」1 项整改（另两条件为外部依赖，见下）。
- main 基准：`origin/main` 已至 `cbf4945`；本次改动在 `work/55231031-change`（与 `release/` 同批提交，SHA 见 `git log`）。
- 整改内容（真实核实，不臆造）：
  1. **更正测试归属**：`release/manifest.json` `commitNote` 此前把 `cbf4945` 误作「QA 2026-11-13 全量 verify 基准」，但 QA 全量 verify 实为更早的 main `e1051df`（Merge PR #21，含 CM #21），`cbf4945` 是其后合入 PR #27（用户手册 PDF）的主线提交。现更正：`commit`（`cbf4945`）标注为**本分支 `work/55231031-change` 的主线起点**（含 PR #27 用户手册合入，不含本 PR 发布文件）；QA 全量 verify 结果与证据单独归 `e1051df`（manifest `tests.evidence` / `tests.note` 已指向 `e1051df`，保持不变）。同步更正 `export-manifest.md` §5、`configuration-index.md` §3/header/修订历史、`export.py` 报告标签（「本分支主线起点（非导出候选）」）。
- 实际运行结果（真实执行）：
  - 独立导出 `ac675b6` 94 件（当前 60 + 历史 34），六项已声明散列均匹配；本轮仅改清单/文档口径，不改工件映射，导出行为不变。
  - 任务 5 正式交付物仍**缺失 3 件**（PR #23 未合入），脚本退出码 2。
- 未完成项 / 外部依赖（不越权）：
  - 任务 5「软件测试设计」三件（`test-design-v1.0.md`、`T08_任务5_软件测试设计.docx`、`test-design-cases-v1.0.json`）仍在 PR #23（`work/55230316-qa`，`26c5dad`）**未合入 main**；合入后需实跑一次完整导出（应退出码 0）再归档。
  - `baseline/release-v1.0.0` 打 tag 仍待 CCB 冀项阳授权；待工件纳入且授权打标后，按标签重跑完整导出并留存报告，届时登记实际发布标签及其 SHA。本轮未运行部署。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（推动 PR #23 合入后实跑完整导出并归档、授权 release 基线并打 tag）；测试经理（确认 PR #23 合入后三件正式交付物路径）。

## 本轮工作（2026-11-14 五轮复审整改）：历史版本完整盘点 + 发布提交口径收口

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」第五轮复审「必须改」两项整改（另任务 5 缺件为外部依赖）。
- main 基准：`origin/main` 已至 `cbf4945`；本次改动在 `work/55231031-change`（与 `release/export.py` 同批提交，SHA 见 `git log`）。
- 整改内容（真实执行，`git log --follow` + 版本头逐 commit 核实，不臆造）：
  1. **历史版本完整盘点补漏**：`HIST` 由 17 件扩至 **34 件**，补上此前遗漏的已入库正式文档历史版本——用户手册 Markdown v0.1（`d94dbaf`）、初始项目计划 v0.5/v0.6/v0.7/v0.8（`364f07b`/`a5e0724`/`1fc3dd9`/`2055c63`）、测试设计 v0.1（`4f5a7a1`）、测试数据设计 v0.1（`1cb2e86`）、缺陷登记表 v0.1/v0.2/v0.3（`56f9149`/`a48367e`/`19ae94b`）、软件测试报告 v0.1/v0.4/v0.6/v0.7/v0.8/v0.9/v1.0（`a48367e`/`b97cf70`/`db038b1`/`291b31d`/`e1e175c`/`19ae94b`/`8f30a49`）。每件 `git cat-file -e` 与版本头逐件核对通过；`export-manifest.md` §4.1 列明工件/版本/来源提交/同目录输出，并声明为完整盘点。
  2. **发布提交口径收口**：`release/manifest.json` 增 `commitNote`，把 `commit`（`cbf4945`）明确标注为**本分支主线起点**（main 合入 PR #27 用户手册后的提交，不含本 PR 发布文件），QA 全量 verify 结果单独归 `e1051df`；最终归档课程包/工件 SHA 以 `baseline/release-v1.0.0` 打 tag 后 `git rev-parse` 解析为准。`export.py` 报告将「清单声明 commit」与「本次导出候选 SHA」分别标注，不一致时提示「本分支主线起点（非导出候选）」；`export-manifest.md` §5 与 `configuration-index.md` §3 同步口径。
- 实际运行结果（真实执行，非臆造）：
  - 历史版本 34 件 `git cat-file -e` 全部命中；导出文档 94 件（当前 60 + 历史 34），字节保真逐件通过=True，发布清单 6 件散列全部匹配。
  - 任务 5 正式交付物仍**缺失 3 件**（PR #23 未合入），脚本正确退出码 2。
- 未完成项 / 外部依赖（不越权）：
  - 任务 5「软件测试设计」三件（`test-design-v1.0.md`、`T08_任务5_软件测试设计.docx`、`test-design-cases-v1.0.json`）仍在 PR #23（`work/55230316-qa`，`26c5dad`）**未合入 main**；合入后需实跑一次完整导出（应退出码 0）再归档。属测试经理/项目经理交付依赖，配置经理不代产、不代合。
  - `baseline/release-v1.0.0` 打 tag 仍待 CCB 授权；发布标签与实际部署本轮未验证。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（推动 PR #23 合入后实跑完整导出并归档、授权 release 基线并打 tag、组织打包提交）；测试经理（确认 PR #23 合入后三件正式交付物路径）。

## 本轮工作（2026-11-14 四轮复审整改）：历史版本补齐 + 发布清单自身校验

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」第四轮复审「仍需修改」两项整改（另任务 5 缺件为外部依赖，见下）。
- main 基准：`origin/main` 已至 `cbf4945`；本次改动在 `work/55231031-change`（与 `release/export.py` 同批提交，SHA 见 `git log`）。
- 整改内容（真实执行，`git` 逐件核实，不臆造）：
  1. **补齐课程导出的历史版本**：`HIST` 由 2 件扩至 **17 件**，版本↔commit 以各文档头部「修订历史」与 `git show` 实测版本头为准——配置管理计划 Markdown v1.0/v1.1/v1.4/v1.5/v1.6（`bfa2cd6`/`5d07897`/`298780f`/`1382623`/`2981c37`）、配置管理计划 Word v1.0/v1.4/v1.5/v1.6（同 commit，Word 自 v1.0 起入库）、总开发任务书 v2.0（`9a80fa3`）、详细项目计划 v3.0/v3.1/v3.1.1/v3.1.2/v3.1.3（`b30c0f2`/`a5e0724`/`1fc3dd9`/`2055c63`/`4f0f304`）、测试计划 v0.1（`b5b1db3`）、系统设计 v0.1（`f6bd329`）。未独立入库工作稿按实说明：配置管理计划 v1.2 未独立成 commit（内容并入 v1.4）、v1.3 未形成独立版本，不臆造 commit。`export-manifest.md` §4.1 列明工件/版本/来源提交/同目录输出文件名。
  2. **校验发布清单本身**：删除写死 openapi/demo 两值，改为读取**同一候选 SHA** 的 `release/manifest.json`，逐件按清单 `file` 字段（server/pom.xml、web/package.json、openapi、V1/V2 迁移、demo.json）核对散列；文件缺失或哈希不匹配返回**退出码 3**。`manifest.json` 为散列条目补 `file` 字段。
  3. **同步修正**：详细项目计划当前版本 `v3.1.3`→`v3.1.4`（`git` 实测 main/HEAD 头均为 v3.1.4；同步 `configuration-index.md`、`release/manifest.json`、`release/export-manifest.md`、`release/export.py`）。
- 实际运行结果（真实执行，非臆造）：
  - 历史版本 17 件 `git cat-file -e` 全部命中；`--out` 指向新建唯一目录导出：文档 77 件（当前 60 + 历史 17），字节保真逐件通过=True，发布清单 6 件散列全部匹配（server/web/openapi/V1/V2/demo）。
  - 任务 5 正式交付物仍**缺失 3 件**（PR #23 未合入），故退出码 2（齐备前不能作为最终课程包）。
- 未完成项 / 外部依赖（不越权）：
  - 任务 5「软件测试设计」三件（`test-design-v1.0.md`、`T08_任务5_软件测试设计.docx`、`test-design-cases-v1.0.json`）已由测试经理在 PR #23（`work/55230316-qa`，`26c5dad`）产出，**尚未合入 main**；合入后需实跑一次完整导出（退出码 0）再归档。属测试经理/项目经理交付依赖，配置经理不代产、不代合。
  - `baseline/release-v1.0.0` 打 tag 仍需项目经理 CCB 授权；发布标签与实际部署本轮未验证。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（推动 PR #23 合入后实跑完整导出并归档、授权 release 基线、组织打包提交）；测试经理（确认 PR #23 合入后三件正式交付物路径）。

## 本轮工作（2026-11-14 三轮复审整改）：导出脚本三项 P1 收口

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」第三轮复审「必须改」三项 P1 整改。
- main 基准：`origin/main` 已至 `cbf4945`；本次改动在 `work/55231031-change`（与 `release/export.py` 同批提交，SHA 见 `git log`）。
- 整改内容（真实执行，`git` 核实，不臆造）：
  1. **`--out` 无条件递归删除（P1）**：原脚本对已存在的输出目录先 `rmtree` 再校验。改为：`--out` 指向已存在的非空目录或文件时直接拒绝退出（不递归删除），仅清空为空目录时继续；请另选唯一输出目录。
  2. **`--commit` 不能锁定全部交付内容（P1）**：原 `build_docs()` 硬编码读取 `HEAD`，而归档/哈希用指定提交，可混入不同提交。新增 `resolve_commit()`，把 `--commit`（默认 `HEAD`）经 `git rev-parse <ref>^{commit}` 解析为**唯一提交 SHA**，归档、全部当前工件读取与报告记录统一使用该 SHA。
  3. **任务 5 齐备检查假通过、历史覆盖不足（P1）**：原检查只核验既有配置项编号且缺项仅打印。改为按**精确文件路径**（`TASK5_FORMAL`，含 PR #23 的 `docs/testing/test-design-v1.0.md`、`docs/testing/T08_任务5_软件测试设计.docx`、`test-data/test-design-cases-v1.0.json`）逐件 `git cat-file -e` 核验，缺项打印明细并返回**退出码 2**。历史版本由仅配置管理计划 Markdown v1.6 一件，补为 Markdown + **Word v1.6 两件**（均 `2981c37`），与当前版同目录并存。
  4. 同步：`release/export-manifest.md` §3/§4（提交锁定、输出目录安全、任务 5 精确文件核验表、历史版本表）；`configuration-index.md` 头部/修订历史/§2.7（任务 5 软件测试设计三件待 PR #23 合入说明）。
- 实际运行结果（真实执行，非臆造）：
  - `--out` 指向非空目录 → 拒绝，退出码 1。
  - `--out` 指向新建唯一目录 → 产出 `commit=9b1886838601cd8f2e06dfd675e3f5b9f97bd680`，文档 62 件（含历史版本 2 件），字节保真逐件通过=True，任务 5 正式交付物**缺失 3 件**（`test-design-v1.0.md`、`T08_任务5_软件测试设计.docx`、`test-design-cases-v1.0.json`），退出码 2。三项 P1 均按预期生效。
- 未完成项 / 外部依赖（不越权）：
  - 任务 5「软件测试设计」三件正式交付物已由测试经理在 PR #23（`work/55230316-qa`，提交 `26c5dad`）产出，**尚未合入 main**，故当前导出核验因缺项返回退出码 2；属测试经理/项目经理交付依赖，配置经理不代产、不代合。
  - `baseline/release-v1.0.0` 打 tag 仍需项目经理 CCB 授权（PR #29 合入 main 后）。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（推动 PR #23 合入使任务 5 齐备、授权 release 基线、组织打包提交）；测试经理（确认 PR #23 合入后三件正式交付物路径）。

## 本轮工作（2026-11-14 二轮评审整改）：release 授权统一待授权 + 导出可执行化并实际核验

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」第二轮评审「仍阻塞」四项整改。
- main 基准：`origin/main` 已至 `cbf4945`；本次改动在 `work/55231031-change`（c02148b）。
- 整改内容（真实执行，`git` 核实，不臆造）：
  1. **发布基线授权统一（阻塞 #3）**：远程尚无 `baseline/release-v1.0.0` tag，索引此前写「PM 已授权」与 manifest「待授权」互相矛盾。统一为「待授权（项目经理（CCB）· 冀项阳，PR #29 合入 main 后授权）/ 待建立」，候选提交 = release/ 内容随 PR #29 合入 main 后的 merge commit（合入前评审/导出候选为 `work/55231031-change` 最新提交）。索引 §3、计划 §5.2、`manifest.json`、T08 Word 两处基线表同步。
  2. **T08 Word 基线表排序（阻塞 #3）**：按 tag commit 时间升序，design-v1.0（11-14）移至 task-package-v3.0（10-14）之后；release 行授权列由「项目经理（CCB）· 冀项阳」改为「待授权（…）」。两处基线表均同步（docx 经 python 改 word/document.xml 后重打包，字节核对通过）。
  3. **导出可执行化（阻塞 #1/#2/#4）**：`release/export-manifest.md` 重写为可执行清单，新增 `release/export.py`（并入 CS-CI-REL-01）并落地运行。脚本三步：整库 `git archive` 快照 + 文档逐件 `git show <commit>:<path>` 字节保真重命名为「编号_版本_名称」+ 历史版本（配置管理计划 v1.6=`2981c37`）与当前版同目录并存；二进制（docx/pdf/drawio/png）不经文本转码。修正旧清单「不需要在提交包里另存」与课程要求的矛盾。
  4. **用户手册 PDF 更正（阻塞 #2）**：导出源由过时的 `docs/ui/T08_用户手册.pdf` 改为 PR #27 成品 `docs/ui/user-manual.pdf`；索引 §2.6 登记同步（待界面设计师重导 T08 课程版）。
- 实际运行结果（真实执行，非臆造）：
  - `python release/export.py --commit HEAD` 产出 `D:\TEMP\campus-sched-export`：文档 61 件（含历史版本 1 件）+ 整库快照 + `export-report.json`。
  - 字节保真逐件核对通过（sha256 与 `git show` 原始字节一致）；任务 5 正式交付物（测试计划/测试设计·结构化用例/测试数据设计/覆盖矩阵/缺陷登记/测试报告 Word+md）8 项齐全；`openapi.yaml`、`demo.json` 哈希与 `manifest.json` 交叉核对匹配。
- 未完成项 / 外部依赖（不越权）：
  - `baseline/release-v1.0.0` 打 tag 仍需项目经理 CCB 授权（PR #29 合入 main 后）。
  - 用户手册 `T08_用户手册.pdf` 重导（界面设计师）。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（授权 release 基线、核对导出目录、组织打包提交）。

## 本轮工作（2026-11-14 修缮）：评审整改收口（design 重打 + 导出清单 + PDF 差异标注）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」评审结论「必须改」五项修缮。
- main 基准：`origin/main` 已至 `cbf4945`；本次改动在 `work/55231031-change`（9006162）。
- 本轮交付（真实执行，`git` 核实，不臆造）：
  1. **design 基线重打**：`baseline/design-v1.0` 原指向 `bbeb984`（10-13，models/ 仅 .gitkeep 占位）。经核实图源于 11-14（`328025d`，PR #31 合入）补齐，故删除旧 tag 并重打指向 `328025d`（含两份 drawio + png），冻结时间按 tag commit 由 10-13 更正为 11-14；计划 §5.2、索引 §3、T08 Word（两处基线表 + 修订历史）同步。
  2. **导出清单**：新增 `release/export-manifest.md`（并入 CS-CI-REL-01），给出课程导出目录的编号/版本/路径命名规则与 `git archive`/`git show` 可复现命令；历史版本副本不双写、由 Git 逐件还原。
  3. **时间字段统一**：T08 Word 基线表列头「建立时间」→「冻结时间（tag commit）」，说明段口径同步（`建立时间` 0 残留）。
  4. **配置管理计划 v1.6→v1.7**：.md 与 Word 头部、修订历史、索引 CS-CI-DOC-04/05、manifest 同步。
  5. **用户手册 PDF 差异标注（#6，待界面设计师）**：`docs/ui/` 下 `T08_用户手册.pdf`（16:17，缺「报名参加」）与 `user-manual.pdf`（18:40，#27 成品）两份 v0.2 内容不一致，已在索引 §2.6 标注，需界面设计师按 #27 重导或分别标注来源；配置经理不代改其内容。
- 未完成项 / 外部依赖（不越权）：
  - 用户手册 PDF 统一（界面设计师）。
  - 任务 6 打包（.zip，含历史版本副本）与学习通上传由项目经理执行。
  - `baseline/release-v1.0.0` 打 tag 仍需项目经理 CCB 授权。
- 下一接收人：项目经理（核对导出清单、授权 release 基线、组织打包提交）；界面设计师（统一用户手册 PDF）。

## 本轮工作（2026-11-14 续）：配置库 checklist 复核 + 时间口径收口

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6「配置库」交付复核（对照 PPT checklist：含全部文档代码 / 遵循配置计划标识方法 / 基线时间顺序合理 / 基线标识与修订历史一致）。
- main 基准：`origin/main` 已至 `cbf4945`（PR #27 UI 合入；PR #31 DS 图源、PR #26 配置整改均已在 main 内）。
- 复核结论与收口（真实执行，`git log`/`git tag -l` 核实，不臆造）：
  1. **基线时间顺序**：init-v1.0（09-25，commit=tag）、task-package-v2.0（09-26，commit=tag）、requirements/design（内容 10-13 冻结 7fb247b / bbeb984，tag 对象 2026-11-14 17:19 补打）、task-package-v3.0（commit 10-14 15:44 / tag 15:54）、release-v1.0.0（11-14 待建立）。requirements/design 属「内容先冻结、基线后补打」，已按 tag commit 时间排序并在计划 §5.2、索引 §3、T08 Word 三口径说明中显式标注，不与 tag 创建时间/授权时间混同。
  2. **修订历史一致性收口**：`configuration-management-plan.md` v1.6「入库」由「—（待提交）」更正为「2026-11-14（2981c37）」；`T08_配置管理计划.docx` 同步四处（v1.6 入库列、v1.6 修订说明、时间口径说明、§3.1 MODEL 示例由「类别已定义、待产出」改为「对应首次资源确认活动图」）。
  3. **发布提交收口**：`release/manifest.json` 与 `release/README.md` 的发布提交由 `750763b` 统一更正为 `cbf4945`（当前 main；release 仍「待建立」，tag 时以 tag commit 为准）。
- 未完成项 / 外部依赖（不越权）：
  - `baseline/release-v1.0.0` 打 tag 仍需项目经理 CCB 授权，不自行建立。
  - 任务 6 打包（.zip，文件目录形式）与学习通上传由项目经理执行。
  - 他人交付物待收口：软件测试报告课程提交版 docx v1.0 与仓库 `test-report.md` v1.1 的版本对齐（测试经理）。已收口：用户手册 `.md` 已 v0.2、与 PDF v0.2 一致（界面设计师已更新，`user-manual.md` 末表「当前手册版本 v0.2」）；项目考核评估表已由项目经理产出（课程提交物，直接随 .zip 提交学习通）。
- 下一接收人：项目经理（核对发布提交、授权 release 基线、组织打包提交）。

## 本轮工作（2026-11-14）：发布说明 / 发布清单 / compose 演示入口对齐（发布交付）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6 发布交付（发布说明、发布清单、恢复演练步骤、D-AT-24 启动对齐），对应 PM 集成评审 2026-11-13 记录 line 39「D-AT-24、发布说明、发布清单和恢复演练仍由配置经理准备、QA 复验、PM 核对实际发布提交」。
- main 基准：`git fetch` 后 `origin/main` 已至 `750763b`（PR #26 配置文档修订历史评审整改合入）。
- 本轮交付：
  1. **发布说明** `release/README.md`（v1.0.0）：前置条件（本机仅需 Docker）、干净环境启动步骤（`Copy-Item .env.example .env` → `docker compose up -d --build`，Flyway 自动 V1→V2，无手改库步骤）、端口表（演示入口 8088 / API 8080 / PG 5432）、9 个演示账号、备份/迁移/恢复演练步骤（pg_dump/pg_restore）、版本一致性、D-AT-24 验收关联。
  2. **发布清单** `release/manifest.json`：releaseVersion v1.0.0、baseline 待建立、发布提交 750763b、环境（Java 21 / Spring Boot 3.5.16 / PG 17）、组件 SHA-256（server pom、web package.json、openapi、V1/V2 迁移、demo.json）、docs 版本、tests（101 单元 + 46 集成，DS 2026-11-13 结果）。
  3. **compose 演示入口对齐** `compose.yaml`：web（Nginx）宿主端口由 `5173` 改为 `8088`，与文档口径（公共契约 04、计划 §7.1、NFR-27、索引 CS-CI-ENV-01）一致；前端 Vite 开发服务器 5173 不在本编排内。
  4. **索引登记** `configuration-index.md`：CS-CI-REL-01 由「发布包与发布说明 · 待建立（当前为空）」改为「发布说明与发布清单 · v1.0.0 · 受控」，并补修订历史行。
- 实际检查（真实执行）：`git diff --check` 通过；`compose.yaml` 端口与 `release/README.md` §3、计划 §7.1 逐项核对一致；SHA-256 为真实计算（`sha256sum`）而非臆造。发布交付为 Markdown/JSON/YAML，未运行产品测试。
- 未完成项 / 外部依赖（本 agent 不越权）：
  - `baseline/release-v1.0.0` 打 tag 仍需项目经理 CCB 授权，不自行建立（索引 §3、manifest 均标「待建立」）。
  - 恢复演练的**实际执行与证据**待 QA 复验时在真实 PostgreSQL 上跑通后补记（发布说明 §5 已固化可复现步骤，暂未执行）。
  - 任务 6 打包（.zip）与学习通上传由项目经理执行。
- 下一接收人：项目经理（核对实际发布提交、授权 release 基线、组织打包提交）；测试经理（复验发布说明干净启动 D-AT-24 与恢复演练）。

## 本轮工作（2026-11-14 续）：登记测试报告与用户手册 PDF（补齐任务 6 缺口）

- 收到并核实两件他人交付物，按配置库目录结构放入并登记（CM 只登记/放置，不改内容）：
  1. **软件测试报告**（测试经理·汤雨润 55230316，v1.0，2026-11-13）→ `docs/testing/T08_软件测试报告.docx`，登记 CS-CI-DOC-28。内容真实：30 项验收场景通过 28、部分 1、未执行 1（D-AT-24，本机缺 Compose 插件）；缺陷 11 条全部关闭；含缺陷记录 + 测试评估摘要 + 修订历史表。
  2. **用户手册 PDF**（界面设计师·杨畅 17231018，v0.2，2026-11-14，18 页）→ `docs/ui/T08_用户手册.pdf`，登记 CS-CI-DOC-64。
- 已从索引 §2.12「待补」移除软件测试报告（已产出）。
- **待收口（如实标注，不代改他人文档）**：仓库内 `docs/ui/user-manual.md` 仍为 v0.1（标注「前端 v0.1，非最终发布手册」，2026-11-01），与课程提交版 PDF v0.2 版本不一致——需界面设计师确认二者关系或更新 .md 版本头；配置项索引已按「.md=v0.1、PDF=v0.2」分别如实登记。
- 剩余缺口（他人）：设计图源 `models/*.drawio`（设计师）；项目考核评估表（项目经理）；PR #29 合并 + `baseline/release-v1.0.0` 授权打 tag（项目经理）。

## 任务 6「配置库」交付（2026-11-13）

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；任务 6 配置库交付（截至 2026-11-14）。
- main 基准：`git fetch` 后 `origin/main` 已至 `e1051df`（PR #21 改期修复已合并）；本分支 `work/55231031-change` 在其上领先 1 个提交（`351b727` 配置文档补修订历史表，待开新 PR 合并）。
- 本轮交付（配置库 = Git 仓库，文件目录形式，含全部文档与代码，本轮不改目录结构）：
  1. **补全配置项索引**（`docs/configuration/configuration-index.md`）：由原先只登记 CM 环境项 + QA 测试项，补全为六人模块全覆盖——管理/需求/设计/界面/测试各岗位文档（含课程提交版 Word）、后端源码（PM 基础工程、AN 活动报名、DS 资源调度、CM 改期通知审计）、前端 `web/`、OpenAPI 契约、验收证据，并明确负责人与状态。
  2. **基线信息**：三条历史基线（init / task-package-v2.0 / v3.0）保持"待核实（无追溯 CCB 记录）"，不补造、不倒造审批；`baseline/release-v1.0.0` 标记"待建立（待 PM CCB 授权后打 tag）"，对应任务 6 最终交付。
  3. **未产生内容不预登记**：软件测试报告（QA 待产出）、设计图源（`models/` 仅骨架）、发布包（`release/` 为空）、项目考核评估表（PM 课程提交物）均不预登记，仅留待补说明。
- 实际检查（真实执行）：
  - `git tag -l`：init-v1.0 / task-package-v2.0 / task-package-v3.0 三条 tag 存在；release-v1.0.0 未打（待 PM 授权）。
  - `git diff --check`：通过。
  - 文档版本号逐项核对：需求族 v1.1、系统设计 v0.2、用户手册 v0.1、测试计划/设计/数据 v0.2、初始计划 v0.9、详细计划 v3.1.3 等，已写入索引"版本"列（以各文档头部标注为准）。
- 未完成项/外部依赖（本 agent 不越权）：
  - 任务 6 打包（.zip）与学习通上传由项目经理执行，本 agent 不执行外部提交。
  - `baseline/release-v1.0.0` 打 tag 需项目经理 CCB 授权，不自行建立。
  - 软件测试报告（测试经理）、用户手册终版（界面设计师）、项目考核评估表（项目经理）为他人交付物，不在配置经理范围。
  - 各文档内部"修订历史记录表"整理：本人已完成 `docs/configuration/` 下三份文档（`configuration-management-plan.md` v1.0–v1.5、`configuration-index.md`、`repo-structure.md`）的修订历史表，版本号/日期均按 `git log` 与交接单核实；其余岗位文档由其负责人整理（红线：不改他人已评审文档）。
- 下一接收人：项目经理（组织任务 6 打包提交、授权 release 基线）；测试经理/界面设计师（补齐各自任务 6 交付物后告知 CM 登记）。

## 评审整改（2026-11-10）：API-32 鉴权先于版本冲突 + 存量自确认处置

独立交叉评审对 `23825cc` 提出「必须改」两项阻塞，本轮逐项整改（未提交，待授权）：

1. **API-32 资源复核在鉴权前返回活动版本/状态**：原 `resourceDecision` 在 `AuthorizationPolicy.confirmation` 之前检查 `baseVersionNo` 并抛 `STALE_VERSION`（details 含 versionNo/lockVersion/status），无复核权限的账号在版本冲突时收到 409 而非 403，泄露无权查看的版本/状态。整改：把 `STALE_VERSION` 检查移到 `lockUsers` + `AuthorizationPolicy.confirmation`（最新权限、完整资源范围、本人回避）之后，与 02 §3「不泄露无权查看的对象」及 §6 原子切换锁序一致。新增 `ChangeServiceTest.resourceDecisionChecksAuthorizationBeforeVersionConflict`：管理员但无 RESOURCE_CONFIRM 权限 + 版本过期 → 403，且不进入调度锁检查。

2. **存量 AWAITING_CONFIRMATIONS 自确认处置（明确 + 验证）**：`impact.requiredStaffUserIds` 过滤所有者只影响新快照；升级前旧快照仍含发起人本人，`confirm` 按旧快照放行发起人自确认。
   - 明确：本人不得向自己确认（01 §3「人员确认不可代签」），`confirm` 授权时追加所有者排除；影响快照不可改，存量申请**走现有撤回流程（API-33）重建**，重建时按新规则——若发起人为唯一工作人员则直接进入审批（PENDING_APPROVER）——不就地改写旧快照。
   - 验证：新增 `ChangeServiceTest.ownerCannotSelfConfirmLegacySnapshot`：旧快照 requiredStaffUserIds 仅含发起人 → 发起人 confirm 403，且未调用 decideConfirmation。

- 回归（真实执行，`D:\Java\jdk-23` + `--release 21`，单元测试无需数据库）：`ChangeServiceTest` 由 4 用例扩至 6 用例；全量单元测试（Surefire `*Test`）**82/82 通过**（0 失败 0 错误）；`git diff --check` 通过。契约检查与 PostgreSQL 集成验收本轮未执行（纯服务层逻辑修复，未触及 contract/openapi 与数据库迁移/表结构）。
- 下一接收人：项目经理复审整改；测试经理复审回归用例。

## 本轮工作（2026-11-07）：修复改期自确认与版本冲突提示两项验收阻塞

- 成员、学号、任务ID：杨瀚霖、55231031、配置经理；CM 改期模块（API-26～33）缺陷修复。
- main 基准：本轮 `git fetch` 后 `origin/main` 推进至 `16de4a5`（PR #19 foundation 合并）；本分支 `work/55231031-change` 由 `b19ae8b`（已并入 main 的 PR #18 头）快进合并到 `16de4a5`，无冲突、未 reset。
- 本轮修复（对应全量验收剩余三项中的两项 CM 项；活动审批项属 AN 冯玉琪，不在本 agent 范围）：
  1. **改期确认（发起人是唯一工作人员时仍要求其确认自己）**：`ChangeService.impact` 计算 `requiredStaffUserIds` 时过滤掉所有者本人。所有者发起改期不再被列入待确认工作人员，若其为唯一工作人员则 `requiredStaffUserIds` 为空，`create` 直接置 `PENDING_APPROVER`（跳过人员确认阶段），不再要求发起人向自己确认。有其他工作人员时仍仅由他们确认，发起人无法代签自己。依据 01 §3「无其他工作人员直接复审」与「人员确认不可代签」。
  2. **版本冲突提示（请求版本过期时未返回当前版本/状态）**：新增 `ChangeService.staleVersion` 助手，`preview`/`create`/`resourceDecision` 四处 `STALE_VERSION` 409 统一在 `details` 返回 `{versionNo, lockVersion, status}`（当前版本/锁版本/活动状态），供前端提示。依据 02 §3「其余 409 的 details 至少含当前 versionNo/lockVersion 或当前 status」。
- 回归测试（真实执行，jdk-23 `--release 21`，单元测试无需数据库）：`ChangeServiceTest` 由 1 用例扩至 4 用例，新增——所有者唯一工作人员→PENDING_APPROVER 且 `requiredStaffUserIds` 为空、`createConfirmations` 空表；所有者+其他工作人员→仍 AWAITING_CONFIRMATIONS 且仅剩该工作人员；预览版本过期→409 STALE_VERSION 且 details 含 versionNo=2/lockVersion=4/status=SCHEDULED。全量单元测试（Surefire `*Test`）**80/80 通过**（含新增 3 用例，0 失败 0 错误）。
- 未完成项/外部依赖：改期回滚/通知一致性等验收 IT 仍依赖 QA 测试支撑修复（`DbTestSupport` 装载顺序、`NotificationConsistencyIT` 生产 Probe 覆盖、`ActivityRegistrationIT` 双 bean 冲突），与本轮两项逻辑修复无关，未复跑；活动审批修复（不填批准意见审批失败）由 AN 冯玉琪负责。
- 下一接收人：项目经理评审本分支改动；测试经理复审回归用例；AN 完成活动审批项后进入全量验收复跑。

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
