# 业务规则

版本：v1.0（依据开发任务包 v2.0；2026-09-26）

以下均为演示产品规则，不代表校方制度。权威细节见 `agents/common/01-开发基线.md` 与 `agents/common/02-数据与接口契约.md`。

## BR-AUTH：身份与授权

- BR-AUTH-01：身份只能从有效登录令牌取得；body 中 actorId 不作为授权来源。
- BR-AUTH-02：组织者只能操作自己拥有的活动；教师仅审批本组织活动且不得审批自己的活动。
- BR-AUTH-03：资源管理员只在资源确认、故障和相关查询职责范围内访问数据；系统管理员不继承业务审批权。
- BR-AUTH-04：名单及个人状态按角色裁剪；无权查看的资源日历占用仅公开资源、时间与占用状态。

## BR-VERSION：活动版本与状态

- BR-VERSION-01：版本 1 随活动创建产生。每次草稿编辑和撤回均插入新不可变版本，旧内容不覆盖。
- BR-VERSION-02：versionNo 标识活动内容版本；lockVersion 独立标识并发写入序列。所有命令核对 expectedLockVersion。
- BR-VERSION-03：教师决定绑定具体活动版本。只有当前提交版本的审批可改变活动流程；撤回产生的新版本不能沿用旧审批。
- BR-VERSION-04：状态路径为 `DRAFT → PENDING_TEACHER → APPROVED_UNRESERVED → SCHEDULED → OPEN`；教师驳回为 `PENDING_TEACHER → DRAFT`；所有者撤回允许从 PENDING_TEACHER 或 APPROVED_UNRESERVED 回 DRAFT（新版本）；取消可从任何尚未开始的非 CANCELLED 状态进入 CANCELLED。CANCELLED 为终态。
- BR-VERSION-05：DRAFT 编辑、提交、撤回、教师决定、资源确认、开放、取消及实际报名变化按规定递增 lockVersion；内部端口成功激活新计划时递增，不应由调用方重复递增。

## BR-PLAN：活动输入与计划

- BR-PLAN-01：expectedAttendees、registrationCapacity 均为正整数且前者不小于后者；主场地容量至少为 expectedAttendees。
- BR-PLAN-02：时长 30 分钟至 6 小时；精确到分钟；setup/teardown 为 0—120 分钟。占用区间含布撤场，为左闭右开。
- BR-PLAN-03：计划必须在同一上海自然日及资源开放时段内；初始开放时间 08:00—22:00。端点相接不冲突。
- BR-PLAN-04：场地数量为 1；器材数量为正整数；同一计划同一资源只能出现一次。工作人员必须是启用的本组织账号并去重。
- BR-PLAN-05：已批准但未预约不代表资源可用。只有联合检查成功后才能设为 SCHEDULED；只有 SCHEDULED、未开始、资源风险 NORMAL 才能开放报名。
- BR-PLAN-06：资源容量、器材库存、开放时间和资源身份首版不提供编辑入口；资源不可用以故障记录表示。

## BR-RESERVATION：预约一致性

- BR-RESERVATION-01：所有预约创建、更换和释放均经 SchedulingPort；禁止活动/变更模块直接写 reservation 表。
- BR-RESERVATION-02：首次确认必须在单一事务内锁活动、按资源 UUID 顺序锁资源、重新检查整组计划、更新活动状态并整组写预约。
- BR-RESERVATION-03：资源冲突为正常业务结果：返回 409 RESOURCE_CONFLICT，活动留在 APPROVED_UNRESERVED，活动版本指针及既有预约不变，且没有部分预约。
- BR-RESERVATION-04：场地同一时刻最多一项活动；器材同一时刻总需求不超过库存减故障量。区间边界先处理结束再处理开始，避免将相接区间视为重叠。

## BR-REGISTRATION：报名

- BR-REGISTRATION-01：只允许学生为 OPEN 且未开始、没有待处理改期的活动报名或退出。
- BR-REGISTRATION-02：`(activity_id,user_id)` 唯一；计数只包括 ACTIVE；容量不得超限。JOIN 已有 ACTIVE 与 WITHDRAW 已有 WITHDRAWN 均返回当前记录。
- BR-REGISTRATION-03：没有历史记录时 WITHDRAW 返回 409 INVALID_STATE；WITHDRAWN 后重报需再次检查容量。
- BR-REGISTRATION-04：每个活动同时最多一个状态为 AWAITING_CONFIRMATIONS、PENDING_TEACHER 或 PENDING_RESOURCE 的待处理改期；待处理期间报名与退出均返回 409 CHANGE_PENDING。
- BR-REGISTRATION-05：实际报名状态变化递增 activity.lockVersion，确保改期影响快照不会遗漏并发名单变化。

## BR-CANCEL：取消

- BR-CANCEL-01：活动所有者可取消尚未开始的非 CANCELLED 活动；先验证所有权，再处理幂等终态。
- BR-CANCEL-02：取消快照收件人后，必须在同一事务取消待处理改期、释放全部 ACTIVE 预约、将有效报名置 CANCELLED、将活动置 CANCELLED 并写事件。
- BR-CANCEL-03：本人合法重复取消返回当前结果，不再次释放资源；他人不能利用终态幂等绕过授权。
- BR-CANCEL-04：取消与改期共用活动行锁。取消提交后，旧改期请求不能恢复活动或预约。

## BR-HTTP：接口、幂等及错误

- BR-HTTP-01：HTTP API 使用 `/api/v1`、统一成功/错误信封及 requestId；字段、枚举与路径采用数据接口契约。
- BR-HTTP-02：持久化命令通过 CommandExecutor 和 UUID Idempotency-Key。相同用户、路由、key、规范化请求体重放原结果；相同 key 用于不同内容返回 409 IDEMPOTENCY_KEY_REUSED。
- BR-HTTP-03：旧锁版本返回 409 STALE_VERSION；无效状态返回 409 INVALID_STATE；容量不足返回 409 CAPACITY_FULL；待处理改期期间操作报名返回 409 CHANGE_PENDING。
- BR-HTTP-04：数据库异常必须触发事务回滚；不得捕获异常后返回伪造成功。业务冲突可以作为 CommandResult 正常提交其规定的冲突记录。
- BR-HTTP-05：列表过滤权限后最多返回 200 项，使用第 201 项判断 truncated；截断不能被客户端呈现为空结果。

## BR-EVENT：事件与通知

- BR-EVENT-01：活动提交、审批、发布、取消及规定的改期操作通过 EventPort 追加审计与 outbox；事件与业务事务一致提交。
- BR-EVENT-02：取消通知收件人快照由组织者、工作人员及有效报名者去重组成；outbox 消费不得重新计算收件人。
- BR-EVENT-03：通知投递失败不回滚已提交业务；重复消费按 eventId 和 recipientId 去重。读取通知不等于工作人员已确认。

## BR-CHANGE 边界

- BR-CHANGE-01：活动模块提供 ActivityPort；`lock`、`activatePlan`、`bumpLockVersion` 要求调用方已有事务。`lock` 使用 `SELECT FOR UPDATE`。
- BR-CHANGE-02：`activatePlan` 要求调用方已持活动锁、基础版本一致且状态为 SCHEDULED/OPEN；复制活动元数据，仅替换计划，插入新版本并更新当前/有效版本及 ACTIVE 报名版本，不写预约、不发通知。
- BR-CHANGE-03：ChangePort.cancelPending 只更新改期表，由活动取消的事务统一负责活动 lockVersion、预约、报名与事件。
- BR-CHANGE-04：ActivityPort 适配器依赖自身 repository 与端口只读能力，不调用上层 Controller；不得形成 ActivityService/ChangeService 循环依赖。
