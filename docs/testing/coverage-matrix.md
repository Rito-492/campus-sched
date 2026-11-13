# 30 场景覆盖矩阵 v3.0

> 归属：测试经理（55230316）。依据 `agents/common/03-验收与演示数据.md` §2（D-AT-01～24 + N-AT-01～06）。
> 用途：QA-02/03 的测试改造对照表与 QA-05 覆盖判定的执行台账（判定规则见 `test-plan.md` §4.1）。
> **执行状态初始全部「未执行」**；每轮执行后更新状态列并附证据路径。主责列指模块自测负责人，QA 列为跨模块验收落点。

| 编号 | 场景摘要 | 需求关联 | 主责 | QA 执行落点 | 状态（2026-11-12 最新main复跑与评审更正） |
|---|---|---|---|---|---|
| D-AT-01 | 登录/错密码/停用后旧令牌 401；响应无 passwordHash | FR-01 | PM | PermissionsIT-01 + login.spec | ✅ 通过：PermissionsIT登录/错密码/停用旧令牌三项真实PG通过；UI登录与三视口页面11-10复跑15/15通过 |
| D-AT-02 | approver_other/account_admin/applicant_b 越权均 403 无变化 | FR-02 | PM+AN | PermissionsIT（TC-02组） | ✅ 通过：PermissionsIT三类越权403且状态/版本/预约不变断言均通过 |
| D-AT-03 | 新建→编辑→提交→批准→资源确认→发布；首确前 0 ACTIVE | FR-03 | AN | AN自测 + `booking.spec.ts` | ✅ 通过：真实浏览器新建→编辑→提交→批准→资源确认→发布→报名全链通过（booking.spec 11-10），首确前0 ACTIVE与名单计数已断言 |
| D-AT-04 | 相邻占用可共存；布场 15 分钟产生边界冲突 | FR-04 | DS | DS自测 + Bench(SCH-05) | ✅ 通过（ResourceSchedulingIT真实PG模块断言） |
| D-AT-05 | 错峰占用峰值恰 2，不得误算 3 | FR-05 | DS | DS自测 + Bench(SCH-06) | ✅ 通过（ResourceSchedulingIT真实PG模块断言） |
| D-AT-06 | 缺件冲突指出 [11,12) 缺 1，无部分占用 | FR-06 | DS | DS自测 | ✅ 通过（ResourceSchedulingIT真实PG模块断言） |
| D-AT-07 | 两批准活动抢同资源，恰 1 成功 1 冲突，无部分预约 | FR-07 | DS+AN | BookingConcurrencyIT | ✅ 通过：BookingConcurrencyIT 1/1真实HTTP+库断言通过（AN空意见修复后），恰1成功1冲突、无部分预约 |
| D-AT-08 | 幂等：同 key 同 body 重放 / 同 key 不同 body 409 | FR-08 | PM | PermissionsIT-08 | ✅ 通过：PermissionsIT幂等两项通过（同key重放、同key不同body 409），回执与预约计数已断言 |
| D-AT-09 | 改期预览两类冲突 + 首选 3102+3202×2 代价 (0,1,1) | FR-09 | DS+UI | DS自测 + conflict.spec | ✅ 通过：TC-09-2真实UI 11-10通过（两类冲突、当前排序首选=3102+3202×2同时间15:00、预览不写库）；DS真库同时间替代断言通过 |
| D-AT-10 | 改期确认链 staff→approver→resource，version2 原子切换 | FR-10 | CM+AN | `change-happy-path.spec.ts` + ChangeRollbackIT 前置 | ✅ 通过：真实浏览器预览选候选→staff/approver/resource→APPLIED/version2，3102+15:00及学生通知、reload通过；11-13清除TC-12-1抢占占用后，默认全量29/29通过；真库原子切换/回滚通过 |
| D-AT-11 | 工作人员/批准人拒绝 → REJECTED 原安排完整 | FR-11 | CM | ChangeRollbackIT-11 | ✅ 通过：ChangeRollbackIT工作人员/批准人拒绝两项真HTTP+库断言通过，原版本预约不变 |
| D-AT-12 | 预览后被抢占 → CONFLICTED，原安排保留 | FR-12 | CM+DS | ChangeRollbackIT-12 + conflict.spec | ✅ 通过：ChangeRollbackIT抢占后最终409 RESOURCE_CONFLICT/CONFLICTED、原安排保留，5/5全通过（11-10） |
| D-AT-13 | 自身旧占用排除，不自竞争 | FR-13 | DS+CM | DS自测 + Bench(SCH-01) | ✅ 通过：真实改期schedule-preview排除自身及无写入断言通过；DS模块同样通过 |
| D-AT-14 | 预约替换中注入异常整体回滚 | FR-14 | CM | ChangeRollbackIT-14 | ✅ 通过：真实端口事务中replaceReservations注入异常，版本/预约/outbox回滚，原幂等键重试APPLIED/version2 |
| D-AT-15 | 改期期间报名冻结 CHANGE_PENDING | FR-15 | AN+CM | RegistrationCapacityIT-15 | ✅ 通过：真实创建改期冻结JOIN/WITHDRAW为CHANGE_PENDING，拒绝终态后恢复报名 |
| D-AT-16 | 容量 1 对 20 并发 JOIN 恰 1 ACTIVE | FR-16 | AN | RegistrationCapacityIT-16 | ✅ 通过：真库4/4，20并发JOIN恰1成功/19 CAPACITY_FULL，重复不增人数、退出后他人补入 |
| D-AT-17 | 取消两次不多释放；取消与改期竞争一致终态 | FR-17 | AN+CM | CancellationRaceIT | ✅ 通过：CancellationRaceIT 2/2；合法重复取消返回当前结果，无额外释放/事件/版本，取消与改期竞争终态一致 |
| D-AT-18 | 故障 → AT_RISK，页面不谎报原安排可用 | FR-18 | DS+UI | DS自测 + conflict.spec | ✅ 通过：TC-18-1真实UI 11-10通过（risk-banner可见且不被谎报为正常），DS真库风险/故障断言通过 |
| D-AT-19 | 通知消费失败恢复 + 重复消费去重 | FR-19 | CM | NotificationConsistencyIT | ✅ 通过：真实生产消费者接入测试故障探针，首次失败后恢复、同事件去重、已读不推进确认均通过 |
| D-AT-20 | 旧 baseVersion/lockVersion → STALE_VERSION | FR-20 | AN+CM | PermissionsIT-20 | 🟨 部分：409 STALE_VERSION、完整错误信封、无新版本写入已真实验证；但details={}未含当前版本/锁版本/状态，违反02 §3，待CM修复（DEF-QA-2026-11） |
| D-AT-21 | 日历脱敏/名单与他人通知 403 | FR-21 | 全员 | PermissionsIT-21 + permissions.spec | ✅ 通过：PermissionsIT隐私子项本轮通过（12/13，唯一失败属D-AT-20）；11-01真实浏览器permissions4/4通过 |
| D-AT-22 | first-fit vs RANKED 300 候选对比 | NFR-评测 | DS+测试 | SchedulingBenchmarkIT + evaluate | ✅ 通过：最新真库Benchmark 1/1，evaluate exit0，160测量行、16组expected全一致；四资源基准范围 |
| D-AT-23 | 刷新/重启后状态来自真实持久化 | FR-23 | UI+配置 | `change-happy-path.spec.ts`（reload 断言） | ✅ 通过：11-13默认全量29/29（含reload后version2/3102/15:00）；11-01关闭seed重启后API与新浏览器状态一致（重启证据为11-01版本） |
| D-AT-24 | 干净环境按发布说明启动换角色答辩 | FR-24 | 全员 | 发布演练（check.ps1 + 人工） | ⬜ 未执行：本机Docker仅29.9.3、缺compose插件与docker-compose；仅静态解析compose/compose.qa服务结构，未启动干净环境，不记通过 |
| N-AT-01 | 身份与角色分离：教师申请人/学生申请人/仅账号管理员调用审批复核 | 身份规则 | PM+AN | IdentityEligibilityIT.nat01 | ✅ 通过：IdentityEligibilityIT 6/6全通过；教师/学生/账号管理员不自动有审批复核权，状态与记录不变 |
| N-AT-02 | 两类身份×四种资源资格（3111-3114/3211-3214），UI+HTTP 双通道 | 资格规则 | DS+UI | IdentityEligibilityIT.nat02 + conflict.spec | 🟨 部分：两类身份×四种资源资格的直接HTTP八组合真库全部通过；UI 仅教师专用组一组证据，其余组合未逐组从UI提交，未达契约§6「UI+HTTP双通道」全覆盖 |
| N-AT-03 | 候选过滤受限资源；强制提交仍拒绝；按所有者身份校验 | 资格规则 | DS | IdentityEligibilityIT.nat03 + Bench | ✅ 通过：候选过滤+强制受限提交403/无新增改期真库通过，按所有者身份 |
| N-AT-04 | 提交后改身份/资格：未确认用最新设置；旧预约保留；新目标不合资格拒绝 | 资格规则 | AN+CM | IdentityEligibilityIT.nat04 | ✅ 通过：身份变更后用最新设置、旧预约保留、新目标不合资格拒绝且原安排不变，真库通过 |
| N-AT-05 | 所有者兼任 APPROVER/RESOURCE_CONFIRM 对本人申请 4 种审核全拒 | 回避规则 | PM+AN | IdentityEligibilityIT.nat05 | ✅ 通过：四种自审回避全部403且无成功变化，独立授权人员正常执行（含改期） |
| N-AT-06 | 审批范围/管理范围不全覆盖拒绝；停用工作人员拒绝不代签 | 范围规则 | PM+CM | IdentityEligibilityIT.nat06 | ✅ 通过：组织/类别范围、资源范围不全403；停用工作人员被拒且待确认快照不变、不代签，撤回重建通过 |

## 2026-11-13 复审清理与浏览器全量复跑

- 按复审要求：TC-12-1 抢占助手返回活动 ID，用例结束由该活动所有者调用 API-21 取消并核对 `CANCELLED`；保留抢占与最终 409 断言。默认 Playwright 固定 `workers: 1`，避免共享 demo 活动 A 的跨文件并发污染。
- 真实后端/前端 + 全新 PostgreSQL：`npm --prefix qa run test:e2e` **29/29 通过**（42.5s）；`change-conflict + change-happy-path` 同序 6/6 通过。并发对照为 26/29（3 例状态竞争），已由串行配置消除。
- 后端默认 verify 仍为 96/96 单测、158 IT=156 通过/1 失败（D-AT-20 details，DEF-11）/1 跳过；本轮未改服务端代码。
- 证据：`artifacts/test-results/qa-2026-11-13/`。

## 2026-11-12 最新main复跑与评审更正（历史）

- main=`db01829`（PR #20 已合入），QA 普通合并 `6a1785c`；#21（CM 改期修复）仍被评审阻塞，未合入。
- 默认 verify：Surefire 96/96；Failsafe 158=156 通过、1 失败、0 错误、1 跳过；唯一失败 `PermissionsIT.tc2001And02` 的 `details={}`（DEF-11），同一用例内两种陈旧版本请求各报 1 处失败。该用例已改为先发出两种陈旧版本请求、再用 `assertAll` 分别汇总，两个分支都真实执行并分别报告失败。
- 按评审更正 **N-AT-02 为部分覆盖**：直接 HTTP 八组合通过，但 UI 仅教师专用组一组证据，未满足契约 §6 的 UI+HTTP 双通道；同步报告、交接与 Ts。
- 30 项：**27 通过、2 部分（D-AT-20、N-AT-02）、0 阻塞、1 未执行（D-AT-24）**；Tx=29/30=96.7%，Ts=27/30=90.0%。未执行项仍为 Compose 环境缺失，不记通过。
- 证据：`artifacts/test-results/qa-2026-11-12/`。

## 2026-11-10 全量复跑（历史）

- main=`ca162bf`（含#22 AN空意见修复，#21 CM改期修复仍未合入）；QA普通合并提交`f06482a`。
- JDK21真实PostgreSQL默认verify：Surefire **77/77通过**；Failsafe **153项=151通过、1失败、0错误、1显式跳过**，exit1。唯一失败为PermissionsIT陈旧版本details为空（DEF-QA-2026-11，CM）；跳过为ResourceSchedulingIT既有显式项，不记通过。
- 缺陷状态：DEF-QA-2026-08（AN空意见）关闭（ContractBoundaryIT用例与全量审批链实测通过）；DEF-QA-2026-09（AN测试端口Bean冲突）关闭（ActivityRegistrationIT 35/35通过）；DEF-10/11仍待CM。
- 真实浏览器（最新main独立全新数据库campus_e2e_20261110）：TC-09-2与TC-18-1修正定位后2/2通过（修正前同一代码2/2失败，证明定位问题真实存在）；booking.spec 1/1通过；change-happy-path 1/1通过。
- 契约40 API/9枚举/15 DTO、QA typecheck通过；评测160行、16组expected全一致。
- 30项：**27通过、2部分（D-AT-20、N-AT-02）、0阻塞、1未执行（D-AT-24）**；Tx=29/30=96.7%，Ts=27/30=90.0%。D-AT-24未执行（Compose不可用），故不计入通过。通过项不推导M0–M4或发布整体放行。
- 证据：`artifacts/test-results/qa-2026-11-10/`。

## 2026-11-01 全模块合并后验收（历史）


- 最新main=`b8be7e4`，QA正常merge=`253c230`加本轮补丁；JDK21.0.13 / PostgreSQL17.11真实默认verify：77/77单测，112 IT=101通过/9失败/1错误/1跳过，exit1。AN模块33项因上下文冲突只记1 error，不是被删除或通过。
- 30项当前统计：**18通过、10部分、2阻塞、0未执行、0失败**；Tx=28/30=93.3%，Ts=18/30=60.0%。目标断言前置失败仍记部分/阻塞；9个测试失败不等于9个独立验收场景失败。通过项不推导产品或发布全绿。
- 真实UI：登录+三视口15/15；后端关闭重新播种重启后，权限+页面16/16。两批布局重复，不相加为独立验收场景；随后完整UI改期主线1/1通过，version2同库后端重启及新浏览器reload验证通过，D-AT-10/23提升通过。
- 原CM缺端口/UI工程缺失已解除。剩余AN首次审批空意见400（DEF-08）、模块替身与生产Bean冲突（DEF-09）；Node22/完整E2E/干净发布仍待验收。
- 完整说明、命令、证据、预审边界见 [本轮集成报告](pr16-integration-2026-11-01.md)。本表之后的10月记录均为历史快照。

## 2026-10-31 PR #15 合并后续验（历史）

- PR #15 已合并，main=`c4f3d413fa1214d127bfff1b3cca0e1ed4b68ddd`；QA普通merge提交=`54d976dfe3c34eaede14391a77deaba17673bbe5`，无冲突，未reset、未强推；原提交和未跟踪证据保留。
- 默认 `./server/mvnw -f server/pom.xml verify` 已完成：Surefire 70/70；Failsafe 132项=105通过、24失败、2错误、1跳过，exit 1。ResourceSchedulingIT 30项=29通过/1跳过；DsStrategyBenchmarkIT 2/2；SchedulingBenchmarkIT 1/1，evaluate exit 0，160测量行、16组expected全匹配；SeederIT 3/3、SeederRollbackIT 1/1、DbTestSupportIT 1/1。与PR15前相比增加11通过及1跳过，24失败/2错误仍由CM生产端口缺失的503/404及其级联导致；D-AT-16仍为20/20 JOIN返回503。
- 真库新增直接证据：ResourceSchedulingIT 的计划可变列表保护、跨事务凭据隔离、缺失ChangePort时503且无部分写入通过。真实ActivityPort+SchedulingPort成功切换/回滚测试因CM ChangePort缺失明确skipped，不计通过。
- D-AT-09由未执行改为部分：模块真实PG断言两类冲突、同时间替代场地及设备×2、代价(0,1,1)、预览不写库通过；独立UUID数据不冒充固定3102/3202演示，UI仍未执行。
- 最新30项统计：4通过、18部分、7阻塞、1未执行（D-AT-23）、0失败；Tx=22/30=73.3%，Ts=4/30=13.3%。这是分层覆盖台账，不表示全量Maven或产品验收通过。
- 原始结果归档：`artifacts/test-results/qa-2026-10-31/post-pr15-merge/`；下节保留PR15前历史执行快照，不代表最新基准。

## 2026-10-31 执行证据与统计（PR15前历史）

- main基线：`feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`；完整verify代码commit=`514b8dc`；D-AT-22参考数据哈希追溯与目标复验commit=`c3a4a2c6418b6ed4859c64a1b5da9b1b9fd82f6f`；改动已同步到Draft PR #16。
- 默认命令：`./server/mvnw -f server/pom.xml verify`；最终复验commit=`514b8dc`；PostgreSQL 17隔离容器，`campus_test`，测试schema按类生成；DB密码和`DEMO_PASSWORD`运行时随机注入且未入代码/日志。容器已按名称、标签、镜像校验后停止移除。
- 结果：Surefire 70/70通过；Failsafe 120项，94通过、24失败、2错误、0跳过，`verify`退出1。SeederIT 3项、QA新增SeederRollbackIT 1项、DbTestSupportIT 1项、DS ResourceSchedulingIT 20项、SchemaIT 8项、FoundationIT 21项、AN ActivityRegistrationIT 33项通过；ActivityRegistrationIT 的CM端口用测试替身，只算部分覆盖。
- D-AT-22：commit `514b8dc` 全量真PG Failsafe中的SchedulingBenchmarkIT 1/1通过，生成160行；reference/scenario路径及SHA-256写入JSON；`npm --prefix qa run evaluate` exit 0，8场景×2策略全部expected一致。评测reference仅3101/3102/3201/3202，未改expected；旧demo错差源于N-AT扩展资源进入CandidateSearch，原始对照留作历史证据。
- D-AT-21：commit `514b8dc`下真实PG `tc2101_calendarRedactsUnauthorizedActivityTitles`通过；用例修正`+08:00`编码并通过绝对URI发送，避免RestTemplate模板二次处理；PermissionsIT其余端口流程仍有7个503；报告见`artifacts/test-results/qa-2026-10-31/d-at21-uri-object-rerun/TEST-com.campus.events.acceptance.PermissionsIT.xml`。
- 统计：RfT=30；Tx=21（4通过、17部分、0失败），7项阻塞于缺失生产端口或交付，2项未执行（D-AT-09、D-AT-23）；Ts=4。执行覆盖率Tx/RfT=70%，完全成功率Ts/RfT=13.3%。阻塞不计Tx，模块级通过不推导M0-M4完成。
- 完整全量verify报告：`artifacts/test-results/qa-2026-10-31/final-after-calendar-encoding/`；带SHA-256的Benchmark复验：`artifacts/test-results/qa-2026-10-31/benchmark-hash-rerun/`、`artifacts/evaluation/summary.md`、`artifacts/evaluation/results.csv`；旧demo参考集mismatch备份：`artifacts/test-results/qa-2026-10-31/benchmark-reference-rerun/evaluation-prior-mismatch/`。

## 需求追溯说明（QA-05 交付给 AN 的主表输入）

- 本矩阵覆盖 `docs/requirements/` 规约中的 FR-01～25 与 NFR-01～30 中**可测试项**；纯文案类 FR 依任务书 §3「文案修改不强制镜像测试」标注 N/A。
- FR/NFR 全量对照以 `docs/requirements/traceability.md` 为主表（AN 维护），本矩阵提供测试侧执行状态回填。
- v2.0 旧映射（test-design.md §0 表）中 teacher/sys_admin 等旧名已废弃；改造完成后并入本矩阵，test-design.md 保留用例细节作为断言依据。

## 统计口径（03 契约 + 课程课件 6.2）

- 已执行覆盖 = Tx / RfT（Tx=已执行用例数，RfT=用例总数）；成功覆盖 = Ts / RfT（Ts=完全成功无缺陷数）。
- 每个测试需求至少一正一负两个用例（课件 5.1）；正负面配对情况在各 IT 的 @DisplayName 标注。