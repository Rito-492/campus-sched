# 测试设计 v0.1 —— D-AT 验收场景 → 测试用例

> 归属：测试经理（55230316）。依据 `agents/common/03-验收与演示数据.md` §2 的 24 项 D-AT 场景逐条转写。
> 断言依据：`agents/common/02-数据与接口契约.md`（字段/枚举/状态码）、`agents/common/01-开发基线.md`（业务规则/状态机）。
> **执行状态初始均为「未执行」**（03 §1）；实际结果只能由真实运行产生，汇总于 `docs/testing/test-report.md`。
> 每条记录对应 D-AT 编号（03 §2 映射职责）；数据用 `test-data/demo.json` 统一尾号，装载规则见 `test-data-plan.md` §5/§8。

## 0. 用例与 D-AT 映射总表

执行位置缩写：`Permissions/Booking/ChangeRollback/CancelRace/RegCapacity/NotifyConsist` = `server/src/test/java/com/campus/events/acceptance/` 下六个 IT；`login/happy/conflict/perm/layout` = `qa/tests/` 下五个 E2E spec；`Bench` = `acceptance/SchedulingBenchmarkIT`；`AN自测/DS自测` = 模块负责人自己的测试（QA 维护状态汇总，不代跑）。

| D-AT | 用例 | 执行位置 | 主责 | 状态 |
|---|---|---|---|---|
| D-AT-01 | TC-01-1/2/3 | Permissions + login.spec | PM | ⬜ 未执行 |
| D-AT-02 | TC-02-1/2/3 | Permissions | PM+AN | ⬜ 未执行 |
| D-AT-03 | TC-03-1 | AN自测 + happy.spec 复核 | AN | ⬜ 未执行 |
| D-AT-04 | TC-04-1/2 | DS自测 + Bench间接 | DS | ⬜ 未执行 |
| D-AT-05 | TC-05-1 | DS自测 + Bench(SCH-06) | DS | ⬜ 未执行 |
| D-AT-06 | TC-06-1 | DS自测 | DS | ⬜ 未执行 |
| D-AT-07 | TC-07-1 | Booking | DS+AN | ⬜ 未执行 |
| D-AT-08 | TC-08-1/2 | Permissions | PM | ⬜ 未执行 |
| D-AT-09 | TC-09-1/2 | DS自测 + conflict.spec | DS+UI | ⬜ 未执行 |
| D-AT-10 | TC-10-1 | happy.spec + ChangeRollback前置复核 | CM+AN | ⬜ 未执行 |
| D-AT-11 | TC-11-1/2 | ChangeRollback | CM | ⬜ 未执行 |
| D-AT-12 | TC-12-1 | ChangeRollback + conflict.spec | CM+DS | ⬜ 未执行 |
| D-AT-13 | TC-13-1 | DS自测 + Bench(SCH-01) | DS+CM | ⬜ 未执行 |
| D-AT-14 | TC-14-1 | ChangeRollback | CM | ⬜ 未执行 |
| D-AT-15 | TC-15-1 | RegCapacity | AN+CM | ⬜ 未执行 |
| D-AT-16 | TC-16-1/2/3 | RegCapacity | AN | ⬜ 未执行 |
| D-AT-17 | TC-17-1/2 | CancelRace | AN+CM | ⬜ 未执行 |
| D-AT-18 | TC-18-1 | DS自测 + conflict.spec | DS+UI | ⬜ 未执行 |
| D-AT-19 | TC-19-1 | NotifyConsist | CM | ⬜ 未执行 |
| D-AT-20 | TC-20-1/2 | Permissions | AN+CM | ⬜ 未执行 |
| D-AT-21 | TC-21-1/2/3 | Permissions + perm.spec | 全员 | ⬜ 未执行 |
| D-AT-22 | TC-22-1 | Bench + `qa/scripts/evaluate-scheduling.ts` | DS+测试 | ⬜ 未执行 |
| D-AT-23 | TC-23-1 | happy.spec | UI+配置 | ⬜ 未执行 |
| D-AT-24 | TC-24-1 | 发布演练（人工+check.ps1） | 全员 | ⬜ 未执行 |

通用断言规则（03 §2/§3）：不只检查 HTTP 200，必须核对持久化状态与数量；并发用独立连接/事务 + 起跑屏障；预期失败的请求是断言对象而非「测试失败」；失败注入只在测试上下文替换 bean/挂钩，**不提供公网故障接口**。

---

## D-AT-01 登录、令牌与响应脱敏

### TC-01-1 正常登录并访问（PermissionsIT / login.spec）
- **前置**：装载 demo.json；Clock 固定 2030-10-19T00:00:00Z。
- **输入**：`POST /api/v1/auth/login {username:"organizer_a", password:"<DEMO_PASSWORD>"}`；随后带 token `GET /auth/me`。
- **步骤**：1) 登录；2) 递归扫描登录响应 JSON；3) 带令牌取 /auth/me。
- **预期**：200；`data{token, expiresAt, user}`，user.roles=["ORGANIZER"]、organizationId=组织1001；/auth/me 返回同一用户；响应信封含 requestId。
- **观察点**：响应任意层级**无 password/passwordHash 键**；`auth_session` 新增 1 行且 token_hash ≠ token 明文；expiresAt 为带时区 ISO8601。
- **清理**：schema 级自动清理（DbTestSupport）。

### TC-01-2 错误密码（PermissionsIT）
- **前置**：同上。
- **输入**：`POST /auth/login {username:"organizer_a", password:"wrong"}`。
- **步骤**：登录失败后立即用正确密码登录一次。
- **预期**：401 `UNAUTHENTICATED`；第二次正确密码 200（账号未被锁死）。
- **观察点**：错误信封结构 `{error:{code,message,details},requestId}`；`auth_session` 无失败登录产生的行；错误响应不含 passwordHash。
- **清理**：无写入。

### TC-01-3 停用账号使旧令牌失效（PermissionsIT）
- **前置**：sys_admin 已登录；student_a 已登录并持有旧令牌 T。
- **输入**：`PATCH /users/{student_a}/enabled {enabled:false}`（sys_admin）；再用 T `GET /auth/me`。
- **步骤**：1) 记录 student_a 登录；2) 停用；3) 旧令牌访问；4) 重新启用。
- **预期**：停用 200 `{ok:true}`；旧令牌 401。
- **观察点**：`app_user.enabled=false`；旧会话不可用（令牌失效，而非仅前端隐藏）。
- **清理**：重新启用 student_a（enabled=true）。

## D-AT-02 越权访问（403）

### TC-02-1/2/3 跨组织审批、SYS_ADMIN 无业务审批权、非所有者编辑（PermissionsIT）
- **前置**：A（4001，OPEN，owner=organizer_a，组织1001）已装载。
- **输入**：① teacher_other（组织1002）`POST /activities/{4001}/teacher-decision {decision:"APPROVE",comment:"",expectedLockVersion:0}`；② sys_admin 同请求；③ organizer_b `PATCH /activities/{4001}`（ActivityEdit）。
- **步骤**：三个请求依次执行，各自用对应账号令牌。
- **预期**：**均 403 FORBIDDEN**；活动、预约及审批记录无成功变化。
- **观察点**：A 的 status 仍 OPEN、lock_version 不变；`activity_approval` 无新增行；`reservation` 状态计数不变；403 详情不泄露无权查看的对象信息（02 §3）。
- **清理**：无写入。

## D-AT-03 首次预约生命周期（AN 自测为主，happy.spec 复核状态标签）

### TC-03-1 新建→编辑→提交→教师同意→资源确认→发布
- **前置**：空活动数据（组织 1001 账号齐全）。
- **输入**：API-12 新建（ActivityInput：30 分钟活动、场地 3101、器材 3201×1）→ API-15 编辑 → API-16 提交 → API-18 teacher_a 同意 → API-19 resource_admin 确认 → API-20 发布。全部写操作带各自 `Idempotency-Key`。
- **步骤**：逐步推进并在每步后 GET /activities/{id}。
- **预期**：状态依次 DRAFT(v1)→DRAFT(v2)→PENDING_TEACHER→APPROVED_UNRESERVED→SCHEDULED→OPEN；version_no 每次编辑 +1；resource-confirm 设置 effectiveVersionNo=当前版本。
- **观察点**：**首次确认前 `reservation` 0 条 ACTIVE**；确认后 ACTIVE 行数 = 场地 1 + 器材 1 = 2，数量与区间（含布撤场）正确；`activity_version` 每版本 1 行且 input_json 完整。
- **清理**：schema 级自动清理。

## D-AT-04 相邻时段与布撤场（DS 自测为主，Bench 间接覆盖）

### TC-04-1 相邻占用可共存
- **前置**：3101 上已有占用 [10:00,11:00)。
- **输入**：新活动计划 D 日 11:00—12:00，setup=teardown=0，场地 3101 → `POST /activities/{id}/schedule-preview`（API-24）。
- **步骤**：预览并读 CheckResult。
- **预期**：feasible=true，conflicts=[]（区间端点相接不冲突，01 §4）。
- **观察点**：预览是只读（不要求 Idempotency-Key、不写库）。
- **清理**：无写入。

### TC-04-2 后场布场 15 分钟产生边界冲突
- **前置**：同 TC-04-1。
- **输入**：同上但 setupMinutes=15（占用 [10:45,12:00)）。
- **步骤**：预览。
- **预期**：feasible=false；1 条 `VENUE_OCCUPIED`，冲突区间 [10:45,11:00)。
- **观察点**：conflict 字段完整（resourceId/resourceName/startsAt/endsAt/required=1/available=0/message 中文）。
- **清理**：无写入。

## D-AT-05 器材峰值不误算（DS 自测；Bench SCH-06 同几何）

### TC-05-1 错峰占用求和误算防护
- **前置**：3201（库存 2）已有 ACTIVE [10,11)×1、[11,12)×1。
- **输入**：新申请 [10,12)×1（场地另计，无冲突）。
- **步骤**：schedule-preview。
- **预期**：feasible=true——任一时刻并发恰 2 ≤ 库存 2；**不能**把两条错峰占用求和成 3。
- **观察点**：无 EQUIPMENT_SHORTAGE 冲突；边界扫描「先处理结束再开始」。
- **清理**：无写入。

## D-AT-06 缺件冲突解释（DS 自测）

### TC-06-1 指出缺 1 且无部分占用
- **前置**：3201（库存 2）已有 ACTIVE [10,12)×2（占满）。
- **输入**：新申请 [11,13)×1。
- **步骤**：预览；失败后查 `reservation`。
- **预期**：feasible=false，`EQUIPMENT_SHORTAGE` 指出 [11,12) 缺 1（required=1, available=0）；其他资源无新增占用。
- **观察点**：`reservation` 无新增行（预览与失败确认都不落部分占用）。
- **清理**：无写入。

## D-AT-07 并发资源确认（BookingConcurrencyIT）

### TC-07-1 两活动抢同一资源，恰一成功
- **前置**：两个已教师批准（APPROVED_UNRESERVED）的活动 X、Y，同场地 3101 同时段 [10:00,11:00)，各需 3201×2。
- **输入**：两个 `POST /activities/{id}/resource-confirm`（各自 Idempotency-Key），由 ConcurrentRunner 用**独立连接 + 起跑栅栏**同时发出（03 §2 非单连接循环）。
- **步骤**：并发执行 → 收集两个响应 → 按活动分组查库。
- **预期**：恰 1 个 200（SCHEDULED）与 1 个 409 `RESOURCE_CONFLICT`（details={conflicts:[]}）；每资源不超额；失败活动**无部分预约**（0 条 ACTIVE）。
- **观察点**：`assertEquals(1, results.filter(200).count())`、`assertEquals(1, results.filter(409).count())`（03 §2 示例）；失败活动状态保留 APPROVED_UNRESERVED。
- **清理**：schema 级自动清理。

## D-AT-08 幂等键语义（PermissionsIT）

### TC-08-1 同 key 同 body 重复确认
- **前置**：可确认活动；Key=K，body=B。
- **输入**：以 K+B 连续两次 `POST /activities/{id}/resource-confirm`。
- **步骤**：两次提交；比较响应；查 `command_receipt` 与 `reservation`。
- **预期**：两次返回相同状态/实体；**不新增记录**。
- **观察点**：`command_receipt` 中 (actor_id,K) 仍 1 行（重放返回首次响应、含首次 requestId，02 §5）；`reservation` 行数不变。
- **清理**：schema 级自动清理。

### TC-08-2 同 key 不同 body
- **前置**：同上，Key=K 已用于 body B。
- **输入**：以 K + B'（不同内容）再请求。
- **预期**：409 `IDEMPOTENCY_KEY_REUSED`。
- **观察点**：不执行新业务写入；错误 details 含当前状态/版本类信息。
- **清理**：无写入。

## D-AT-09 改期冲突与替代方案（DS 自测 + conflict.spec）

### TC-09-1 A 改到 15:00 报两类冲突、首选 (0,1,1)
- **前置**：demo 数据：A=10:00—11:00（占用 09:30—11:30，3101+3201×2），B=14:30—16:30（3101+3201×1）。
- **输入**：A 的目标计划 D 日 15:00—16:00、setup/teardown 30、仍 3101+3201×2 → API-24 schedule-preview + API-25 alternatives（03 §1 改期目标）。
- **步骤**：预览 → 读 conflicts → 读 alternatives.items[0]。
- **预期**：**两类冲突同时报**：`VENUE_OCCUPIED`（3101 与 B 重叠 [14:30,16:30)）与 `EQUIPMENT_SHORTAGE`（3201 缺 1：库存 2 − B 占 1 = 可用 1 < 2）；首选替代 = 3102+3202×2 同时间，代价前三项 **(0,1,1)**（03 §1 固定断言）。
- **观察点**：`preview 未改数据库`——`reservation`/`change_request` 无新增；AlternativesView 含 evaluatedCount/truncated/offsetsMinutes/maxEvaluations。
- **清理**：无写入。

### TC-09-2 UI 预览一致性（conflict.spec）
- **前置**：organizer_a 登录 web。
- **输入**：活动 A 详情 → 发起改期 → 目标 15:00—16:00 → 预览。
- **步骤**：走 UI 主线，读页面冲突列表与替代方案列表。
- **预期**：页面同时呈现场地冲突与器材缺 1；首选方案标注 3102+3202×2、同时间。
- **观察点**：用公共 data-testid 断言（不靠按钮位置，QA-03）；截图存 `artifacts/screenshots/`。
- **清理**：E2E 独立环境，不清理共享库。

## D-AT-10 改期成功主线（happy.spec + 前置库断言）

### TC-10-1 确认链走完并原子切换到 version2
- **前置**：A（OPEN）+ TC-09 的目标/首选方案。
- **输入**：API-26 change-preview → API-27 changes 创建（ChangeCreate：baseVersionNo=1、targetPlan=首选、expectedLockVersion、reason≥1字）→ API-30 staff_a 确认 APPROVE → API-31 teacher_a 同意 → API-32 resource_admin 同意。
- **步骤**：每步后 GET /changes/{id} 记录 status。
- **预期**：状态依次 AWAITING_CONFIRMATIONS →（staff_a 同意）PENDING_TEACHER →（teacher 同意）PENDING_RESOURCE →（资源同意）APPLIED；A 变 **version2**；旧预约 RELEASED、新预约 ACTIVE；报名保留且 versionNo=2；通知/审计关联 version2（03 §2）。
- **观察点**：`activity.version_no=2、effective_version_no=2`；`reservation`：旧 2 行 status=RELEASED、新 2 行 status=ACTIVE（3102+3202×2）；`activity_registration` 仍 ACTIVE 且 version_no=2（02 §5 activatePlan 更新报名版本）；`audit_log`/`outbox_event`/`notification` 的 versionNo=2；ChangeView.appliedVersionNo=2。
- **清理**：schema 级自动清理。

## D-AT-11 拒绝改期（ChangeRollbackIT）

### TC-11-1 工作人员拒绝
- **前置**：改期处于 AWAITING_CONFIRMATIONS。
- **输入**：staff_a `POST /changes/{id}/confirmation {decision:"REJECT",comment:"时间冲突"}`。
- **步骤**：拒绝后查活动、预约、报名。
- **预期**：改期 REJECTED；A 原版本/预约**完全保留**（effective_version_no=1，旧 ACTIVE 行不变）；报名冻结解除。
- **观察点**：`change_confirmation.decision='REJECT'`；无新 `activity_version`；随后报名/退出请求不再返回 CHANGE_PENDING。
- **清理**：schema 级自动清理。

### TC-11-2 教师驳回
- **前置**：改期经全部 staff 同意后处于 PENDING_TEACHER。
- **输入**：teacher_a `POST /changes/{id}/teacher-decision {decision:"REJECT",comment:"不同意"}`。
- **预期**：REJECTED；同 TC-11-1 的保留性断言；审批绑定该 changeId 与 targetPlan，不可被其他方案复用（01 §6）。
- **观察点**：teacher_decision_json 落库；活动版本指针不变。
- **清理**：schema 级自动清理。

## D-AT-12 预览后被抢占（ChangeRollbackIT + conflict.spec）

### TC-12-1 最终确认报 CONFLICTED 且原安排保留
- **前置**：改期已创建并走到 PENDING_RESOURCE（预览时可行）。
- **输入**：另一活动 Z 先占用候选资源（3102+3202 全量）→ resource_admin `POST /changes/{id}/resource-decision {decision:"APPROVE",comment:""}`。
- **步骤**：抢占 → 最终确认 → 查库。
- **预期**：409 `RESOURCE_CONFLICT`；改期 **CONFLICTED**；A 原有效安排保留；无新预约。
- **观察点**：details 固定为 `{conflicts:Conflict[], change:ChangeView}`（02 §3）；`activity_version` 无新行；旧预约仍 ACTIVE；`last_conflicts_json` 保存冲突；组织者须重新预览并创建新改期，旧教师意见不移用。
- **清理**：schema 级自动清理。

## D-AT-13 自身旧占用排除（DS 自测；Bench SCH-01 同语义）

### TC-13-1 不把自己算作竞争活动
- **前置**：A 原占用 [09:30,11:30)（含布撤场）。
- **输入**：目标计划与自身旧占用**部分重叠**（如 10:30—11:30、setup/teardown 30 → 占用 [10:00,12:00) 与旧占用重叠 [10:00,11:30)），排除 activityId=A 做检查。
- **步骤**：schedule-preview / 预览（excludeActivityId=A）。
- **预期**：排除自身旧占用后无冲突（其他活动无冲突时 feasible=true）；不得把自己计为额外竞争者。
- **观察点**：conflicts 为空；若未排除自身，会误报 VENUE_OCCUPIED——此为定位此类缺陷的判别式。
- **清理**：无写入。

## D-AT-14 改期事务回滚（ChangeRollbackIT）

### TC-14-1 预约替换中注入异常，整体回滚
- **前置**：改期处于 PENDING_RESOURCE；测试上下文准备故障挂钩（替换 bean/挂钩，03 §2）。
- **输入**：resource-admin 最终确认；故障点在 **activatePlan 之后、replaceReservations 中**（02 §6）注入 RuntimeException。
- **步骤**：1) 注入前读数据库快照；2) 触发确认捕获异常；3) 再读快照比较；4) 移除故障后以原请求重试。
- **预期**：整个事务回滚：活动版本指针、预约、改期、outbox **均恢复**；无部分提交；改期保留原待处理状态（PENDING_RESOURCE）；原请求重试可继续（01 §6）。
- **观察点**：`assertEquals(beforeEffectiveVersion, afterEffectiveVersion)`、`beforeActiveReservations==after`、`beforeOutboxCount==after`（03 §2 示例，查库比较而非只看 HTTP）。
- **清理**：移除故障挂钩；schema 级自动清理。

## D-AT-15 改期期间报名冻结（RegCapacityIT）

### TC-15-1 待处理 409、终态恢复
- **前置**：OPEN 活动存在待处理改期（AWAITING_CONFIRMATIONS/PENDING_TEACHER/PENDING_RESOURCE 之一）。
- **输入**：student `PUT /activities/{id}/registration {action:"JOIN",expectedVersionNo:n}` 与 `{action:"WITHDRAW",...}`；改期到终态后重试。
- **步骤**：冻结期请求 → 终态（REJECTED 或 APPLIED）→ 重试。
- **预期**：冻结期 409 `CHANGE_PENDING`（保证影响名单不漂移，01 §6）；终态后按名额规则执行。
- **观察点**：`activity_registration` 在冻结期无变化；终态后 JOIN/WITHDRAW 正常落库。
- **清理**：schema 级自动清理。

## D-AT-16 并发报名容量（RegCapacityIT）

### TC-16-1 容量 1 对 20 并发 JOIN
- **前置**：装载 demo.json + registration-concurrency.json（活动 4101 容量 1、20 名 perf_s 学生）。
- **输入**：20 个 `PUT /activities/{4101}/registration {action:"JOIN",expectedVersionNo:1}`，ConcurrentRunner 独立连接 + 起跑栅栏同步发出。
- **步骤**：并发 → 收集 20 个响应 → 查库。
- **预期**：恰 **1 条 ACTIVE**，其余 `CAPACITY_FULL`（409）。
- **观察点**：`assertEquals(1, activeCount)`；20 个响应中 1 个 200、19 个 409；无超额 ACTIVE。
- **清理**：schema 级自动清理。

### TC-16-2 重复 JOIN 不增人数
- **前置**：TC-16-1 中签者 u。
- **输入**：u 重复 JOIN 两次。
- **预期**：返回同一状态/实体，ACTIVE 计数不变。
- **观察点**：`activity_registration` 仍 1 行 ACTIVE（复合主键 (activity_id,user_id)）。
- **清理**：同上。

### TC-16-3 退出恢复名额
- **前置**：TC-16-1 后 u ACTIVE。
- **输入**：u WITHDRAW → 另一学生 JOIN。
- **预期**：退出后名额释放（status=WITHDRAWN）；新学生 JOIN 成功 1 条 ACTIVE。
- **观察点**：ACTIVE 计数回到 1；versionNo 校验按当前有效版本。
- **清理**：同上。

## D-AT-17 取消与竞争（CancelRaceIT）

### TC-17-1 取消两次不多释放、不复活
- **前置**：已预约活动（SCHEDULED/OPEN，含 ACTIVE 预约与报名）。
- **输入**：owner `POST /activities/{id}/cancel {reason:"取消",expectedLockVersion:n}` 两次（同 key/不同 key 各一次）。
- **步骤**：首次取消 → 记录预约行状态计数 → 重复取消 → 再计数。
- **预期**：最终 CANCELLED；0 条 ACTIVE 预约；有效待处理改期 0 个（作废为 CANCELLED_BY_ACTIVITY）；报名取消（CANCELLED）并通知；**重复不多释放**（RELEASED 行数不因重复请求增长）；不得复活。
- **观察点**：`reservation` ACTIVE=0；`change_request` 无 AWAITING_CONFIRMATIONS/PENDING_*；`activity_registration` 无 ACTIVE。
- **清理**：schema 级自动清理。

### TC-17-2 取消与改期资源确认并发
- **前置**：活动存在 PENDING_RESOURCE 改期。
- **输入**：cancel 与 `POST /changes/{id}/resource-decision` 用栅栏并发。
- **步骤**：并发 → 双响应收集 → 查库。
- **预期**：最终态一致：活动 CANCELLED、0 ACTIVE 预约、0 有效待处理改期；取消始终可覆盖待处理改期（01 §6）；无论哪方先提交，**不得出现「改期生效后活动仍被取消但预约残留」的部分状态**。
- **观察点**：两事务的最终库状态联合断言（不是只看各自 HTTP 码）。
- **清理**：同上。

## D-AT-18 资源故障影响（DS 自测 + conflict.spec）

### TC-18-1 故障标 AT_RISK、受影响清单含 A、页面不谎报
- **前置**：A 原占用资源（3101 或 3201）上有 ACTIVE 预约。
- **输入**：resource_admin `POST /resources/{id}/faults` 创建与 A 原占用重叠的故障（FaultInput：unavailableQuantity≥1，venue 限 1）；随后让 A 的改期失败（或直接观察原安排）。
- **步骤**：建故障 → GET /activities/{A} → GET /resources/{id}/faults → 打开 UI 活动页。
- **预期**：原 reservation 记录仍在但 **resourceRisk=AT_RISK**；FaultView.affectedActivities 含 A；页面**不能**显示原安排可正常使用（须展示风险提示）。
- **观察点**：`resource_fault` 落库且数量 ≤ 资源 quantity；`ActivityView.resourceRisk` 是查询时计算（02 §4）；日历 FAULT 条目 quantity=故障数、activityId=null、label=「资源不可用」、不泄露登记者与私有原因（02 §3）；E2E 截图佐证页面提示。
- **清理**：删除故障记录（或 schema 级清理）。

## D-AT-19 通知一致性（NotifyConsistIT）

### TC-19-1 消费失败恢复 + 重复消费不重发
- **前置**：一次产生通知事件的业务提交（如改期创建）；通知消费者可注入首次失败（测试上下文）。
- **输入**：业务提交 → 消费者首次消费抛错 → 恢复 → 对同一 event 再次投递。
- **步骤**：等待条件达成（expect 轮询，03 §3 不用长 sleep 掩盖时序）；查 `notification`。
- **预期**：**业务已提交不回滚**（失败通知不回滚已提交安排，01 §8）；最终每收件人恰 **1 条** 通知（event_id+recipient_id 唯一）；重复消费不新增。
- **观察点**：`outbox_event` attempts/状态流转（PENDING→失败重试→DONE）；`notification` 按 (event_id,recipient_id) 计数=1；**读取通知不改变工作人员确认**——`POST /notifications/{id}/read` 只写 read_at，`change_confirmation` 不变。
- **清理**：移除失败挂钩；schema 级自动清理。

## D-AT-20 陈旧版本保护（PermissionsIT）

### TC-20-1 旧 baseVersion 创建改期
- **前置**：A 已被改期产生 version2。
- **输入**：`POST /activities/{id}/changes` 带 baseVersionNo=1（旧值）。
- **预期**：409 `STALE_VERSION`；无覆盖最新安排。
- **观察点**：details 含当前 versionNo/lockVersion（02 §3）；无新 `activity_version`。
- **清理**：无写入。

### TC-20-2 旧 expectedLockVersion 提交
- **前置**：活动 lock_version 已递增。
- **输入**：写操作（提交/编辑/确认）带旧 expectedLockVersion。
- **预期**：409 `STALE_VERSION`。
- **观察点**：同上；幂等重放优先于版本报错（01 §8：同 key 同 body 重放不能先因旧 expectedLockVersion 报错）。
- **清理**：无写入。

## D-AT-21 隐私与读取隔离（PermissionsIT + perm.spec）

### TC-21-1 日历脱敏
- **前置**：其他组织/无权活动占用某资源。
- **输入**：普通学生 `GET /resources/calendar?from=...&to=...`。
- **步骤**：读取条目；断言字段。
- **预期**：日历只显示无权限占用信息（时间/资源/数量），**不泄露名称、组织者、名单**（01 §3）。
- **观察点**：无权条目 label 不含活动标题（显示「已占用」类文案）、无 ownerId/名单字段；数量>200 时 `truncated=true` 且前端提示缩小范围（02 §5）。
- **清理**：无写入。

### TC-21-2 名单与他人通知 403
- **输入**：学生 `GET /activities/{他人的活动}/registrations`；`GET /notifications` 后核对收件人。
- **预期**：名单 403（仅所有者/同组织教师/资源管理员，API-23）；通知只返回本人条目。
- **观察点**：响应不泄露报名人 ID 列表；学生公开详情 `input.staffUserIds=[]` 裁剪（02 §3）。
- **清理**：无写入。

### TC-21-3 工作人员视角的变更详情裁剪
- **输入**：staff_a `GET /changes/{id}`。
- **预期**：registeredUserIds 与 notificationUserIds 均为空数组，registeredCount 保持真实（02 §3）。
- **观察点**：三字段的可见性差异断言。
- **清理**：无写入。

## D-AT-22 策略对比评测（Bench + evaluate-scheduling.ts）

### TC-22-1 FIRST_FIT vs RANKED 可复现评测
- **前置**：装载 `test-data/scenarios/scheduling.json`（8 场景，见 test-data-plan.md §7）。
- **输入**：DS 的 `StrategyEvaluator.evaluateStrategies(plan, attendees, excludeActivityId)`（02 §8，仅供测试调用）。
- **步骤**：每场景每策略预热 1 次、测量 10 次（03 §4）；`SchedulingBenchmarkIT` 写 `server/target/scheduling-evaluation.json`；`npm --prefix qa run evaluate` 读取生成 `artifacts/evaluation/results.csv` + 环境说明。
- **预期**：相同数据/候选/约束下两策略各自的真实成功情况、代价与耗时**如实记录**；无解场景明确搜索范围；SCH-08 断言两策略选择不同（FIRST_FIT=(-30,换场地,0)、RANKED=(+30,不换,替换1)，见场景文件 expected）。
- **观察点**：CSV 列 = scenarioId、strategy、feasible、timeShiftMinutes、venueChanged、replacedEquipmentLines、evaluatedCount、elapsedMs（03 §4）；无可行方案的三个代价字段写 **null 不造 0**；elapsedNanos/1_000_000.0；记录机器、版本、数据与计时范围；**缺原始结果时脚本失败**。
- **清理**：schema 级自动清理；评测产物只增不改历史。

## D-AT-23 持久化与恢复（happy.spec）

### TC-23-1 刷新与重启后状态来自真实持久化
- **前置**：E2E 独立 Compose 环境（dev,e2e profile、campus_test、端口 8089、固定 APP_TEST_NOW/DEMO_DATE）。
- **输入**：按 UI 主线完成一次改期生效 → 浏览器刷新 → `docker compose restart server` → 重新登录继续查看。
- **步骤**：每步后读页面状态。
- **预期**：状态来自真实持久化数据；页面正常恢复；**无仅内存中的成功结果**。
- **观察点**：刷新/重启后 version2、预约、通知仍可见；失败场景（如 CONFLICTED）重启后仍如实展示；截图存 `artifacts/screenshots/`。
- **清理**：E2E 环境销毁/重置流程归 CM（不得接真实部署库）。

## D-AT-24 干净环境发布验收（发布演练）

### TC-24-1 按发布说明启动并换角色答辩
- **前置**：干净机器/干净数据库；仅 `release/` 发布包与发布说明。
- **输入**：按发布说明启动 Docker Compose（db、server、web）→ 按角色完成 D-AT-09/10 主线 → 选 D-AT-12 或 14 解释原安排保护 → 展示故障影响、审计与实际测试报告（03 §5 演示顺序）。
- **步骤**：逐步执行并记录命令输出。
- **预期**：真实后端/数据库连通；**无手改数据库才可继续的步骤**；版本、用户手册、测试报告一致。
- **观察点**：`pwsh -File scripts/check.ps1 -Scope all` 退出码 0 且预期测试数非 0；发布 manifest 版本号与报告一致；全程留存日志于 `artifacts/test-results/`。
- **清理**：环境下线；证据归档交 CM。

---

## 附：界面与契约核对（QA-03，独立于 D-AT）

| 项 | 内容 | 状态 |
|---|---|---|
| 契约检查 | `qa/scripts/check-contract.ts` 读 `contracts/openapi.yaml`，核对 38 接口、必需字段、枚举、响应包装、认证与 Idempotency-Key；发现差异交实现者或 PM，**不私自改契约迎合实现** | ⬜ 未执行 |
| 视觉核对 | `ui-layout.spec.ts` 按 05 界面规范检查 1366/1920 桌面与 390 移动视口，自动查溢出/错误，人工审查层次/文本/对比；截图不等于「自动证明美观」 | ⬜ 未执行 |
| E2E 通则 | 每角色独立浏览器 context 登录；公共 data-testid；`expect` 轮询业务状态，不用长 sleep | ⬜ 未执行 |
