# PR #16 最新集成验收与评审交付（2026-11-01）

> 测试经理：汤雨润（55230316）；QA-02～05。本文是实际运行快照，不是全产品验收通过声明。技术预审与团队 GitHub Approve 分开记录，不自审、不直接推 main。

## 1. 安全同步与范围

- 已确认 PR #13/#14/#15/#17/#18 均已合并；本轮 main 基准 `b8be7e48c227627c2c6e24aee534cf727200faf6`。
- 在 `work/55230316-qa` 正常 merge，源码基准 `253c23075ec95905f9dea52775d5d68f6730145e`。备份分支 `backup/qa-before-all-prs-20261101` 保留原 `e1e175c`。无 reset、无强推、无冲突；原未跟踪材料保留。
- 继续使用 PR #16 的远端分支 `work/55230316-qa-feb0d70`；旧固定远端 `work/55230316-qa` 保持 `0bb9aac`。
- 本轮只修改 QA 的 acceptance/support、qa、测试文档与证据，不修改 AN/CM/UI 生产实现、不修改公共契约与期望值。
- 此前“CM 端口与 UI 未交付”的结论已过时：生产 EventPort、ChangePort、改期、通知以及 Vue 前端均已接收。下面记录合并后真正剩余问题。

## 2. 环境与命令

- Linux/WSL2；PostgreSQL 17.11 独立 Docker 容器 `campus-qa-pr16-20261101`，仅绑定本机，数据库 `campus_test`；验收按类独立 schema。
- 首轮使用 JDK27，随后从 Adoptium 下载 JDK21.0.13 并核对发布元数据 SHA-256；来源见 [jdk-source.json](../../artifacts/test-results/qa-2026-11-01/pr16-ready/jdk-source.json)。Java21 不再是未执行依赖。
- `./server/mvnw -f server/pom.xml --batch-mode --no-transfer-progress verify`，默认完整构建，未启用 isolated profile、未排除失败测试。
- Node26.12.1；web 声明的 Node22 基线尚未复验，不能将 Node26 结果当 Node22 结果。独立tester已实际完成web typecheck、21文件107单测、production build，三项exit0，生产检查无开发原型/演示数据标识，最大JS块201.47kB。首次npm缓存属主EACCES，改用临时npm缓存后干净安装203包成功，未改依赖锁文件。
- 浏览器起初因系统缺 ATK/X11/GBM 依赖无法启动；保留原失败结果。后将运行库解压至工作区临时目录、通过 `LD_LIBRARY_PATH` 使用，未安装或修改主机系统包。真实后端8080、前端5173；非 DSH GUI、非 Mock。
- DB/演示密码运行时随机生成，只保存在权限0600的未跟踪临时文件，通过环境注入；没有提交 `.env`、令牌、网络 trace。Playwright trace 已关闭，避免保存认证请求体/Bearer。

## 3. 后端真实结果

最终源码 = `253c230` 加本轮 QA 补丁；补丁快照见 [test-patch.diff](../../artifacts/test-results/qa-2026-11-01/pr16-ready/java21-reviewed/test-patch.diff)，不冒充仅该提交的原始结果。统计由 XML 自动生成，见 [result-summary.json](../../artifacts/test-results/qa-2026-11-01/pr16-ready/java21-reviewed/result-summary.json)。

| 检查 | 实际结果 |
|---|---|
| Surefire | **77/77 通过** |
| Failsafe | **112项：101通过、9失败、1错误、1跳过**；verify exit 1 |
| RegistrationCapacityIT | **4/4**；20并发 JOIN 恰1成功、19 CAPACITY_FULL，重复JOIN/释放重报及改期冻结均通过 |
| CancellationRaceIT | **2/2**；合法重复取消不多释放/不增事件和版本，取消与改期竞争一致 |
| NotificationConsistencyIT | **1/1**；生产消费者首次失败、恢复、同事件重复消费去重与已读不代签 |
| ChangeRollbackIT | **4/5**；工作人员/批准人拒绝、自身排除、故障注入整体回滚及原幂等键重试通过；抢占前置首次审批400 |
| PermissionsIT | **10/13**；登录、停用令牌、越权、读取裁剪通过；幂等/陈旧版本的首次审批前置400 |
| IdentityEligibilityIT | **2/6**；身份与权限分离、候选过滤与强制受限提交通过；其余4项首次审批400 |
| AN ActivityRegistrationIT | **上下文1 error**；生产 EventPort 与测试替身重复 Bean，原33项未执行；不得按33通过统计 |
| Seeder / SeederRollback / DbTestSupport | **3+1+1 全通过** |
| ResourceSchedulingIT | **29通过/1跳过**；其显式跳过项不计通过，不能继续简单解释成全局缺ChangePort |
| OutboxClaimIT | **4/4**；认领所有权回归通过 |
| D-AT-22 | Benchmark1/1；evaluate exit0，160测量行、16场景策略分组全部expected一致；仅四资源基准，不声称完整demo几何一致 |

总数由132变为112不代表用例被删除：AN整类上下文失败被JUnit记为1 error而非33个实际执行；同时合入PM/CM新增测试。与旧报告不能只比较总通过数。

## 4. 本轮修正的 QA 问题

1. 重复取消旧断言409与03契约末尾的明确要求冲突：改为合法所有者返回200当前结果，增加不增lockVersion、不追加outbox断言；取消后复核用有权限的resource_admin以达到状态保护断言。
2. 通知测试此前定义了同名私有探针，不是CM消费者实际注入类型：现实现CM生产 `NotificationDeliveryProbe`，真实投递失败恢复可达；通知已读补UUID幂等键。
3. 改期顺序测试前例失败遗留PENDING_RESOURCE：每例结束通过合法withdraw API清理，不直改业务表，也不吞原失败。
4. 对Spring事务代理设置Mockito故障会在设置桩时触发MANDATORY：现对代理下实际spy设置故障，HTTP调用仍经过生产事务代理，恢复后原key真实重试通过。
5. 浏览器登录错误文案旧regex误判“请重新登录”：现核对实际API error.message与requestId在UI可见，而非放松为任意文本。
6. Playwright trace关闭；其他与新UI向导、选择器、状态文案的兼容改造另见本轮qa差异与前端检查证据。

## 5. 浏览器、刷新与重启

- 真实服务 `login.spec.ts + ui-layout.spec.ts` **15/15通过**：3登录检查和4页面×3视口；截图非空，见 [截图清单](../../artifacts/screenshots/qa-pr16-ready-2026-11-01/manifest.json)。只自动核对溢出、错误横幅、字体下限与页面状态；未声称人工审美验收通过。
- 明确停止本轮后端进程，以同一数据库、`--app.demo-seed=false`重启；重新登录读取活动A，id/status/versionNo/effectiveVersionNo/lockVersion/input/报名数/风险/待处理改期全部一致，见 [重启结果](../../artifacts/test-results/qa-2026-11-01/pr16-ready/restart-result.json)。数据库本身未重启，不冒充整套发布重启。
- 重启后 `permissions.spec.ts + ui-layout.spec.ts` **16/16通过**：日历脱敏、名单403、他人通知保护、工作人员裁剪及三视口页面；四项权限是真实UI+HTTP，不是组件替身。
- 两次15/16包含重复布局检查，不合并宣称31个独立验收场景。随后`change-happy-path.spec.ts`真实1/1通过：预览选候选、staff/approver/resource、APPLIED/version2、3102/15:00、学生通知和reload。再次停止后端同库关闭seed重启，API状态一致，新浏览器登录与reload均看到version2/3102/15:00，见 [生效后重启结果](../../artifacts/test-results/qa-2026-11-01/pr16-ready/applied-restart-result.json)。据此D-AT-10/23提升通过；数据库本身未重启。

### 后续浏览器兼容复验

- 完整改期成功主线1/1及生效后后端重启/新浏览器reload已通过，D-AT-10/23计通过；最新30项为18通过/10部分/2阻塞，Tx93.3%、Ts60.0%。
- booking真实向导/保存/编辑/提交已通过，最终到批准动作收到400，与DEF-08一致；没有补意见绕过。
- 资格三项真实复验**3/3通过**：UI明示具体受限资源并禁选、教师保存对照、学生HTTP提交403且无预约。只覆盖教师专用组，不冒充8组合全部通过。
- 实跑定位QA假设错误：ElementPlus内部input受占位层遮挡，改点击实际select外壳；资格校验边界是submit而非DRAFT保存。不能强点disabled后声称产品坏；HTTP先保存DRAFT再提交，断言403且DRAFT/lock/effectiveVersion不变和无预约。这些不修改产品资格规则。

## 6. 新缺陷与职责

### DEF-QA-2026-08（B / assign AN）：首次审批合法空意见失败

复现：创建→提交活动后，授权批准人调用 API-18，`{"expectedLockVersion":n,"decision":"APPROVE","comment":""}`，预期200，实际400 `VALIDATION_ERROR`“事件字段不完整”。9项集成测试同源失败。

根因：AN [ActivityService.java:141](../../server/src/main/java/com/campus/events/activity/ActivityService.java#L141) 将可空comment直接作为事件summary/body，CM EventPort拒绝空事件。02契约规定APPROVE意见可空。应由AN使用非空事件描述作为空意见后备值；不放宽事件校验，不给测试偷偷补非空意见。

### DEF-QA-2026-09（C / assign AN）：模块测试与生产Bean冲突

AN [ActivityRegistrationIT.java:84](../../server/src/test/java/com/campus/events/activity/ActivityRegistrationIT.java#L84) 的EventPort测试替身与生产EventPortAdapter同时存在，ChangeService无法唯一注入，33项模块测试未执行。建议在AN测试配置中明确主测试Bean，不改生产装配。

### 已解除的旧缺口

DEF-QA-2026-04/05“生产端口缺失”的原故障已不再复现：提交/改期/冻结/取消/通知及权限读取均已实际到达。关闭原缺失缺陷，将新问题独立登记，不能继续用旧503/404解释本轮400。

## 7. PR推进边界

PR #16 可提交完整QA修改及真实失败证据供正式交叉评审；这与全产品验收放行不同。尚无团队成员批准，不自审、不开管理员后路、不直接合并main。评审人需要核查本PR的测试修正和残余AN缺陷是否应先行修复/关联后续修复；9失败/1错误/1跳过必须在PR置顶说明，不因其他PR已合并而隐去。

干净环境发布 D-AT-24 尚未执行；完整30场景、Node22基线、其余改期分支仍需逐项补齐。下一接收人：AN修复08/09，PM/CM核对发布门槛，另一岗位成员批准QA PR；接收确认尚未收到。
