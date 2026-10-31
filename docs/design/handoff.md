# 设计岗位交接单（handoff）

> 维护：设计师（55230315）。格式依据 `agents/common/04-协作与集成规程.md` §4 固定模板。运行时信息填真实值；未执行的检查如实标注，不虚构通过。

## 最新续验（2026-10-31：QA原始修复接收与评审交付）

- 黄方勇55230315，DS-03～05；本人分支work/55230315-scheduling；fetch后main仍为feb0d70，本地历史工件和stash保留。
- 读取QA原始分支work/55230316-qa-feb0d70提交4681e4e5ee4eade6725582b4bce23a1af20cb903；独立detached工作区叠加DS生产修复bee9805及旧版29项资源IT。未在本人分支合入或改写QA文件。
- 本机Java21.0.6 / 标准Maven3.9.11 / 原始pom / PostgreSQL17.6：QA组合verify退出0，70单测及39项IT无失败/错误/跳过。SchedulingBenchmarkIT产出160行，原QA汇总脚本退出0，16组期望一致，FIRST_FIT/RANKED均7/8可行。DS确认QA专用4资源fixture的场景几何与生产策略一致；这是该源码组合的D-AT-22专项复验通过，非main或整机验收。
- 修正自身测试表达：拆开CM缺失保护与真实切换成功测试。本人最终代码标准verify：70单测通过；38项IT中37通过、1明确skipped（缺真实ChangePort，切换/回滚未执行），无失败/错误；退出0不作为完整联调通过。CM EventPort/ChangePort及UI/E2E仍未到位。
- 证据：artifacts/test-results/ds-2026-10-31-followup/，含运行日志、退出码、160行原始JSON、CSV、摘要、源文件SHA-256。旧轮失败与建议实验保留为历史；详情integration-validation-v1.0.md。
- PR #15更新后进入Ready for review，交叉评审及合并仍未完成；QA分支合入目标后由QA复跑，不自动合并main、不自动联系其他聊天。课程平台仍无提交回执。下一接收人：QA汤雨润、PM冀项阳、AN冯玉琪、CM杨瀚霖；尚未取得接收确认。

## 前次交付（2026-10-31：main=feb0d70 标准复验与联调）

- 黄方勇 55230315；DS-01～05；任务包 v3.0；分支 work/55230315-scheduling，main 基准 feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9。同步前 stash -u，快进后 apply，本地修改全部保留，备份 stash 未删除。
- PR #9 调度实现与 PR #12 任务4设计文档均已合入。课程正式工件仍为 software-design-task4.md/.docx/.pdf；学习通提交尚无回执，状态“尚未确认提交”，截止沿用 2026-10-31 23:59（上海时间）。本轮未覆盖已评审 Office 工件。
- 标准 Maven 3.9.11、Java 21.0.6、真实 PostgreSQL 17.6：70 单测通过；SchemaIT 8、ResourceSchedulingIT 28、DS 独立策略评测2通过。包含正式 SchedulingBenchmarkIT 的 verify 整体失败，不能记整机通过。证据 artifacts/test-results/ds-2026-10-31/standard-verify.log；独立策略两份160行原始JSON同目录。
- 新增真实 AN/DS 端口探针：ActivityPort.lock 依赖 CM ChangePort，当前503；按成功链路执行的失败日志保留，补充明确依赖拒绝断言后70单测及1项探针通过。只证明缺依赖无部分写入；跨端口成功切换/回滚分支尚未执行。CM EventPort 同样缺失，故障API实测503且无故障/outbox写入。
- QA 修复 0bb9aac 的 DbTestSupport 已在临时 detached 集成工作区接入复验，不混入本人的源码提交；AN ActivityRegistrationIT 33项通过（含CM测试替身，仅模块结果）。正式 D-AT-22 仍复现评测器自身 activity/version 自动提交外键失败；提供场景事务及JSONB转换建议补丁，详见 integration-validation-v1.0.md 与同目录日志。
- 建议补丁实验：标准 Maven 的70单测及 SchedulingBenchmarkIT 1项通过，生成160行真实测量；原QA汇总脚本退出1，SCH-01/04/08两策略共6组预期不一致。补丁、原始JSON、results.csv、summary.md一并交QA；D-AT-22仍不记通过，非整机验收。
- 下一接收人：AN 冯玉琪核对端口；CM 杨瀚霖交付 EventPort/ChangePort 后复跑成功链路；QA 汤雨润接收补丁与真实数据偏差；PM 冀项阳组织交叉评审。接收确认：尚未接收。通过本人分支 PR 交付，不合并main。

## 历史交付（2026-10-26：DS-02～05 真库闭环、预约锁修复及评测差异）

- 成员：黄方勇 55230315；任务包 v3.0；本人分支 `work/55230315-scheduling`。开工工作区干净，Git 身份 `Kimcra1`；fetch 后最新 main 为 `34dc4d5abd3d859e3a63ff476228593555f2e03b`，从 `bc8e7cf` 快进同步。同步前相对 main ahead/behind=0/1、相对本人远端=0/0；同步后分别为0/0、1/0。本轮代码/文档/证据未提交、未推送，未合并main。
- **生产修复**：`SchedulingPortAdapter.Checked` 保存不可变的设备列表快照，防止检查后调用方修改 Plan.equipment，使 replace 写入未锁资源。真库回归在旧代码实际失败，修复后通过。未改共享Plan/端口、02契约、V1或其他模块。
- **新增验证**：ResourceSchedulingIT 增加8项真实数据库/API/事务测试；DsStrategyBenchmarkIT 增加完整参考资源与四资源对照两个评测环境。已有资源资格/历史、日历/硬约束、资格行锁竞争、预约竞争/替换/释放/风险及300预算均真实复跑，不重复实现纯逻辑。
- **最终结果**：隔离构建 `23项DS单测 + 38项PostgreSQL IT`（资源28、迁移8、独立评测2），退出0，失败/错误/跳过均0。仅本机campus_test随机schema；全部清理，public无表，本轮PostgreSQL实例已停止。
- **默认入口**：先执行Windows默认compile/test/verify，均在Wrapper启动失败；使用Unix Wrapper与Java21后，三个默认入口仍在CM `DemoDataSeeder.java:118` 未处理Exception处失败。隔离全部单测52项仍有AN `ActivityStateMachineTest:49` 1项失败。原日志及每个首个失败保留，没有排除失败测试来宣称默认全过。
- **真实依赖状态**：main仍无AN ActivityPortAdapter和CM EventPort。API-10实测503，0故障/回执/审计/outbox、原预约保留；API-24/25成功HTTP链路待ActivityPort。现有测试中EventPort调用记录/故障注入替身仍仅限测试，不代表CM持久化通过。
- **策略结果**：每环境8场景×2策略×10次=160条原始行，各预热1次。全部12资源的参考环境两策略均8/8成功，但SCH-01/04/08各两策略共6组偏离原预期；四资源对照两策略均7/8成功，八场景预期全部匹配。300无解预算另有20条实测，FIRST_FIT/RANKED中位数199.50/181.35ms。无解choice/代价为null。正式SchedulingBenchmarkIT实际失败于DbTestSupport:236资源范围外键顺序，尚无正式测量结果；不以DS输出冒充QA results.csv。
- 证据：[验证说明与完整命令](scheduling-validation-v1.1.md)、[日志/原始测量/汇总](../../artifacts/test-results/ds-2026-10-26/)、[最终源码SHA-256](../../artifacts/test-results/ds-2026-10-26/source-manifest.json)。临时pom只隔离dev生产包，全部测试源码仍编译；最终命令明确选择DS测试，默认共享pom不变。
- 按用户明确授权，已向PM/CM发布 [PR #8构建阻塞证据](https://github.com/Rito-492/campus-sched/pull/8#issuecomment-6413505371)；[评论全文](ds-build-blocker-v1.0.md)。其他交接与所有接收确认：**尚未接收**；作者未自审自批。

### 本轮逐项验收状态

下表“模块通过”仅指列出的实际断言；待真实HTTP/端口/页面联调的场景不登记为完整验收通过。

| 编号 | 本轮实际状态与证据 | 尚缺的真实集成与接收人 |
|---|---|---|
| D-AT-04 | **DS模块通过**：adjacentLoadsUsePeakAndSetupOverlapConflicts验证相邻可共存、15分钟布场精确冲突区间；独立评测SCH-05验证边界 | AN实际活动调用链、UI呈现/QA验收 |
| D-AT-05 | **DS模块通过**：错峰预约峰值恰2，不误累加；独立评测SCH-06 | AN首次确认实际HTTP |
| D-AT-06 | **DS模块通过**：conflictAndMissingResourceLeaveNoPartialReservation核对精确缺量、输方无新增预约 | AN复核HTTP错误信封与状态 |
| D-AT-07 | **端口并发通过，整场景待联调**：两个独立事务经栅栏竞争场地恰1成功；跨场地共享设备不超额 | AN真实已审批活动、最终确认HTTP与DS联合运行 |
| D-AT-08 | **部分覆盖**：资源创建/资格复用统一幂等，重复无新增、异体同key为409；原预约保留 | PM+AN确认接口同key同body/异body正式验收，不能用资源接口替代 |
| D-AT-09 | **DS模块通过**：新增演示时间的联合场地/器材冲突；首选同时间换另场地+同类设备×2，代价(0,1,1)，数据库未改 | AN ActivityPort支撑API-24/25；UI方案选择、QA E2E |
| D-AT-13 | **DS端口通过**：replaceExcludesOwnOccupancyAndReleaseIsRepeatable；四资源SCH-01排除自身旧占用 | CM真实改期与AN activatePlan的版本/报名联动 |
| D-AT-18 | **部分覆盖**：实时AT_RISK、故障叠加可用量下限0、原预约保留及影响清单/隐私通过；API-10实际503、成功链路阻塞 | CM EventPort、失败改期仍保留风险；UI风险条 |
| D-AT-22 | **DS独立评测完成，正式验收阻塞**：两环境各160行+300预算20行；完整参考环境6组预期差异如实记录 | QA修复装载；PM/QA裁决四资源fixture或完整资源重构场景；正式入口复跑 |
| N-AT-02 | **资格模块通过**：两身份×四资格×场地/设备，受限403且不写预约；资格HTTP历史原子性通过 | AN/UI直接提交和页面8组资格矩阵；真实活动HTTP尚未到位 |
| N-AT-03 | **模块覆盖**：候选资格过滤/同类替换单测及最终端口持久化身份重查通过 | AN/CM真正强制提交受限目标、最终复核HTTP验证 |
| N-AT-04 | **DS模块通过**：最新身份覆盖旧预览参数；资格写锁等待后拒绝；既有预约不删；历史失败回滚；replace最终重查 | AN提交、CM改期创建/最终确认完整使用最新用户锁与身份 |
| N-AT-06 | **部分覆盖**：资源管理范围撤销后HTTP拒绝；完整活动范围过滤故障清单；查询批准人范围单测通过 | AN完整首次审核、CM复核/停用工作人员确认/撤回重建；不宣称人员流程已验收 |

### 下一接收人与待办

1. **PM冀项阳 / CM杨瀚霖**：处理已发布的默认编译证据，按PR #8既有评审要求集成修复；复跑默认compile/test/verify。不要将隔离结果记为全工程通过。
2. **AN冯玉琪 / CM杨瀚霖**：交ActivityPort/EventPort/真实首次确认、取消、改期编排；按activity→change→user→resource锁序调用SchedulingPort，同事务创建版本后replace，异常向外传播。资源冲突返回时CM需提交CONFLICTED且保留原安排。M2/M3、活动/改期/报名/outbox整体回滚尚待联调。
3. **QA汤雨润 / PM**：修复DbTestSupport装载顺序与正式评测schema绑定，核对活动/版本事务及JSONB；决定策略资源全集，不私改02；按本表正式复验D-AT/N-AT并做交叉评审。
4. **UI杨畅**：对接现有API-07/08/09/11/40；API-10/24/25等待真实端口。保留truncated、隐私占位和故障风险说明；页面验收本轮未执行。

## 历史交付（2026-10-21：PR #9 隐私评审修复）

- 成员：黄方勇 55230315；任务包 v3.0；DS-02/03。本人分支 `work/55230315-scheduling`，修复前 HEAD `622d6b8`，fetch 后 `origin/main=4fbda0ecf4f155ef1e05c385a677ebe7d13b087d`，已包含在本人分支，无新增 main 提交需要合并，开工时工作区干净。
- 反馈：[PR #9 的 Rito-492 评审](https://github.com/Rito-492/campus-sched/pull/9#pullrequestreview-5587757375) 为 CHANGES_REQUESTED：API-11 泄露未授权活动信息、API-09 对遮蔽活动仍返回真实风险。该反馈已接收；修复是否放行仍待交叉复审，作者不自批。
- 修改 `ResourceService`：故障清单复用现有活动查看策略逐项过滤，使用最新 Actor；API-10 返回值也采用同一过滤。内部故障事件独立使用完整影响清单，防止 HTTP 脱敏遗漏通知。遮蔽日历的风险固定为 NORMAL 占位，不查询真实活动风险；有权活动仍返回实时风险，FAULT 仍为 AT_RISK。
- 修改 `ResourceSchedulingIT`：新增故障 HTTP 权限/范围/权限撤销回归、内部事件不受响应裁剪影响的测试、真实数据库列表/日历稳定排序测试；补齐日历风险及有权访问断言。测试替身仅记录 EventPort 调用，不声称 CM 事件持久化通过。缺四项环境变量在创建 schema 前明确报阻塞，不跳过真库测试。
- 真实结果：新增/加强的三项隐私用例在旧生产代码上均失败；修复后隔离构建 **23 项单测、28 项真库 IT（20 项 ResourceSchedulingIT + 8 项 SchemaIT）通过，0 失败/错误/跳过**。标准 test 与 verify 仍失败于 CM `DemoDataSeeder.java:118`，未修改他人模块或共享 pom。排序 SQL 原本符合 createdAt DESC、id ASC，本次以回归测试确认，无需更改 SQL。
- 证据和完整命令：[评审修复说明](scheduling-review-fix-v1.0.md)、[本轮证据](../../artifacts/test-results/ds-2026-10-21/)。对应 D-AT-21 隐私及 N-AT-06 范围的模块回归；不代表正式全链路验收通过。
- 下一接收人：PM/QA 复审两个必须改项；UI 对 activityId=null 的“已占用”条目不得将 NORMAL 占位解释为该活动安全可用；CM 修复默认编译并交 EventPort，AN 交 ActivityPort 后再完成真实集成。既有 AN 状态机/QA 装载问题本轮未复跑，详见历史记录，不将旧结果当本轮结果。

## 历史交付（2026-10-20：DS-02～05 持久化实现与策略评测）

- 成员：黄方勇 55230315；任务包 v3.0。本人分支 `work/55230315-scheduling` 已从 `287673a` 快进同步 `origin/main=4fbda0ecf4f155ef1e05c385a677ebe7d13b087d`，交付前再次 fetch，main 未变化。代码提交 `91d3063`；保持既有 Git 身份，无他人未提交修改。
- 保留已合并的 V1 22 表迁移及 TimeInterval、CapacitySweep、CandidateRanking 逻辑；V1、公共 DTO/端口、其他成员生产代码没有修改。CandidateRanking 仅更新过时注释；SchemaIT 修复真实执行暴露的测试装载问题。
- 交付类：
  - `scheduling/StrategyEvaluator`、`StrategyResult`：签名与 common/02 §8 一致，FIRST_FIT/RANKED 共用候选空间和约束；计时包含读取/生成/检查/排序，使用 `System.nanoTime`。
  - `scheduling/CandidateSearch`、`ConstraintChecker`：资格过滤、9 个偏移、同类设备单项替换、规范化/去重/稳定排序、最多检查 300 个完整组合、最多 5 项、truncated；复用原有区间与峰值算法。
  - `scheduling/SchedulingPortAdapter`：真实资源/故障/预约查询、最新持久化所有者身份、事务内 UUID 有序锁定新旧资源、成功检查凭据的事务隔离、原子替换/释放、实时风险。
  - `resource/ResourceRepository`、`ResourceService`、`ResourceController`：资源创建及 AccountPort 授权、资格前后历史、故障与影响清单、日历三段和故障脱敏。写接口复用 CommandExecutor、Responses、认证及幂等。
  - `scheduling/ScheduleQueryService`、`ScheduleController`：独立预览编排，避免 ActivityPort 与 SchedulingPort 循环依赖。
- API 交付状态：API-07/08/09/11/40 已有真实实现；API-24/25 代码已接端口但运行依赖 AN 的 ActivityPort；API-10 代码已完成但成功提交依赖 CM 的 EventPort。缺少依赖明确返回 503，不提供生产替身、不伪造成功，故障不留部分写入。
- 实际验证：Java 21.0.6、Maven Wrapper 3.9.11、Spring Boot 3.5.16、PostgreSQL 17.6；本机 `127.0.0.1:5440/campus_test`，每类随机 `t_<UUID>` schema，测试后清理并停止本轮便携实例。
  - **DS 隔离验证通过**：23 项单测（原有 13 项、新增策略 6 项、查询权限 4 项）；25 项真库 IT（ResourceSchedulingIT 17 项、SchemaIT 8 项），失败/错误/跳过均为 0。
  - 资格矩阵、峰值与端点、场地/设备并发恰一成功、无部分预约、最终身份重查、与资格更新争同一行锁、预约中途 SQL 异常回滚、资格不撤销既有预约、实时风险、日历脱敏/截断、HTTP 幂等和授权范围均有实际断言。事件异常测试替身仅在测试中抛错，验证故障回滚；不宣称 CM 事件链完成。
  - **300 候选真实评测通过**：无解预算场景，两策略各预热 1 次、实测 10 次，共 20 条原始结果；每条 `evaluatedCount=300`、`feasible=false`、`choice=null`。FIRST_FIT 中位数 126.59 ms，RANKED 125.01 ms，仅为这一个场景，不推导全局性能结论。
  - **默认构建失败**：最新 main 的 `dev/DemoDataSeeder.java:118` 未处理受检异常。Windows Wrapper 在获准访问构建缓存后也复现同一错误；早期沙箱中的 Wrapper 路径错误不是项目构建通过。CM 修复位于 `origin/fix/demodataseeder-118`（顶端 `ee76c2a`），尚未合入 main。
  - **首次隔离全部单测失败**：48 项中 1 项失败，为 AN 的 `ActivityStateMachineTest.rejectionReturnsToDraftAndWithdrawalCreatesANewVersion:49`。本轮未修改 AN 实现或断言。
  - **QA 八场景评测失败**：实际调用 SchedulingBenchmarkIT，在 main 的 `DbTestSupport` 装载参考数据时因先插 `user_resource_scope`、后建资源触发 `fk_user_resource_scope_resource`，尚未产生八场景测量行。QA 修复 `0bb9aac` 已在成员远端分支，但未合入 main；没有编造 `results.csv`。
- 精确命令、临时隔离构建边界、文件哈希及原始日志见 [实现与验证说明](scheduling-implementation-v1.0.md) 和 [证据目录](../../artifacts/test-results/ds-2026-10-20/)。隔离构建仅在被忽略的 target 下复制 pom、排除 CM dev 包，仍编译全部测试源码；没有修改共享 pom 或默认测试入口。这不是全工程/M2/M3/E2E 通过。
- 未完成项及下一接收人：
  - **冀项阳（PM）/杨瀚霖（CM）**：通过交叉评审合入 CM 编译修复，再复跑默认 test/verify；本轮不替其他岗位合并分支。
  - **冯玉琪（AN）**：交 ActivityPortAdapter 与首次确认/取消编排；调用 SchedulingPort 前持有活动锁及按 UUID 排序的操作者/所有者锁；修复上述既有状态机测试。
  - **杨瀚霖（CM）**：交 EventPort、改期真实编排；接入已实现的 lockAndCheck/replace/release/risk，补活动版本/改期/outbox 整体回滚验收。
  - **汤雨润（QA）**：交叉评审本 PR，合入测试装载修复后运行八场景评测及 D-AT/N-AT；本模块数据验证不能替代正式跨模块验收。
  - **杨畅（UI）**：API-07/08/09/11/40 可接入；API-10/24/25 需上述端口到位，日历保留 truncated 和脱敏字段。
- 已推送本人分支并创建 [草稿 PR #9](https://github.com/Rito-492/campus-sched/pull/9)，用于交叉评审与跟踪上述集成依赖。接收确认：尚未接收；作者不自审、不自批，不直接合并 main。

---

## 历史交付（2026-10-19：DS-03/05 纯逻辑；DS-02～05 接入依赖核对）

- 成员：黄方勇 55230315；基线 v3.0；实际 `origin/main=68158cf`。本人 `work/55230315-scheduling` 开始时干净，由 `bbeb984` 快进同步到 main，未 reset、未推送、未创建 PR、未向 main 合并。
- 新增 scheduling 包 `TimeInterval.java`、`CapacitySweep.java`、`CandidateRanking.java` 及对应三个 `*Test.java`。实现半开区间/布撤场/上海日/开放时间、设备同时峰值与故障下限、候选代价及规范设备排序；不改 V1 schema/SchemaIT、公共 contract 或他人文件。
- Java 21.0.6 `javac --release 21` 编译退出码 0；通过本机缓存 JUnit Jupiter 5.10.2 引擎直接运行，13 用例、0 失败、0 跳过，退出码 0。包含 200 组随机区间对照；完整命令、临时引擎启动器及输出见 [检查证据](scheduling-check-v1.0.md)。这不是 Maven 全工程或产品验收通过。
- `./scripts/check.ps1 -Scope scheduling` 退出码 1：缺 PM pom/Wrapper；`./server/mvnw.cmd -f server/pom.xml test` 退出码 1：命令不存在。`git diff --check` 退出码 0。
- 未执行数据库/SchemaIT/并发/回滚/HTTP/E2E/策略评测；30 个验收场景未执行。本轮不重复旧数据库烟测，不将旧 handoff 的历史结果当作新结果。
- DS-02 资源资格及接口、DS-03 日历/完整 check、DS-04 SchedulingPort 事务实现、DS-05 完整候选生成及 StrategyEvaluator/StrategyResult 仍待公共工程/DTO。纯逻辑不定义替身，也不返回伪成功。
- [接入清单](scheduling-progress-v1.0.md) 已一次列清 PM 所需文件/类型/签名、影响任务以及 AN/CM/QA 的交接点。实际 main 已有 CM Compose/check/load-env，旧记录中“脚本缺失”已不适用；本轮未验证数据库运行状态。
- 下一接收人：PM 冀项阳交公共 contract/构建/认证与事务入口；DS 随后接真实资源、候选及端口，AN/CM 接预约端口，QA 交叉验证。接收确认：尚未接收。用户随后授权提交、推送本人分支并创建 PR；交叉评审尚未进行，不自审自批，不自动合并或联系其他成员。

---

## 本次交付（2026-10-13 第二批：DS-01 升级 v3.0——22 表 schema + 烟测 + SchemaIT + 文档）

- 成员、学号、任务ID：黄方勇 55230315 / DS-01（v3.0 契约升级）
- 基线：v3.0；基准 commit/包版本：main `c637653`（已合并入本人分支）；本人分支 `work/55230315-scheduling`
- 升级依据与判定：转述任务与 main 的 v3.0 契约逐字核对一致（02 §2/§4/§8、01 契约 §2 三角色与资格、QA 装载器列名）；V1 首形态（17 表）从未合并、未进基线，**原地升级合法，无 V2 补丁**（判定记录见 migration-register.md）
- 交付文件与接口：
  - `server/src/main/resources/db/migration/V1__schema.sql`——22 表（新增 user_permission/user_approval_scope/user_resource_scope/authorization_change/resource_eligibility_change；app_user.identity NOT NULL；resource.allow_teacher/allow_student；teacher_decision_json→approver_decision_json；PENDING_TEACHER→PENDING_APPROVER 全链路枚举替换）
  - `server/src/test/java/com/campus/events/resource/SchemaIT.java`——v3.0 口径 8 用例（tc01 22 表齐全…tc08 更名核验）
  - `docs/design/database-design.md`（v3.0：ER 图 22 实体、三角色/身份/资格表结构表、更严实现清单）
  - `docs/design/system-design.md`（状态机 PENDING_APPROVER、三角色/资格规则、AccountPort）
  - `docs/design/migration-register.md`（v3.0 版本决策记录 + 20 项烟测覆盖清单）
- 实际运行命令与结果：
  - ✅ **v3.0 psql 等价烟测**（PostgreSQL 17 docker postgres:17；campus_test 独立 schema；`psql -1 -v ON_ERROR_STOP=1` 单事务）：**20 项断言全部通过**（退出码 0），含 22 表齐全、新表授权数据链路、身份/角色/权限枚举、更名核验（teacher_decision_json=0）、延迟外键正负路径、3 触发器、资格/身份同事务变更终值核验
  - ✅ 延迟外键负路径（提交期校验）单独复核通过（首次形态与 v3.0 两轮均通过）
  - ⬜ **未执行**：`./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchemaIT" verify`（阻塞于 PM 基础工程 pom/mvnw/contract 包；SchemaIT 已就绪 v3.0 口径）
  - ⬜ **未创建**：`StrategyEvaluator`/`StrategyResult`（02 §8 v3.0 签名含 `Identity`）——阻塞于 PM `contract` 包（Plan/Alternative/Identity 类型），按规程替身只允许进测试代码，生产口径归 DS-05 交付
  - 环境：Docker Desktop + postgres:17 容器（ds-local-postgres，宿主 5432）；`.env` 白名单键就绪（`.env` 不入库）；测试 schema 用后即清，campus_test/campus_dev 无残留污染
- 验收编号与证据路径：DS-01 验收项已在上列烟测中达成（35 条断言的断言清单见 migration-register.md）；暂无 D-AT 通道项
- 未完成项/外部依赖及负责人：
  1. `server/pom.xml` + `contract` 包（Plan/Alternative/Conflicts/Identity…）→ **jxy 55230223（PM）**——SchemaIT 编译、StrategyEvaluator 生产代码、DS-02/03/04 全链依赖
  2. `scripts/load-env.ps1`/`check.ps1` → **yhl 55231031（CM）**；`.env` 拼写差异（TEST_DATABASE_USERNAME vs TEST_DB_USERNAME）待 PM/CM 裁决（DbTestSupport 实读前者）
  3. `demo.json` 及装载器与 V1 已对齐（QA 装载器列名逐字段核对通过）；QA 首跑 DbTestSupport 迁移可用本人容器或自建环境
- 下一接收人及其动作：**评审人员（QA/PM 择一交叉）**——按 pr-review-checklist 交叉评审本 PR；**jxy（PM）**——contract 包落地后知会跑 SchemaIT 与 StrategyEvaluator
- 接收确认：尚未接收（仅真实收到反馈后更新）

---

## 历史交付（2026-10-13 第一批：DS-01 首形态 17 表）

- 成员、学号、任务ID：黄方勇 55230315 / DS-01（v2.0 契约口径）
- 基线：v2.0→v3.0 过渡期；基准 commit：本分支 `f6bd329`（基线 main `b994ad3`）
- 交付文件与接口：V1__schema.sql（17 表首形态）、SchemaIT.java（6 用例）、system-design/database-design/migration-register 初版
- 实际运行命令与结果：✅ psql 等价烟测 14 项断言全部通过（PostgreSQL 17，campus_test 独立 schema）；⬜ Maven verify（阻塞 PM pom）
- 后续：首形态从未进基线，已被本批 v3.0 形态**原地升级取代**（见本批）；验证记录保留于 migration-register.md 供追溯。

## 2026-10-28 任务4软件设计文档交付

- 成员：黄方勇 55230315；采用开发任务包 v3.0，对应学习通任务4。
- 正式工件：software-design-task4.docx、software-design-task4.pdf、software-design-task4.md；按用户要求仅保留一套正式文件，不含编辑版本记录。
- 内容：20 个用例、88 条备选/异常分支、45 张图（其中 41 张顺序图），包含子系统、类操作、页面边界及需求与测试追踪。
- 实际检查：Microsoft Word 更新目录并导出 PDF；61 页、45 个目录项页码核对通过，已完成渲染和视觉检查。未运行产品测试，不新增 D-AT/N-AT 通过结论。
- 本次提交范围仅为以上文档与本条交接记录；先前代码、测试及其交接改动不在本次提交内。
- 下一接收人：项目经理及交叉评审成员；学习通截止 2026-10-31 23:59，尚未上传学习通，接收确认尚未取得。

## 2026-10-29 任务4 PR #12 评审修复

- 针对 Rito-492 对 cca5652 的两项阻塞意见：移除 UC-04 页面直连 SchedulingPort，资格限制改由后续 API-16 提交场景中的 ActivityService 调用内部端口检查；资格管理仍允许双关闭并保留既有预约。
- 全部用例顺序图补充 HTTP 适配边界，服务结果经响应返回；render、clearSession、selectPlan 均为页面本地操作。HttpController 明确为职责抽象，补齐 handle 和 ChangeDetailPage.render 的操作目录，不新增产品接口。
- 同步更新正式 Markdown、Word 与 PDF。实际核验：20 个用例、88 条分支、41 张顺序图与嵌入图逐张一致、45 张总图、635 条消息边界检查、385 条调用操作目录检查通过；61 页、45 个目录项页码核对通过，完成全部页面渲染及视觉检查。
- 本轮只修正文档，未运行产品测试，未宣称交叉评审通过；交由原评审人复审，尚未合并 main。
