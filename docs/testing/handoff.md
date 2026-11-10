# 测试岗位交接单（handoff）

> 维护：测试经理（55230316）。格式依据 `agents/common/04-协作与集成规程.md` §4 固定模板。运行时信息填真实值；**不能为了填满模板虚构结果**，未执行的检查如实标注。

## 最新交付（2026-11-10：最新main全量复跑、定位修正与任务5独立PR）

- 成员/任务：汤雨润（55230316），QA-02/03/04/05。
- 基准：main `ca162bf`（#22 已合入；#21 仍 CHANGES_REQUESTED 未合入），QA 普通合并 `f06482a`。
- 后端：JDK21 + PostgreSQL 17.11 默认 verify，Surefire 77/77；Failsafe 153=151 通过/1 失败/0 错误/1 跳过。唯一失败为 DEF-11（CM 陈旧版本 details 为空）；跳过为既有显式项。
- 缺陷：DEF-08、DEF-09 以实际复跑关闭；DEF-10、DEF-11 仍待 CM（#21），未改测试期望迁就。
- 浏览器：定位修正后 TC-09-2/TC-18-1 2/2 通过（修正前 2/2 失败）；booking.spec、change-happy-path 各 1/1 通过。使用全新数据库 `campus_e2e_20261110`，真实后端/前端，无 Mock。
- 30 场景：28 通过、1 部分（D-AT-20）、1 未执行（D-AT-24）；Tx 96.7%，Ts 93.3%。D-AT-24 未执行：本机缺 compose 插件，仅静态解析 compose 文件。
- 任务5文档已单独提交 **PR #23**（分支 `work/55230316-task5-docs`，base main），不混入 #16；已请求 PM 交叉评审。门禁自测 27/27、真实用例集 exit 0。
- 证据：`artifacts/test-results/qa-2026-11-10/`、[测试报告](test-report.md)、[覆盖矩阵](coverage-matrix.md)、[缺陷登记](defect-register.md)。
- 下一接收人：CM 修复 DEF-10/11（#21 评审阻塞须清零）；PM 复审 #16 与 #23。尚未收到复审接收，未自行 Approve 或合并。

## 历史交付（2026-11-06：PR16复审修正）

- 成员/任务：汤雨润（55230316），QA-02/03/05；接收PM冀项阳对`1700e29`的第二次CHANGES_REQUESTED（与主线`16de4a5`组合复验）。只改QA断言与测试文档，不接AN/CM生产文件。
- 已通过：停用工作人员待确认快照项获复审通过，本轮未改动。
- 本轮修正：`PermissionsIT`新增`assertStaleVersionDetails`，409详情至少返回当前`versionNo`/`lockVersion`/`status`之一且值必须准确，不锁定唯一字段；保留重新GET与无新版本写入；测试设计TC-20-1同步。
- 真实复验严格分开：正式基线仍在AN空意见400前置停止；负向对照（仅解阻AN）在`details={}`命中新断言失败，证明断言有效；解阻实验（AN+CM）目标用例通过。实验补丁不提交。
- 产品缺口：**DEF-QA-2026-11（CM）** API-27两处`STALE_VERSION`未返回版本详情；QA已更新断言，待CM修复后以最新组合复验。DEF-QA-2026-08/09仍归AN。
- 证据：[复审修正说明](pr16-rereview-2026-11-06.md)、[缺陷登记](defect-register.md)、`artifacts/test-results/qa-2026-11-06-pr16-rereview/`。契约与QA类型检查实际通过。
- 下一接收人：CM修复DEF-11、AN修复08/09；PM复审PR16。尚未收到复审接收，未自行Approve或合并main。

## 历史交付（2026-11-03：课程任务5软件测试设计）

- 成员/任务：汤雨润（55230316），QA-03/05；本轮目标为课程软件测试设计与QA质量代码，不是全量产品放行。
- 基准：`work/55230316-qa`，安全普通合并最新`origin/main=16de4a5`，本轮实现起点HEAD=`5f081aa2cccc401aaec35a6948bed37a643890f2`；原未跟踪材料保留，未reset/强推，本轮未创建提交或推送。
- 模板：读取后原名归档至[课程原件](../course/5.3%20测试设计文档模板.docx)，21911字节，SHA-256=`550734eabcc078286b118a84f2fbb2f90330497d370840b7528d03af906c2353`，内容未修改；[归档与基准证据](../../artifacts/test-results/qa-2026-11-03-task5-context.json)。
- 设计：[新版测试设计](test-design-v1.0.md)、[结构化用例集](../../test-data/test-design-cases-v1.0.json)；24组（20UC+4专项）、138条（47正/91负），每组至少1正2负；UC20/20、验收30/30、API40/40均为**设计追踪**，不是执行覆盖。全部新用例标未执行，旧设计及覆盖账本保留。
- QA代码：[质量门禁](../../qa/scripts/check-test-design.ts)、[27项自测](../../qa/scripts/check-test-design.test.ts)、[真实HTTP/PG边界回归](../../server/src/test/java/com/campus/events/acceptance/ContractBoundaryIT.java)。门禁验证结构、必填、场景、极性、状态、编号引用并检查部分内容信号，不代替人工语义评审；自测含反例，防止缺失/空集假通过。
- 已实跑：最新27/27门禁自测、QA TypeScript、40API/9枚举/15DTO契约检查、Maven test-compile通过。父代理最终独立复跑27项自测与类型/契约检查均通过。初次对尚不存在JSON检查失败、初稿11项问题（4项非法需求编号+7项负面关键词误伤）保留原日志；非法编号已修，负面语义规则降为人工复核提示，不能以关键词决定语义正确。
- 2026-11-06新增真实执行：以一次性 Docker PostgreSQL 17.11（隔离 `campus_test`、每测试独立 schema）实际运行 `ContractBoundaryIT` 7项，**4通过/3失败/0跳过**（exit 1）。失败为可复现产品缺陷：DEF-QA-2026-08（空审批意见400）、DEF-QA-2026-10（改期把所有者列入必确认并预建确认行，仅所有者未直达PENDING_APPROVER，CM待接收）。这不是138条设计用例的批量执行，也不表示产品通过；证据见[执行结果](../../artifacts/test-results/qa-2026-11-06-task5-db/result.json)与[缺陷登记](defect-register.md)。
- 未实跑：全量verify、真实浏览器E2E及Compose干净发布；Node26/JDK27偏离基线Node22/Java21，release编译目标21不等于Java21实测。HTTP/PG边界测试已于2026-11-06在隔离测试库实际执行（见上条），资格校验契约确认为提交阶段拒绝，未等到最终确认。（2026-11-10补记：任务5文档与门禁已随PR #23单独提交；后端已在JDK21真库全量复跑，见顶部最新交付。）
- 已知产品缺陷保持正确预期：AN合法空审批意见不得拒绝、CM所有者不向自己工作人员确认。本轮不改AN/CM生产代码，不靠改预期或跳过让其变绿。最新产品结论仍以[PM集成记录](../management/integration-review-2026-11-02-v1.0.md)为准，不上调历史30项覆盖率。
- 组织方式：顶层协调+内容writer、QA tester、排版、独立reviewer。排版前两次子代理运行中断未产出，不伪造其完成；已恢复必要任务。AI审查不冒充团队成员批准。
- [提交与顶层验收说明](task5-submission-v1.0.md)：项目经理冀项阳11月07日23:59（Asia/Shanghai）前上传学习通分组任务5，**仅非代码工件**。尚未接收、尚未上传、无平台回执；不预填签字。
- 最终Word：[测试设计v1.0](test-design-v1.0.docx)，2026-11-06 17:24按用户意见精简后重生成，78052字节，SHA-256=`2254e34c95fa2a2f171af5ed09b4bad6a47575bb89136f078f3e9ad8e76121f5`。已移除临时补充的实施人员/检查清单自检等非设计章节，正文回归测试设计本体（用例数据与统计未变）。独立逐字段核1742项、缺失0；XML与关系部件良构、138条编号/全部环境与数据字典一致；内部路径与验收编号等术语已清零。生成反例实测缺失/空集均exit2且不生成文件。
- 最终技术预审：[审查与处置](task5-review-v1.0.md)。独立reviewer提出的7类必须改已修，补4条（范围不全含两个独立子例）；父代理进一步修单变量前置、旧令牌强401、授权后快照不变及Word统计bug。仅AI技术预审，不是人类正式Approve。
- [最终核验JSON](../../artifacts/test-results/qa-2026-11-03-task5-final/final-verification.json)、[父代理独立QA复验](../../artifacts/test-results/qa-2026-11-03-task5-final/qa-recheck.json)、[Word生成检查](../../artifacts/test-results/qa-2026-11-03-task5-docx/verification-report.md)。最终138条门禁与Word生成均exit0。未做完整XSD验证、Word真实分页视觉检查；项目经理须在Word/WPS人工打开确认。没有平台提交、签字、Git提交或推送。

## 最新修正（2026-11-02：PR16评审两项必须改）

- QA汤雨润55230316，QA-03/05；接收PM冀项阳的CHANGES_REQUESTED（被审19ae94b，main仍b8be7e4）。只修IdentityEligibilityIT与PermissionsIT，不修改AN/CM生产或契约。
- 停用工作人员：待确认快照保留、行数不变、decision/decided_at仍NULL、AWAITING_CONFIRMATIONS不推进；保留401/403拒绝及恢复启用。
- STALE_VERSION：移除details必含version/lock断言；两请求均核对完整错误信封、重新GET版本不变、无新版本写入。
- 原基线两目标仍因既有AN合法空意见400前置失败，不宣称通过；另以不提交的独立副本解除已知AN摘要/两Primary阻塞，完整实验及原始结果严格分开。未按实验提升原基线覆盖。
- 细节、命令、实验补丁及实际统计见[评审修正说明](pr16-review-fixes-2026-11-02.md)。下一接收人PM复审PR16；AN缺陷08/09仍待原作者修复。尚未收到复审接收，不自审合并。

## 历史交付（2026-11-01：推进PR #16正式评审）

- 成员/任务：汤雨润55230316 / QA-02全模块接收、QA-03真库回归、QA-04真实浏览器与评测、QA-05报告。
- main=`b8be7e4`，PR13/14/15/17/18均已合并；QA普通merge=`253c230`加本轮QA补丁。备份`backup/qa-before-all-prs-20261101`保留`e1e175c`；无reset/强推，旧固定远端仍`0bb9aac`，未跟踪历史材料保留。
- Java21已真实运行默认verify：77/77单测；112 IT=101通过/9失败/1错误/1跳过，exit1。通知1/1、容量4/4、取消2/2、改期4/5、权限10/13、身份2/6；AN33项因重复Bean上下文错误未执行，不能声称总数下降即测试减少。
- 实际修正：重复取消200契约、通知真实probe和已读幂等键、改期残留合法撤回与代理下spy故障注入、Seeder末尾SQLState/外键及权限表回滚定位、UI兼容脚本与trace防泄露。均为QA自有范围，不修改生产或expected。
- 真实浏览器登录+三视口15/15；停止本轮后端、关闭seed重启，同库状态一致；重启后权限+页面16/16。布局有重复不相加；随后真实UI改期主线1/1通过，version2同库后端重启及新浏览器reload验证通过。
- 最新30项：18通过、10部分、2阻塞，Tx93.3%、Ts60.0%，不表示产品放行。D-AT22仍160测量/16组expected一致、四资源基准范围。
- 残余AN问题：DEF-08合法APPROVE空意见400、DEF-09测试替身与生产EventPort冲突；旧缺端口缺陷04/05关闭。Node22/完整UI与发布演练仍待完成。接收确认尚未收到。
- 证据：[完整集成报告](pr16-integration-2026-11-01.md)，[默认verify统计](../../artifacts/test-results/qa-2026-11-01/pr16-ready/java21-reviewed/result-summary.json)，[重启结果](../../artifacts/test-results/qa-2026-11-01/pr16-ready/restart-result.json)。
- 下一接收人：AN修复08/09；PM/CM检查发布门槛；另一岗位成员正式交叉评审QA PR16。独立agent技术预审不冒充团队GitHub批准，不直接合并main。

## 历史续验（2026-10-31：PR #15 合并后）

- 成员/任务：汤雨润55230316 / QA-02接收DS、QA-03真库回归、QA-04评测、QA-05覆盖。
- PR #15已合并，main=`c4f3d41`；QA普通merge提交`54d976dfe3c34eaede14391a77deaba17673bbe5`。无冲突，未reset、未强推；原HEAD `291b31d`保留于`backup/qa-before-pr15-20261031`，原未跟踪内容未动。
- 默认 `./server/mvnw -f server/pom.xml verify` 已完成：Surefire 70/70；Failsafe 132项=105通过、24失败、2错误、1跳过，exit 1。ResourceSchedulingIT 30项=29通过/1跳过；DsStrategyBenchmarkIT 2/2；SchedulingBenchmarkIT 1/1，evaluate exit 0，160测量行、16组expected全匹配；SeederIT 3/3、SeederRollbackIT 1/1、DbTestSupportIT 1/1。与PR15前相比增加11通过及1跳过，24失败/2错误仍由CM生产端口缺失的503/404及其级联导致；D-AT-16仍为20/20 JOIN返回503。
- D-AT-09新增真库模块直接证据，改记部分；仍缺固定ID演示/UI。30项当前为4通过、18部分、7阻塞、1未执行；Tx=73.3%，Ts=13.3%。真实跨端口成功切换测试因CM ChangePort缺失明确skipped，不能记通过。
- 证据目录：`artifacts/test-results/qa-2026-10-31/post-pr15-merge/`；本机JDK27，Java21待复验；合同/TypeScript/Playwright列举通过，E2E未运行。
- PR13/14仍OPEN；DS PR15已解除待合并依赖，剩余CM生产EventPort/ChangePort/通知与UI/发布环境。接收确认尚未收到；下一接收人CM杨瀚霖提供生产端口，UI杨畅提供前端入口，QA随后复测。

以下保留历史记录，不将其中旧PR状态作为当前状态。

## 本次工作（2026-10-31：同步feb0d70、真库验证与交接）

- 成员/任务：汤雨润（55230316）/ QA-01数据支撑、QA-02模块接收、QA-03跨模块DB验收、QA-04评测、QA-05覆盖与报告。
- 变更保全：同步前tracked差异、59项未跟踪路径和全部文件归档于 `.git/qa-pre-sync-backup-2026-10-31/`，SHA256校验通过；本地备份分支`backup/qa-pre-sync-20261031`保留旧HEAD。`.review-tmp/`未清理、未提交。未执行reset。
- 基线：QA分支已变基到`origin/main=feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`，main是QA PR分支祖先；最终全量verify运行源码commit=`514b8dcce2d3666ff0fc9f15c3bae7826656c60e`（含D-AT-21 URI编码修复），D-AT-22哈希元数据随全量运行再次验证。原唯一QA领先提交0bb9aac的DbTestSupport修复重放为db3ea4f；本轮另增加`98796ed`（test profile/scoped DB及Benchmark装载事务修复）、`a697d90`（Seeder回滚IT）、`825718f`（覆盖证据更新）、`7ad9cfe`（评测reference fixture隔离）。
- PR状态（本轮GitHub实时查询；origin/main仍为feb0d70）：#8 CLOSED；#10 MERGED（feb0d70，Seeder/IT唯一权威）；#11 MERGED（73c2bcc）；#12 MERGED（c9c757b）；#13 AN、#14 PM仍OPEN且review required；#15 DS已转非Draft，head=`7521511`并含SchedulingPortAdapter等改动，但仍需评审；三者均未合入main。本QA工作由Draft PR #16承载，分支`work/55230316-qa-feb0d70`、base=`feb0d70`。
- 备份后复核`0bb9aac`：每数据文件事务、资源后写user_resource_scope、`LocalTime`绑定SQL time、`::jsonb`绑定和新增DbTestSupportIT。最终真实库`DbTestSupportIT`通过；另有`SeederRollbackIT`验证末尾报名FK失败后organization/user/resource/activity/version/reservation/registration全回滚。
- 正式Seeder：默认verify中`DemoDataSeederIT` 3/3通过（首次授权、撤权不重放、删用户后新用户授权）；QA新增`SeederRollbackIT` 1/1通过，PostgreSQL 17，`DEMO_PASSWORD`与DB密码为运行时随机值，未写入源码/日志。
- 最终默认Maven：`./server/mvnw -f server/pom.xml verify`，源码和测试均编译成功；Surefire 70/70通过；Failsafe 120项=94通过、24失败、2错误、0跳过，命令退出1。运行时JDK为27.0.1（目标Java21），需在JDK21重验。全量结果见`artifacts/test-results/qa-2026-10-31/final-after-calendar-encoding/`。失败主要是生产EventPort/ChangePort缺失；D-AT-16诊断复验20/20 JOIN均503 INTERNAL_ERROR（ChangePort缺失）；D-AT-21 URI编码修复后日历脱敏子用例通过，详情链仍被ChangePort阻塞；其余各项映射见coverage-matrix/defect-register。
- D-AT-22：初始不一致由canonical demo中的N-AT资格扩展资源污染候选空间导致。QA使用仅含3101/3102/3201/3202的Benchmark-only reference fixture，expected未改；commit `c3a4a2c`专项真PG复验输出reference/scenario路径及SHA-256，BenchmarkIT 1/1且evaluate exit 0，160行与全部expected一致。旧mismatch快照仍归档备查。
- 其余支撑检查：40 API合同、demo/并发/调度引用、QA TypeScript、Playwright 29项列举通过；这些不计产品验收/E2E。`web/package.json`缺失且本机无`docker compose`插件，未运行真实浏览器E2E/发布演练。
- 覆盖：30项已逐项更新；4项通过（其中D-AT-04/05/06仅限DS模块真实PG范围，D-AT-22基准评测全expected一致），17项部分覆盖，7项被生产端口/交付阻塞，2项未执行，当前无D-AT/N-AT计为失败。具体状态、Tx/Ts口径及责任人见`coverage-matrix.md`和`test-report.md`。这不表示M0–M4完成。
- PR与接收：按用户授权未改写旧远端固定分支，新建远端`work/55230316-qa-feb0d70`并已创建Draft PR #16（base feb0d70）；URL：https://github.com/Rito-492/campus-sched/pull/16。原`origin/work/55230316-qa`保持不动。PM/CM/AN/UI接收确认仍未收到。

| 检查 | 当前结果 | 证据 |
|---|---|---|
| 备份/同步 | 完成；QA基于feb0d70，备份SHA256通过 | `.git/qa-pre-sync-backup-2026-10-31/`、本地备份分支 |
| PR #8/#10/#11/#12 | #8关闭；#10/#11/#12合并 | GitHub PR状态及合并commit |
| SeederIT / SeederRollbackIT / DbTestSupportIT | 全部真实PG Failsafe通过 | `artifacts/test-results/qa-2026-10-31/final-after-calendar-encoding/failsafe-reports/` |
| 默认Maven verify | 70 Surefire pass；120 Failsafe=94 pass/24 fail/2 error；exit 1 | `artifacts/test-results/qa-2026-10-31/final-after-calendar-encoding/default-verify.exit`、`failsafe-reports/failsafe-summary.xml`与结构化XML；stdout原始日志保留本地 |
| 评测汇总 | 160测量行；D-AT-22 evaluate exit 0、expected全匹配 | `artifacts/test-results/qa-2026-10-31/final-after-calendar-encoding/evaluate.log`、`artifacts/evaluation/summary.md`、`artifacts/evaluation/results.csv` |
| 契约/数据/TypeScript/Playwright列举 | 静态检查通过；29 tests列出但未执行浏览器 | `artifacts/test-results/qa-2026-10-31/static/` |
| D-AT/N-AT | 状态已按真实运行逐项更新 | `coverage-matrix.md`、`test-report.md` |

## 历史工作（截至2026-10-26：PR状态、Seeder构建及环境复核）

- 成员/任务：汤雨润（55230316）/ QA-02 合同与模块接收、QA-03/04/05 前置验证与证据更新。
- 分支：`work/55230316-qa`，HEAD `0bb9aac`；工作区原有未跟踪目录 `.review-tmp/` 保留，未查看/修改其内容。无已跟踪文件预存改动。
- 基准核实：fetch 后 `origin/main=34dc4d5abd3d859e3a63ff476228593555f2e03b`（PR #9 合入）。QA分支尚未同步该最新基准；本轮构建在QA分支执行；可直接读取的server/pom.xml及CM Seeder源码与当前main对应文件一致。未自动合并，避免改变个人分支状态。
- PR最新状态（2026-10-26，GitHub `gh pr view`）：PR #8 head `ee76c2a` OPEN/CHANGES_REQUESTED/BLOCKED；PR #10 head `a6d734c` OPEN/CHANGES_REQUESTED/BLOCKED，review显示其包含与#8重复的Seeder源码与IT且测试存在硬编码密码；PM/CM尚未指定唯一权威来源，不对两个副本重复记证据。PR #11 head `0886c8b` OPEN/CHANGES_REQUESTED/BLOCKED，评审指出API-13 scope=mine缺工作人员/报名历史并集、API-14缺本人报名历史详情授权；责任AN，未合并代码不得记验收通过。
- 环境：`TEST_DB_*`变量未设置；5440/5432/8081/8080/8089/5173端口均未监听。Docker CLI Server 29.9.2可用，但本轮未启动测试Compose。
- 默认构建：`./server/mvnw -f server/pom.xml -DskipTests compile` 失败（退出1），`DemoDataSeeder.java:118` 未处理`seedActivity()`声明的受检`Exception`；`-DskipTests test-compile`同样停在main源码编译阶段。证据 `artifacts/test-results/qa-2026-10-26/default-compile.log`（完整默认`test`输出为同目录`default-test.log`），缺陷`DEF-QA-2026-01`分派CM杨瀚霖。未进入任何测试。
- Seeder验证计划：待PM/CM选定#8/#10唯一权威后，仅对该来源跑默认构建和正式Failsafe `DemoDataSeederIT`，验证首次导入、撤权后重导不恢复、新用户授权正确、失败整体回滚。`DEMO_PASSWORD`必需且只能安全环境注入，日志不得回显。本轮未有唯一来源、DB凭据或运行数据库，故未运行Seeder IT；不以临时runner/隔离测试作为证据。
- 30个D-AT/N-AT仍全为“未执行”。默认编译失败是构建/缺陷证据，不将D-AT-24记失败或通过。API合同/权限、首次预约、资格、并发报名/取消回滚及E2E均未实际执行。
- 本轮修复测试计划中38→40 API及TEST_DB_*变量说明；修正coverage/test-design/test-data-plan中的真实E2E/IT类名与v3.0术语，移除qa helpers.ts中硬编码密码默认值（登录用例仅在运行时读取必需环境变量）；新增test-report.md和本轮测试数据/列举证据。未修改生产模块。
- 后续依赖：PM冀项阳+CM杨瀚霖决定PR #8/#10唯一Seeder权威、处理PR #8分支落后主线问题并消除IT硬编码密码；CM修复默认构建及Compose/环境入口。PR #9已把Scheduling/StrategyEvaluator/StrategyResult合入main，不再列作待交付依赖；QA分支需先同步main才能编译SchedulingBenchmarkIT。AN冯玉琪修复PR #11评审指出的API-13 `scope=mine`缺并集、API-14本人报名历史详情授权缺口，合并后QA复验。UI杨畅目前`web/`仍只有Dockerfile与.gitkeep，缺package.json，E2E等前端工程。当前测试环境需显式安全提供`DEMO_PASSWORD`；代码仍有TEST_DATABASE_URL与TEST_DB_*分开读取的口径，待CM统一。接收确认：尚未接收。

| 检查 | 结果 | 证据 |
|---|---|---|
| `git fetch origin --prune`/分支核对 | 完成，QA HEAD `0bb9aac`，origin/main `34dc4d5` | 本次命令记录 |
| `gh pr view 8/10/11` | 完成，均OPEN且BLOCKED/CHANGES_REQUESTED | GitHub当前状态及#10评论 |
| TEST_DB_*与服务端口 | 未配置/未监听 | 本次环境检查；未记录秘密值 |
| `node qa/scripts/check-contract.ts` | 通过 | 40 API、9组枚举、15 DTO全部通过；输出 `artifacts/test-results/qa-2026-10-26/contract-check.log` |
| 数据引用脚本 | 通过 | `data-reference-check.py` 核对demo、并发、调度数据集；结论在本轮复核记录，未更改原历史脚本输出 |
| `npx tsc --noEmit`（qa） | 通过 | Playwright TypeScript类型检查 |
| `npx playwright test --list`（qa） | 通过 | 29 tests / 7 files；测试列举不是执行/E2E通过 |
| `python3 artifacts/test-results/qa-2026-10-20/data-reference-check.py` | 通过 | demo、并发、调度数据集引用完整；本轮明细见`artifacts/test-results/qa-2026-10-26/data-reference-check.log` |
| 默认compile / test-compile | 失败，退出1；未进入测试 | `artifacts/test-results/qa-2026-10-26/default-compile.log` |
| Seeder Failsafe/契约/E2E/全量验收 | 未执行 | 唯一来源未决且无数据库/API/UI服务 |

历史批次记录如下，所述基准/阻塞是当时观察，不代表本轮现状：

## 本次工作（2026-10-20：PR #7 合并后 QA 支撑复核）

- 成员、学号、任务ID：汤雨润 55230316 / QA-01 支撑复核 + QA-02 合同/数据校验
- 基准：最新 `origin/main=4fbda0ecf4f155ef1e05c385a677ebe7d13b087d`（PR #7 已合并）；本地 `work/55230316-qa` 从 `7e365ff` 快进同步至该基准，保留既有工作。
- 当前状态更正：`server/pom.xml`、Maven Wrapper、共享 contract、`V1__schema.sql`、CM Compose/check 脚本现已在 main。下文早期批次中“缺 server/pom.xml / Maven Wrapper”均为当时的历史阻塞，不代表当前状态。
- DbTestSupport 复核发现并修复（仅修改 QA 所有的测试支撑代码，不改生产模块）：
  1. `user_resource_scope.resource_id` 是 V1 即时外键；原装载顺序先写授权后写资源，真实 PostgreSQL 报外键错误。现先记录待写范围，写入资源后再写范围。
  2. `activity` 的当前/有效版本外键为 `DEFERRABLE INITIALLY DEFERRED`；原装载器各次 `JdbcTemplate.update` 自动提交，活动行会在对应版本插入前提交。现将每个数据文件的装载放在单一事务中。
  3. `activity_version.input_json` 为 jsonb；现 SQL 显式使用 `?::jsonb`。实测还发现资源 `open_from/open_to` 是 SQL `time` 列，原传 `String` 会失败，现改传 `LocalTime`。
  4. 新增 `server/src/test/java/com/campus/events/support/DbTestSupportIT.java`，断言 demo 装载后的组织、用户、资源、资源范围、活动、版本及 JSONB 结构。
- 本轮实际检查与结果：
  - ✅ `node qa/scripts/check-contract.ts`（Node 26.12.0）：40 API、9 组枚举、15 个 DTO 全部通过。
  - ✅ 测试数据引用校验：demo（2组织/9用户/12资源/2活动/1报名）、并发场景（20用户/1活动）和调度场景（8场景、2策略、300候选）引用完整。
  - ✅ 临时 Docker `postgres:17`（实际 PostgreSQL 17.11，数据库 `campus_test`，随机隔离 schema、Flyway V1）：真实调用 DbTestSupport 的独立 Java 检查通过；demo 全数据装载、`loadReferenceData` 只装参考数据、故意制造无效报名外键失败后无部分数据残留，均通过。修复前曾真实复现资源范围外键错误；中间修复后又复现 `open_from` varchar→time 错误，现已修复。
  - ✅ `DbTestSupport.java` 与新增 `DbTestSupportIT.java` 用 Maven 测试依赖完成 `javac` 编译；实际装载行为由同一支撑类的临时运行器直接调用验证。
  - ⬜ **未执行**：通过 Maven Failsafe 正式运行 `DbTestSupportIT` 和全套 QA IT；默认全工程编译仍受主线 CM 种子导入旧代码及 DS 缺失的 `StrategyEvaluator`/`StrategyResult` 阻塞。临时运行器结果不记作 30 场景验收通过。
- PR #8 验证边界：PM 提交的 `artifacts/test-results/pm-2026-10-20/pm-cm-isolated-verify.log` 记录临时 PM+CM 隔离副本 22 项单测和 24 项 IT 通过（其中 CM 3 项）；这是可查的实际数据库证据，但本轮 QA 未独立重跑该 Maven 套件，也不等于默认全量构建或 QA 验收。
- 证据：`artifacts/test-results/qa-2026-10-20/`（合同检查、测试数据引用、真实 PostgreSQL 装载及运行环境记录）。临时数据库容器测试结束后停止；不使用 H2、生产库或业务 Mock。
- 当前依赖及负责人：
  1. CM PR #8 最新提交 `ee76c2a` 修复种子导入编译/导入顺序/授权重放；GitHub 显示 CLEAN/APPROVED，但已记录的正式评审提交是旧 head `52f3736`，仍需确认对最新 head 的评审并集成验证 → 杨瀚霖 55231031。
  2. 主线缺 `StrategyEvaluator.java`、`StrategyResult.java`；CM 修复进入后默认测试编译仍会被这两个类型阻塞 → 黄方勇 55230315。
  3. Activity/Registration、Scheduling、Change/Event 业务端口与 Controller 仍未形成完整主流程；web 尚无 `web/package.json` → 冯玉琪 22220314、黄方勇 55230315、杨瀚霖 55231031、杨畅 17231018 分别按所有权推进。
  4. `coverage-matrix.md` 中 30 个 D-AT/N-AT 仍全部“未执行”；真实业务服务/E2E 尚未就绪 → QA 后续独立验收。
- 下一接收人及动作：CM/DS 交付并集成后，QA 在标准 Maven/Failsafe 和真实 Compose 测试环境复验；每个场景有实际证据后才更新覆盖状态。接收确认：尚未接收。

## 本次交付（2026-10-14 第五批，QA-04 补漏与验收裁决）

- 成员、学号、任务ID：汤雨润 55230316 / QA-04（补漏）+ QA-02（接收）
- 基线：baseline/task-package-v3.0（d07c967）；main 已含 PR#1-#3
- 交付文件与接口：
  - `acceptance/SchedulingBenchmarkIT.java` 补齐（QA-04 历史漏项）：8 场景→StrategyEvaluator（02 §8 四参签名，含 Identity）→160 行原始结果写 server/target/scheduling-evaluation.json
  - `DbTestSupport.loadReferenceData()` 纯增量（评测只装载参考数据，不带演示活动，§7 规则）
  - `test-data-plan.md` §9 新增：评测原始结果 JSON 格式（修复 handoff 悬空引用）
  - `testid-requirements.md` 新增：12 公共 testid + 10 扩展项（交 UI）
- 实际运行命令与结果：
  - ✅ v3.0 §8 签名四参（含 applicantIdentity）实测核对——任务书旧口径三参已按契约修正
  - ✅ javac 语法级：仅缺依赖级联错误（含 LinkedHashMap 推断=Jackson 缺失级联，已甄别）
  - ✅ DbTestSupport 公共 API 稳定性核对：无缺失，纯增量
  - ⬜ **未执行**：SchedulingBenchmarkIT 真实运行（等 PM 工程 + DS StrategyEvaluator）
- 验收编号与证据路径：D-AT-22 通道待实测
- 未完成项/外部依赖及负责人：
  1. StrategyEvaluator/StrategyResult（02 §8）→ hfy 55230315（她 handoff 已注明等 PM contract 包）
  2. PM 公共工程 → jxy（全链路编译）
- 下一接收人及其动作：hfy 交付 StrategyEvaluator 后我跑评测全链路；yc 按 testid-requirements.md 提供标识
- 接收确认：尚未接收

### QA 验收记录（QA-02「接收各模块证据」）

1. **CM 种子导入器（PR#2）接收：通过**。字段级核对：identity/roles/permissions/approvalScopes/managedResourceIds/allowTeacher/allowStudent 全部按 02 §2 解析落库；派生规则与 test-data-plan §5 逐条一致（version_no/effective_version_no/activity_version/reservation 区间含布撤场）；${DEMO_DATE} 占位替换；APP_DEMO_SEED 门禁；已存在幂等跳过；缺 DEMO_PASSWORD 跳过不造假。
2. **`.env` 变量名分歧裁决（hfy 提出）：已解决**。证据：CM 配置用 TEST_DB_USERNAME/TEST_DB_PASSWORD，QA 侧原用 TEST_DATABASE_*。裁决：**以 CM 的 TEST_DB_* 为准**（改动面最小），DbTestSupport 改为 TEST_DB_* 优先并回退 TEST_DATABASE_* 兼容，两侧现在互通。请 yhl 在 .env.example 中只列 TEST_DB_* 一套。
3. **PR#3（AN 状态机）接收：通过**（评审记录见合并提交 d07c967）。
4. **PR#1（DS schema）接收：通过**（评审记录见合并提交 ed8c3bc）。

## 本次交付（2026-10-13 第四批，v3.0 测试代码全量改造）

- 成员、学号、任务ID：汤雨润 55230316 / QA-02（改造）+ QA-03（跨模块+N-AT）+ QA-04（E2E）
- 基线：v3.0；实际 main 基准 `5097af7`；本人分支含本地提交待推送
- 交付文件与接口：
  - `acceptance/` 六 IT 全量 v3.0 迁移（用户名/三角色枚举/approver-decision/PENDING_APPROVER/approverDecision/RESOURCE_INELIGIBLE；PermissionsIT 增 TC-21-4 对 API-04 新语义）
  - `acceptance/IdentityEligibilityIT.java` 新建：N-AT-01~06 六方法（身份角色分离/8组资格矩阵/候选过滤/最新设置/回避/范围与停用）
  - `support/DbTestSupport.java`：装载器对齐 02 §4 新表（user_permission/user_approval_scope/user_resource_scope/identity/allow_teacher/allow_student），公共 API 稳定，新增只读辅助查询
  - `qa/tests/`：7 spec 29 tests（原 5 迁移 + 新增 booking.spec（D-AT-03 首次预约）、eligibility.spec（N-AT-02 资格限制））；testid teacher-approve→approver-approve
  - 文档：test-design v0.2（v3.0 迁移+N-AT 索引+课件5.1正负配对/6.1过程五要素）、test-plan v0.2（课件3.1 项目核实表/八类测试策略/6.2 覆盖率口径）、defect-register.md（课件6.2 模板）、coverage-matrix 落点更新
- 实际运行命令与结果：
  - ✅ 旧标识全局 grep（organizer_a/teacher_a/sys_admin/PENDING_TEACHER/teacher-decision/teacherDecision/ORGANIZER/SYS_ADMIN）残留 0
  - ✅ javac 语法级全量检查：报错全部为缺第三方依赖级联，真语法错误 0
  - ✅ `npx tsc --noEmit` 通过；`npx playwright test --list` = 29 tests / 7 files
  - ⬜ **未执行**：全部测试真实运行（编译依赖 PM 的 pom/contract 包，运行依赖服务）
- 验收编号与证据路径：无通过项；30 场景状态全为未执行（coverage-matrix）
- 未完成项/外部依赖及负责人：PM 公共工程（编译）、DS V1 schema 合入（17 表已产出待合）、CM 环境、UI testid（多处语义兜底已注释待提供）
- 下一接收人及其动作：jxy 基础工程就绪后我跑首次编译与 IT；yhl 按 v3.0 demo.json 实现种子
- 接收确认：尚未接收

## 本次交付（2026-10-13 第三批，v3.0 对齐第一批）

- 成员、学号、任务ID：汤雨润 55230316 / QA-01（新版数据）+ QA-02（契约脚本）首批
- 基线：v3.0（任务包）；实际 main 基准：`5097af7`（已 merge 入 `work/55230316-qa`）；本人分支 @ 本地提交
- 交付文件与接口：
  - `test-data/demo.json` 重写：三角色（APPLICANT/APPROVER/ADMIN）+ 独立身份 + permissions/approvalScopes/managedResourceIds + 8 项资格资源（3111–3114/3211–3214 四组资格）
  - `test-data/scenarios/registration-concurrency.json`：20 学生补 v3.0 字段
  - `docs/testing/test-data-plan.md` v0.2：尾号表（资格资源段）、users/resources v3.0 格式
  - `docs/testing/coverage-matrix.md`：**30 场景覆盖矩阵**（D-AT-01～24 + N-AT-01～06 ↔ 需求 ↔ QA 执行落点 ↔ 状态）
  - `qa/scripts/check-contract.ts` 升级：40 API（API-18/31 approver-decision、API-39/40）、三角色/Identity/Permission 枚举、资格 DTO
  - 课程测试课件 4 份归档 `docs/course/`（3.1/5.1/6.1/6.2，含老格式 .ppt 读取）
- 实际运行命令与结果：
  - ✅ v3.0 数据引用校验（Python 八组断言：顶层键/ID 唯一/角色权限范围规则/03§1 账号配置/资格四组/跨文件引用）全部通过
  - ✅ `npx tsc --noEmit` 通过；check-contract 缺 openapi 退出码 1（正确）
  - ⬜ **未执行**：IT/E2E/评测真实运行（服务未就绪）；test-design.md 旧断言的 v3.0 改造（teacher-decision→approver-decision、五角色→三角色断言消除）为下一步
- 验收编号与证据路径：本批为数据/脚本/矩阵交付，无 D-AT 通过项；覆盖矩阵状态列全为「未执行」
- 未完成项/外部依赖及负责人：
  1. 40 API 的 openapi.yaml → jxy 55230223（PM）；到位后跑真实契约检查
  2. IT/E2E 的 v3.0 断言改造 → 本人下一步；依赖 PM 公共工程编译验证
  3. 课程课件模板要求（测试计划结构/正负用例/缺陷 A–D 等级/Tx·Ts 覆盖率公式）→ 并入 test-plan/test-design/report 后续版本
- 下一接收人及其动作：jxy 提供 openapi.yaml 后通知我跑契约检查；yhl（CM）按 v3.0 demo.json 实现种子导入（DTO 同 02 §2）
- 接收确认：尚未接收（仅真实收到反馈后更新）

## 本次交付（2026-09-26 第二批，QA-03/QA-04 脚本）

- 成员、学号、任务ID：汤雨润 55230316 / QA-03（契约检查脚本部分）、QA-04（评测脚本部分）
- 基线：v2.0；基准commit/包版本：`work/55230316-qa` @ `824ef61`
- 交付文件与接口：
  - `qa/scripts/evaluate-scheduling.ts`——读 `server/target/scheduling-evaluation.json`，产出 `artifacts/evaluation/results.csv`（03 §4 八列）+ `summary.md`（机器/版本/数据/计时范围/中位数与范围/期望对照）
  - `qa/scripts/check-contract.ts`——核对 38 接口、7 组枚举、12 个 DTO 必需字段、响应包装、认证、Idempotency-Key
  - `docs/testing/test-plan.md` §4.1 覆盖判定规则（04 §3 判定权的规则化）
- 实际运行命令与结果：
  - ✅ `npx tsc --noEmit`（qa）——通过
  - ✅ evaluate 正路径：160 行合成夹具正确汇总、无解行代价 null、CSV 列名与 03 §4 一致；负路径：抽掉场景行后退出码 1 且不生成产物
  - ✅ check-contract 正路径：GOOD 夹具（38 接口/契约字段齐）退出码 0；负路径：BAD 夹具抓出 22 处差异退出码 1（缺接口/缺幂等键/枚举缺值/DTO 缺字段均命中）
  - ✅ 无输入时两者退出码 1（缺文件必失败，04 §5）
  - ⬜ **未执行**：对真实 `contracts/openapi.yaml` 与真实评测 JSON 的检查（等 PM 的 openapi.yaml、DS 的 StrategyEvaluator）
  - 夹具用后即删，`artifacts/` 无任何残留——**测试证据只能由真实运行产生**
- 验收编号与证据路径：暂无 D-AT 通过项；脚本自测输出仅本次命令行记录
- 未完成项/外部依赖及负责人：
  1. `contracts/openapi.yaml` → **jxy 55230223 / fyq 22220314**，到位后我跑真实契约检查（`npm --prefix qa run test:contract`）
  2. SchedulingBenchmarkIT 的原始 JSON 输出 → 依赖 **hfy 55230315** 的 StrategyEvaluator（02 契约 §8）；JSON 格式见 test-data-plan §9
  3. 其余同第一批（pom/contract 包、V1 schema、E2E Compose、data-testid）
- 下一接收人及其动作：jxy/fyq 提供 openapi.yaml 后通知我；hfy 的评测入口就绪后跑 QA-04 全链路
- 接收确认：尚未接收（仅真实收到反馈后更新）

### 流程观察（供 PM/CM 决策，非阻塞）

- 所有成员提交的 git author 均为 `Rito-492`，无法从提交溯源到人（本次 AN 分支的两提交因此被误认）。建议各成员设置独立 git 身份（姓名首字母加学号，与公开仓库匿名化一致），由 CM 在配置管理计划中约定。

## 本次交付（2026-09-26 第一批，QA-01 全部）

- 成员、学号、任务ID：汤雨润 55230316 / QA-01（M0测试计划、固定数据和支撑代码）
- 基线：v2.0；基准commit/包版本：`baseline/task-package-v2.0`；分支 `work/55230316-qa` @ `4f5a7a1`
- 交付文件与接口：
  - `docs/testing/test-data-plan.md`（ID尾号/占位符/导入器派生规则 §5）、`test-design.md`（24项D-AT→33用例）、`test-plan.md`（含E2E环境约定 §3）
  - `test-data/demo.json`、`test-data/scenarios/{registration-concurrency,scheduling}.json`（8个评测场景）
  - `server/src/test/java/com/campus/events/support/{DbTestSupport,TestClockConfiguration,ConcurrentRunner}.java`
  - `qa/package.json`+`package-lock.json`（Playwright 1.63.0/TS 7.0.2 锁定）、`qa/tsconfig.json`、`qa/playwright.config.ts`
- 实际运行命令与结果：
  - ✅ JSON机器校验（合法性/ID唯一/引用完整/字段与02契约一致/代价列名与03§4一致）——通过
  - ✅ `npm install`（qa，lockfile v3 / 27包）——通过
  - ✅ `npx tsc --noEmit`（qa）——通过
  - ✅ `npx playwright test --list` ——真实退出码 1（0测试必失败语义正确；注意管道会吞退出码）
  - ⬜ **未执行**：Java三件套编译（阻塞于PM基础工程，见下）；24项D-AT场景执行（依赖产品实现，用例状态初始全部"未执行"，见 test-design.md §0）
- 验收编号与证据路径：本批为QA-01支撑交付，暂无D-AT通过项；证据目录约定 `artifacts/screenshots/`、`artifacts/test-results/`（03 §5）
- 未完成项/外部依赖及负责人：
  1. `server/pom.xml` + `com.campus.events.contract` 包 → **jxy 55230223（PM的M0基础工程）**。共享工程缺失期间未自建第二套工程（04 §2）
  2. `server/src/main/resources/db/migration/V1__schema.sql` → **hfy 55230315（DS）**，DbTestSupport的Flyway迁移依赖
  3. `scripts/load-env.ps1`/`check.ps1` + E2E测试Compose（dev,e2e profile、campus_test、端口8089、固定APP_TEST_NOW/DEMO_DATE）→ **yhl 55231031（CM）**，约定见 test-plan.md §3
  4. 故障场景冲突码（EQUIPMENT_SHORTAGE vs RESOURCE_FAULT）契约未唯一指定 → **jxy 按02契约§7裁决**，用例暂不硬断言
- 下一接收人及其动作：yhl（CM）按 test-data-plan.md §5 实现种子导入器与E2E Compose；jxy（PM）基础工程落地后知会我跑Java首编译；hfy（DS）按02契约§8提供StrategyEvaluator并核对scheduling.json场景几何
- 接收确认：尚未接收（仅真实收到反馈后更新）

## 第六批：课程任务2工件——软件测试计划（Word 版，2026-10-16）

- 交付物：`docs/testing/T08_软件测试计划.docx`（终稿约6800字，A4，含目录/页眉页脚）
- 依据：RUP 测试计划模板（课件3.1 六节结构）+ 课下任务2检查表（7项逐一对位，含-0.8大项：测试需求显式含用户界面/性能评测/访问控制/安装四类）
- 终稿审查修正（对照 coverage-matrix 实况）：D-AT 编号映射校正（初稿误将 D-AT-09/10 写作审批链，实属改期链；D-AT-22 为 300 候选对比）；实施员补齐杨瀚霖（5人）；表数以 V1__schema.sql 实数 22 为准
- 精简：初稿1.1万字按用户要求精简至6852字（64%），检查表7项与11类测试类型完整保留
- 课程名称：按《课下任务2》课件首页，将封面课程名更正为“软件工程案例分析与实践”
- 真实性：项目核实表12项均对照仓库现存文档；负载测试按模板给"不实施+理由"
- 验证：docx ISO 校验 PASSED（381段）；文本提取逐项核对检查表7/7、D-AT映射修正7/7、11类测试类型——全过
- 已知限制：本机无 LibreOffice，未出 PDF（任务要求 PDF 或 Word 之一即可）
- 待外部：yhl 出 T08_配置管理计划.docx（检查表4项）；jxy 打包 .zip 提交学习通；时间口径（10-17 vs 课件10-22）待确认
- 记录更正：早前提交 5bf9e17 的提交信息含"handoff 第六批"，但该条目因 reset --hard 未实际入库，以本条为准
