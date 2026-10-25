# 活动与报名模块接口交接 v1.0

日期：2026-10-25。成员：冯玉琪（22220314），系统分析员。任务：AN-02～05。开发基准：main `34dc4d5`；分支：`work/22220314-activity`。公共契约仍为任务包 v3.0，本交接不定义第二套 DTO 或响应。

## 已实现的接口与外部依赖

所有接口前缀 `/api/v1`。写接口复用 PM 的 `CommandExecutor`、`CanonicalJson` 和 `Responses`，需要 UUID `Idempotency-Key`；令牌由现有认证过滤器解析，不接受请求体提供 actorId。API-12 成功为 201，其余为 200，列表保留 truncated。

| API | 方法与路径 | 请求类型 | 实现及外部依赖 |
|---|---|---|---|
| 12 | POST /activities | ActivityInput | 创建 DRAFT 和完整不可变首版本；AccountPort、真实数据库 |
| 13 | GET /activities?scope=mine/public/review&status=… | 无 | SQL 先按归属/状态/当前授权范围过滤，再取 201 条；最多返回 200 |
| 14 | GET /activities/{id} | 无 | 所有者、当前范围内审核人、工作人员或 OPEN 的认证用户；公开/普通工作人员视图隐藏 staffUserIds |
| 15 | PATCH /activities/{id} | ActivityEdit | 仅所有者 DRAFT；旧 lockVersion 拒绝；保存产生新版本 |
| 16 | POST /activities/{id}/submit | VersionCommand | 最新所有者身份、资源资格及工作人员有效性；SchedulingPort.check、EventPort |
| 17 | POST /activities/{id}/withdraw | ReasonCommand | 待审或已批准未预约时克隆新 DRAFT，保留旧版本；EventPort |
| 18 | POST /activities/{id}/approver-decision | ApprovalCommand | 当前 APPROVER 角色、组织及全部必要类别授权、本人回避；按版本保存审批；EventPort |
| 19 | POST /activities/{id}/resource-confirm | VersionCommand | 当前 ADMIN/RESOURCE_CONFIRM、全部资源范围、本人回避；SchedulingPort 联合检查与预约，EventPort、ChangePort 查询 |
| 20 | POST /activities/{id}/publish | VersionCommand | 所有者、SCHEDULED、未开始、无待处理改期、资源无风险；SchedulingPort、ChangePort、EventPort |
| 21 | POST /activities/{id}/cancel | ReasonCommand | 先关待处理改期，再释放预约、取消 ACTIVE 报名和活动，写收件人快照；ChangePort、SchedulingPort、EventPort |
| 22 | PUT /activities/{id}/registration | RegistrationCommand | 本人 APPLICANT/STUDENT；OPEN、未开始、有效版本匹配、无待处理改期、剩余容量；ChangePort |
| 23 | GET /activities/{id}/registrations | 无 | 所有者、范围完整的批准人或资源复核管理员；普通学生/工作人员无名单访问权 |

所有完整视图实时查询资源风险与 ACTIVE 人数。SCHEDULED/OPEN 视图通过 ChangePort 查询 activeChangeId。DRAFT/PENDING_APPROVER/APPROVED_UNRESERVED 按生命周期不可能有待处理改期，不需要 ChangePort 即可读取。缺少所需 CM 端口时明确返回 503 INTERNAL_ERROR，写事务回滚；不会假定“没有改期”或“事件已写入”。因此这些生产接口源码已完成，但当前 main 尚不能完成无替身的发布/报名/取消全链路。

## 给 CM 的 ActivityPort

- 实现类 `activity/ActivityPortAdapter.java` 是独立组件，不注入 ActivityService/Controller。只依赖本模块 Repository、ActivityViews 以及 SchedulingPort.risk/ChangePort.pendingId 的只读能力。
- get 返回完整内部 ActivitySnapshot，保留工作人员和全部 ACTIVE 报名 ID，myRegistrationStatus 固定 null。HTTP 裁剪不会影响 CM 的影响/收件人快照。
- lock/activatePlan/bumpLockVersion 都是 MANDATORY。调用方必须先持有活动锁。先锁主行、再读取当前版本，避免锁等待期间版本切换造成 JOIN 旧快照丢行。
- activatePlan 校验 baseVersionNo 和 SCHEDULED/OPEN；复制元数据，只替换 Plan，追加版本、更新 current/effective 版本及 ACTIVE 报名版本，lockVersion 加 1。该方法不写预约和事件。
- CM 仍需在同一事务依次调用 lock、按 UUID 排序锁用户、SchedulingPort.lockAndCheck、activatePlan、replaceReservations、EventPort.append。activatePlan 已增加锁版本，成功后不再重复 bump。
- 活动取消统一按 receipt→activity→change→user→resource 顺序执行，先调用 ChangePort.cancelPending，再按 UUID 排序 AccountPort.lock，最后释放资源、取消报名并写事件。合法所有者重复取消返回当前结果，不因旧 lockVersion 重复取消失败。

## 给 UI/QA 的版本与失败示例

API-12 创建结果为 versionNo=1、lockVersion=0、effectiveVersionNo=null。首次 PATCH `{expectedLockVersion:0,input:…}` 后为 versionNo=2、lockVersion=1；提交/审批/确认/发布每次只增加 lockVersion，确认后 effectiveVersionNo=2。撤回则克隆新版本并增加 lockVersion。

报名请求示例：`{"action":"JOIN","expectedVersionNo":2}`；退出改为 WITHDRAW。expectedVersionNo 比较有效安排版本，不是 lockVersion。重复 JOIN/重复 WITHDRAW 返回既有记录，不重复增加人数或 lockVersion；从未报名直接退出返回 INVALID_STATE。

| HTTP/业务码 | 典型原因 | UI处理 |
|---|---|---|
| 400 VALIDATION_ERROR | 参数、人员、时长、重复资源或原因不合法 | 保留输入，显示错误 |
| 401 UNAUTHENTICATED | 当前账号停用或未认证 | 重新登录 |
| 403 FORBIDDEN | 最新身份/权限/范围不足，或本人审批/复核 | 禁用对应操作并说明 |
| 403 RESOURCE_INELIGIBLE | 活动所有者当前身份不允许使用某资源 | 展示 details 中的受限资源，调整方案 |
| 409 STALE_VERSION | lockVersion 或有效安排版本已变化 | GET 最新实体后确认操作 |
| 409 INVALID_STATE | 非法状态、活动已开始或无报名可退出 | 刷新状态和按钮 |
| 409 CHANGE_PENDING | 改期冻结报名/退出，或待改期时尝试发布 | 展示改期入口与冻结原因 |
| 409 CAPACITY_FULL | 事务内真实人数已达容量 | 展示满额，不保留虚假成功 |
| 409 RESOURCE_CONFLICT | 联合确认或发布风险检查失败 | 展示 conflicts/资源风险 |
| 503 INTERNAL_ERROR | 所需 EventPort/ChangePort 实现尚未接入 | 展示服务未就绪，保留请求与幂等键 |

网络超时重试应复用同一幂等键和请求体。默认构建的 CM 种子编译问题、AN 实测命令/数量与 CM 替身范围见 [handoff](handoff.md) 最新记录。跨模块接收人：CM 杨瀚霖、UI 杨畅、QA 汤雨润、PM 冀项阳；接收确认：尚未接收。
