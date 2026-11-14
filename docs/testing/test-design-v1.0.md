# 校园活动协同与资源调度系统 软件测试设计（v1.0）

- 作者：汤雨润（55230316）
- 日期：2026-11-03
- 说明：本文全部用例未执行，不含执行结果与通过率；负面用例的预期结果是系统按规则拒绝或按规则降级且无非预期副作用，属于预期结果。

## 修订历史

| 日期 | 版本 | 说明 | 作者 |
|---|---|---|---|
| 2026-11-03 | 1.0 | 依据课程模板与需求规约编制：场景组合表、业务用例矩阵与详情、界面/性能/安全/安装专项用例 | 汤雨润（55230316） |

## 0 测试环境与基础数据

### 0.1 测试环境

1. 技术栈：Java 21、Spring Boot 3.5、Spring JDBC/Flyway、PostgreSQL 17；前端 Vue 3 + TypeScript + Element Plus；Node.js 22。
2. 测试框架：JUnit 5 + Spring Boot Test（连接真实 PostgreSQL，不用 H2 或 Mock 替代）、Playwright（Chromium）端到端测试。
3. 测试数据库：独立测试库 campus_test，每次测试使用独立数据集，迁移后装载测试数据；不对开发库清表。
4. 固定时钟：测试固定时钟 2030-10-19T00:00:00Z，演示日 D=2030-10-20（Asia/Shanghai），不依赖实际运行日期。
5. 接口约定：路由省略公共前缀 /api/v1；JSON 为 camelCase、UTF-8；时间为 ISO8601 带时区；成功返回 {data,requestId}，错误返回 {error:{code,message,details},requestId}。
6. 幂等约定：持久化 POST/PATCH/PUT 携带 UUID 格式 Idempotency-Key（登录/退出除外）；同用户、同键、同路由、同请求体重放返回首次完整响应。
7. 凭据约定：演示口令仅经环境变量注入，文档、日志与截图不出现明文密码或令牌。
8. 端到端环境：真实前后端，Chromium 默认 1366×768，必要时另测 1920×1080 与 390×844。
9. 并发与故障注入：并发使用独立连接/事务加起跑屏障；失败注入仅限测试上下文，不提供对外故障或重置接口。

### 0.2 基础数据字典

共用的组织、账号、资源与活动在此统一定义；实体以 12 位尾号别名引用 UUID，如 3101 即 `...000000003101`。

| 数据键 | 取值与含义 |
|---|---|
| UUID约定 | 所有 ID 为 UUID，统一形态 00000000-0000-0000-0000-XXXXXXXXXXXX（尾号左补零到 12 位）；本文以尾号别名引用，如 3101 即 ...000000003101 |
| 组织1001/1002 | 1001=软件学院；1002=其他学院（仅 approver_other 审批范围，越权测试用） |
| 2001 applicant_a | APPLICANT；identity=TEACHER；组织1001；活动4001所有者 |
| 2002 applicant_b | APPLICANT；identity=STUDENT；组织1001；活动4002所有者 |
| 2003 approver_a | APPROVER；identity=TEACHER；组织1001；审批范围=组织1001×{VENUE,PROJECTOR} |
| 2004 resource_admin | ADMIN；identity=TEACHER；permissions={RESOURCE_MANAGE,RESOURCE_CONFIRM,AUDIT_READ}；管理范围=3101/3102/3201/3202/3111-3114/3211-3214 |
| 2005 student_a | APPLICANT；identity=STUDENT；组织1001；已报名活动4001（ACTIVE） |
| 2006 staff_a | APPLICANT；identity=STUDENT；组织1001；活动4001工作人员（改期待确认对象） |
| 2007 account_admin | ADMIN；identity=TEACHER；permissions={USER_MANAGE}；无业务审批权与资源确认权 |
| 2008 approver_other | APPROVER；identity=TEACHER；审批范围仅组织1002×{VENUE,PROJECTOR}（越权测试） |
| 2009 student_b | APPLICANT；identity=STUDENT；组织1001；竞争名额与隐私测试 |
| 3101 学术报告厅A | VENUE；category=VENUE；capacity=80；quantity=1；open 08:00—22:00；allowTeacher=true、allowStudent=true |
| 3102 多功能厅B | VENUE；capacity=120；quantity=1；open 08:00—22:00；true/true |
| 3201 投影设备组A | EQUIPMENT；category=PROJECTOR；quantity=2；capacity=null；open 08:00—22:00；true/true |
| 3202 投影设备组B | EQUIPMENT；category=PROJECTOR；quantity=2；capacity=null；true/true |
| 3111/3211 | 资格组合①：allowTeacher=true、allowStudent=true（场地3111/设备3211，各 quantity 见下） |
| 3112/3212 | 资格组合②：仅教师（true/false） |
| 3113/3213 | 资格组合③：仅学生（false/true） |
| 3114/3214 | 资格组合④：两者均禁止（false/false）；场地 capacity=80/quantity=1/category=VENUE，设备 quantity=2/category=PROJECTOR，均 08:00—22:00 |
| 4001 活动A | 『校园技术分享会』；owner=2001；OPEN；versionNo=1；expectedAttendees=60、registrationCapacity=20；staff=[2006]；D 日 10:00—11:00，3101+3201×2，布撤场各 30 分钟（占用 09:30—11:30） |
| 4002 活动B | 『社团交流会』；owner=2002；SCHEDULED；versionNo=1；expectedAttendees=50、registrationCapacity=20；staff=[]；D 日 14:30—16:30，3101+3201×1，布撤场 0 |
| D / Clock | 演示日 D=2030-10-20（Asia/Shanghai）；自动测试 Clock 固定 2030-10-19T00:00:00Z |
| DEMO_PASSWORD | 演示口令仅经环境变量注入（BCrypt 编码入库）；文档/日志/JSON 不出现明文；短口令测试另用环境变量 TEST_SHORT_PASSWORD（<8 字符） |
| BASE_PLAN | 有效计划基准：startAt=2030-10-20T10:00:00+08:00、endAt=2030-10-20T11:00:00+08:00、setupMinutes=30、teardownMinutes=30、venueId=3101、equipment=[{resourceId:3201,quantity:2}]；占用区间 [09:30,11:30)。各用例只列与之不同的差异值 |
| BASE_ACTIVITY_INPUT | 有效活动基准：title='校园技术分享会（测试设计）'（≤100字）、description='公开简介（≤2000字）'、expectedAttendees=60、registrationCapacity=20、staffUserIds=[2006]、plan=BASE_PLAN。各用例只列差异值 |
| BASE_REGISTRATION | 有效报名基准：action=JOIN、expectedVersionNo=当前有效版本号 |
| BASE_CHANGE | 有效改期基准：baseVersionNo=当前 versionNo、expectedLockVersion=当前 lockVersion、reason='调整安排'（≥1字）、targetPlan=改期目标（3102+3202×2，D 日 15:00—16:00，布撤场各 30） |
| 4101 / 2101-2120 | 报名并发测试数据：活动4101『并发报名测试活动』OPEN、owner=2001、expectedAttendees=1、registrationCapacity=1、staff=[]、equipment=[]、D 日 18:00—19:00（布撤场 0，场地 3101）；学生 perf_s01～perf_s20（尾号 2101～2120，STUDENT） |

## 1 设计测试场景

对需求规约确定的每个用例（UC-01～UC-20）及界面、性能、安全、安装等专项，依据基本流与各备选流构造场景组合表；每个组合即该场景下的可执行用例。

### UC-01 登录与退出——场景组合表

**基本流**：1) 用户输入用户名与密码并提交；2) 系统检查账号启用状态及密码；3) 系统建立8小时有效会话并返回用户信息；4) 页面进入活动列表并按角色/权限展示导航；5) 用户选择退出；6) 系统删除当前会话，页面清除凭据并回到登录页。

**备选流1**：2a. 密码错误、账号停用或认证失败：显示统一认证错误且不建立会话（不区分账号是否存在）。

**备选流2**：3a. 会话过期或账号停用：后续请求 401 UNAUTHENTICATED 并要求重新登录。

**备选流3**：4a. 刷新页面时会话校验失败：回到登录页，不渲染业务数据。

**备选流4**：6a. 退出网络结果不明确：清除本地会话并提示服务器退出结果待核实，不展示虚假成功。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-01-TC01 | 正常登录、访问本人信息并退出 | 基本流 |
| UC-01-TC02 | 错误密码被统一拒绝且不建立会话 | 基本流+备选流2a |
| UC-01-TC03 | 停用账号拒绝登录且旧令牌即时失效 | 基本流+备选流2a+3a |
| UC-01-TC04 | 未登录访问业务接口被拒 | 基本流+备选流3a |
| UC-01-TC05 | 会话过期后请求被拒 | 基本流+备选流3a |
| UC-01-TC06 | 退出结果不明时不展示虚假成功 | 基本流+备选流6a |

### UC-02 管理账号身份与授权——场景组合表

**基本流**：1) 系统列出允许管理的账号；2) 管理员录入或选择目标账号；3) 明确身份、角色、批准组织和资源类别／资源管理范围；4) 系统校验数据及操作权限；5) 持久化并记录修改前后值；6) 页面显示最新授权和启用状态。

**备选流1**：4a. 用户名重复、组织不存在、创建密码不足 8 字符（NFR-08，且 UTF-8≤72 字节）或授权字段无效（星号通配、引用不存在的组织/资源类别）：400 VALIDATION_ERROR 并保留输入。

**备选流2**：4b. 仅有资源权限的管理员尝试管理账号：403 FORBIDDEN。

**备选流3**：5a. 停用账号使现存令牌立即失效。

**备选流4**：5b. 授权变更后按最新权限检查；身份变更后新申请与待资源复核事项重新检查申请资格。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-02-TC01 | 创建账号并记录授权前后值 | 基本流 |
| UC-02-TC02 | 用户名重复创建被拒 | 基本流+备选流4a |
| UC-02-TC03 | 创建密码不足 8 字符被拒 | 基本流+备选流4a |
| UC-02-TC04 | 仅有资源权限的管理员不能管理账号 | 基本流+备选流4b |
| UC-02-TC05 | 授权整体替换与权限清空语义 | 基本流 |
| UC-02-TC06 | 非法授权字段被拒 | 基本流+备选流4a |

### UC-03 查询活动及资源日历——场景组合表

**基本流**：1) 系统按角色和关系加载活动列表；2) 用户选择日期及资源；3) 展示场地占用、布场、主体、撤场和设备同时用量；4) 用户点击时间块；5) 明细展示其有权查看的信息及活动入口；6) 用户打开详情查看有效安排、版本、状态和实时风险。

**备选流1**：1a. 无数据时提供创建或调整筛选入口。

**备选流2**：1b. 列表超过 200 项时返回 truncated=true 并提示，不能显示为完整总数。

**备选流3**：2a. 日历请求区间超过 7 天：400 VALIDATION_ERROR 并提示调整范围。

**备选流4**：5a. 无权查看私有活动详情时只显示『已占用』及时间/资源/风险，无私有详情链接。

**备选流5**：6a. 对象不存在与无权限分别给出对应反馈，不泄露名单。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-03-TC01 | 本人/公开列表与活动详情 | 基本流 |
| UC-03-TC02 | 私有占用在日历脱敏且直连详情被拒 | 基本流+备选流5a |
| UC-03-TC03 | 日历区间超过 7 天被拒 | 基本流+备选流2a |
| UC-03-TC04 | 日历展示布撤场、设备同时用量与故障条目 | 基本流 |
| UC-03-TC05 | 列表截断标志与提示 | 基本流+备选流1b |

### UC-04 维护资源及申请资格——场景组合表

**基本流**：1) 选择场地或设备并填写资料；2) 设置教师和学生分别是否可申请；3) 系统检查容量、库存和时段；4) 保存资源；5) 仅调整身份资格时保存新资格及前后值记录；6) 查询页展示资格状态和受限原因。

**备选流1**：3a. 场地 quantity 不为 1、容量非正、设备 capacity 非空、库存非正或开放时间逆序：400 VALIDATION_ERROR。

**备选流2**：5a. 调整资格后，未完成资源确认的申请按最新资格检查。

**备选流3**：5b. 已生效预约保留，不因资格调整自动删除。

**备选流4**：5c. 创建后的容量、库存、时段、名称或类别首版不提供修改入口。

**备选流5**：6a. 两类身份均关闭时，拒绝新增申请。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-04-TC01 | 新增场地并自动纳入创建者管理范围 | 基本流 |
| UC-04-TC02 | 非法资源资料被拒（逐项单变量） | 基本流+备选流3a |
| UC-04-TC03 | 资格调整记录前后值且无基本资料修改入口 | 基本流步骤5+5c |
| UC-04-TC04 | 学生所有者申请仅教师资源被拒且不占资源 | 基本流+备选流6a |
| UC-04-TC05 | 资格关闭后新申请被拒但既有预约保留 | 基本流+备选流5a+5b+6a |
| UC-04-TC06 | 资格八组合矩阵（2 身份×4 组合） | 基本流+备选流6a |

### UC-05 创建或编辑活动草稿——场景组合表

**基本流**：1) 填写基本信息；2) 选择时间和符合身份资格的资源；3) 指定本组织工作人员；4) 系统校验字段和人员；5) 新建保存版本 1 或编辑保存新不可变版本；6) 获得 activityId 后可执行资源预览，页面显示草稿及校验结果。

**备选流1**：4a. 时间在过去、时长越界、人数不符、资源类型错误或人员无效：400 VALIDATION_ERROR 贴近字段提示。

**备选流2**：4b. 资源资格受限显示原因，提交前必须更换。

**备选流3**：5a. 非草稿禁止直接修改；已预约活动提示改期入口。

**备选流4**：5b. 旧 lockVersion 返回 409 STALE_VERSION，保留输入并加载最新状态。

**备选流5**：6a. 存在资源占用冲突可保存符合输入约束的草稿，但不得称为已预约。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-05-TC01 | 创建草稿形成版本 1 且不占资源 | 基本流 |
| UC-05-TC02 | 编辑草稿产生不可变新版本 | 基本流 |
| UC-05-TC03 | 字段边界逐项校验拒绝非法草稿 | 基本流+备选流4a |
| UC-05-TC04 | 非所有者编辑被拒 | 基本流+BR-02 |
| UC-05-TC05 | 非草稿禁止直接修改 | 基本流+备选流5a |
| UC-05-TC06 | 旧 lockVersion 编辑被拒且不覆盖 | 基本流+备选流5b |

### UC-06 提交或撤回活动申请——场景组合表

**基本流**：1) 用户确认当前内容；2) 系统校验身份资格、完整内容、状态和权限；3) 提交时绑定当前版本，转 PENDING_APPROVER；4) 保存提交记录和事件；5) 撤回时说明原因，复制当前内容生成新版本 DRAFT；6) 页面显示待审批或新草稿及过程记录。

**备选流1**：2a. 任一资源资格受限拒绝提交且不占资源。

**备选流2**：2b. 并发审批或他人更新导致版本过期：409 STALE_VERSION，刷新后重新选择操作。

**备选流3**：5a. 已预约或已发布禁止此撤回方式，应改期或取消。

**备选流4**：5b. 旧审批保留历史但不对新版本有效。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-06-TC01 | 提交进入待业务审批且不占资源 | 基本流 |
| UC-06-TC02 | 资格受限提交被拒且不占资源 | 基本流+备选流2a |
| UC-06-TC03 | 撤回生成新草稿并使旧审批失效 | 基本流步骤5+备选流5b |
| UC-06-TC04 | 已发布活动禁止撤回式收回 | 基本流+备选流5a |
| UC-06-TC05 | 陈旧版本提交被拒 | 基本流+备选流2b |

### UC-07 审批活动申请——场景组合表

**基本流**：1) 查阅待审批的内容版本和安排；2) 填写意见并同意；3) 系统复核权限、回避及版本；4) 保存决定、审批人和时间；5) 活动变 APPROVED_UNRESERVED；6) 页面显示业务已批准、资源尚未确认。

**备选流1**：2a. 驳回必须说明理由（≥1 字），记录后回 DRAFT。

**备选流2**：3a. 仅有教师身份、组织不符、类别不在范围或本人活动均拒绝（403）。

**备选流3**：3b. 已撤回或其他人已处理，禁止覆盖，显示当前状态（409）。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-07-TC01 | 批准人同意（空意见合法） | 基本流 |
| UC-07-TC02 | 本人回避与跨组织审批被拒 | 基本流+备选流3a |
| UC-07-TC03 | 审批范围不全（缺设备类别）被拒 | 基本流+备选流3a |
| UC-07-TC04 | 空理由驳回被拒 | 基本流+备选流2a |
| UC-07-TC05 | 驳回回草稿并保留意见 | 基本流+备选流2a |
| UC-07-TC06 | 撤回后旧审批请求被拒 | 基本流+备选流3b |

### UC-08 确认首次资源预约——场景组合表

**基本流**：1) 查看批准的具体版本；2) 确认资源；3) 系统复核最新身份资格、时间、容量、设备峰值及故障；4) 全部可行时一次事务建立主场地和设备预约；5) 活动变 SCHEDULED 并设置有效版本；6) 返回完整预约结果和记录。

**备选流1**：3a. 任一资源冲突：返回详细冲突，保持 APPROVED_UNRESERVED、无部分预约（409）。

**备选流2**：3b. 资格已调整或权限不足：拒绝且不产生预约（403）。

**备选流3**：3c. 两个活动同时抢相同场地：最多一个成功，另一个资源冲突。

**备选流4**：4a. 数据库异常整体回滚，无部分占用。

**备选流5**：6a. 重复相同命令重放原结果，不重复占用。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-08-TC01 | 首次联合预约成功并建立完整占用 | 基本流 |
| UC-08-TC02 | 设备缺件时联合确认失败且无部分预约 | 基本流+备选流3a |
| UC-08-TC03 | 两活动并发抢同一场地恰一成功 | 基本流+备选流3c |
| UC-08-TC04 | 同键同体重放返回原结果 | 基本流+备选流6a |
| UC-08-TC05 | 同键不同体复用被拒 | 基本流+备选流6a/BR-29 |
| UC-08-TC06 | 资格调整后确认被拒 | 基本流+备选流3b |
| UC-08-TC07 | 写入异常整体回滚且允许重试 | 基本流+备选流4a |
| UC-08-TC08 | 所有者兼任资源确认管理员对本人首次确认被拒 | 基本流+备选流3a |

### UC-09 发布活动——场景组合表

**基本流**：1) 查阅已确认安排；2) 点击发布；3) 系统重新检查状态、时间和风险；4) 活动变 OPEN；5) 公开列表可查看安排和报名容量。

**备选流1**：3a. 未确认资源、已开始、故障风险或无权限，拒绝并说明（409/403）。

**备选流2**：3b. 状态已变化，刷新后展示最新结果。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-09-TC01 | 发布并对外可见 | 基本流 |
| UC-09-TC02 | 未资源确认不得发布 | 基本流+备选流3a |
| UC-09-TC03 | 资源风险 AT_RISK 时拒绝发布 | 基本流+备选流3a |
| UC-09-TC04 | 非所有者发布被拒 | 基本流+备选流3a |

### UC-10 报名或退出活动——场景组合表

**基本流**：1) 展示有效报名人数、容量和本人状态；2) 用户点击报名；3) 系统检查状态、版本及剩余容量；4) 保存本人 ACTIVE 关系并更新并发版本；5) 页面持续显示已报名及人数；6) 用户退出时把本人关系改为 WITHDRAWN 并释放名额。

**备选流1**：3a. 满额为 409 CAPACITY_FULL，不新增报名。

**备选流2**：3b. 20 个不同学生抢 1 个名额，恰 1 成功。

**备选流3**：3c. 有待处理改期时返回 409 CHANGE_PENDING。

**备选流4**：3d. 有效版本已变返回 409 STALE_VERSION。

**备选流5**：4a. 本人已 ACTIVE 重复 JOIN 返回原记录。

**备选流6**：6a. 已 WITHDRAWN 重复退出返回原记录；无报名历史退出为 409 INVALID_STATE。

**备选流7**：6b. 退出后重新报名需重新检查名额。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-10-TC01 | 学生报名成功并更新并发版本 | 基本流 |
| UC-10-TC02 | 20 个学生并发抢 1 个名额恰 1 成功 | 基本流+备选流3b |
| UC-10-TC03 | 重复报名与重复退出返回原记录 | 基本流+备选流4a+6a |
| UC-10-TC04 | 无报名历史退出被拒 | 基本流+备选流6a |
| UC-10-TC05 | 待处理改期冻结报名与退出 | 基本流+备选流3c |
| UC-10-TC06 | 陈旧 expectedVersionNo 报名被拒 | 基本流+备选流3d |
| UC-10-TC07 | 身份或状态不符的报名被拒 | 基本流+BR-15 |

### UC-11 查看冲突并比较备选——场景组合表

**基本流**：1) 系统验证输入、权限和资格；2) 检查主场地及全部设备；3) 展示每项冲突区间、需求、可用数及原因；4) 在规定范围生成符合资格和硬约束的备选；5) 返回最多 5 项及排序理由、搜索范围和截断标志；6) 用户选 2～3 项比较时间变化、场地和设备替换；7) 所有者选择目标，用于后续草稿编辑或改期影响预览。

**备选流1**：2a. 排除同一活动自身旧预约，不能误判自身为竞争者。

**备选流2**：4a. 无方案时说明仅当前搜索范围未找到，引导人工调整。

**备选流3**：4b. 候选不足 300 仍可返回，达预算截断要说明。

**备选流4**：4c. 同类替换资源无申请资格应过滤。

**备选流5**：7a. 选择不代表占位，不直接预约。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-11-TC01 | 端点相接与设备峰值统计正确 | 基本流 |
| UC-11-TC02 | 冲突解释精确到区间与数量 | 基本流+BR-17 |
| UC-11-TC03 | 首选替代方案与排序元数据 | 基本流步骤4-7+备选流7a |
| UC-11-TC04 | 自身旧占用被排除不自竞争 | 基本流+备选流2a |
| UC-11-TC05 | 候选过滤资格受限资源且强制提交仍被拒 | 基本流+备选流4c |

### UC-12 预览并创建改期申请——场景组合表

**基本流**：1) 显示当前有效安排和实时风险；2) 用户输入目标并查看冲突及备选；3) 选定目标后重新预览该目标的影响；4) 展示资源差异、审批重置、待确认人员、报名人数及通知影响；5) 用户填写原因并确认最后目标；6) 系统锁定并复核版本、权限、最新资格和无待处理改期；7) 保存不可变目标、影响快照、工作人员待办和事件；8) 有其他工作人员进入 AWAITING_CONFIRMATIONS，否则直接 PENDING_APPROVER；9) 暂停该活动报名及退出。

**备选流1**：3a. 选择了新备选必须重新计算，不能提交此前目标。

**备选流2**：6a. lockVersion 过期返回 409 STALE_VERSION 并重新展示最新影响。

**备选流3**：6b. 已有待处理改期或目标资源资格受限，拒绝。

**备选流4**：6c. 目标资源仍有冲突可创建，但应明确未占位、最终可能失败。

**备选流5**：7a. 数据库异常整体回滚，不遗留冻结。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-12-TC01 | 改期影响预览并按最后选择重算 | 基本流步骤1-4+备选流3a |
| UC-12-TC02 | 创建改期进入工作人员确认并冻结报名 | 基本流步骤5-9 |
| UC-12-TC03 | 仅所有者为工作人员时直达复审 | 基本流步骤8 |
| UC-12-TC04 | 每活动仅一个待处理改期 | 基本流+备选流6b |
| UC-12-TC05 | 目标资格受限被拒且无冻结 | 基本流+备选流6b |
| UC-12-TC06 | 陈旧版本创建改期被拒 | 基本流+备选流6a |
| UC-12-TC07 | 带冲突目标仍可创建但不占位 | 基本流+备选流6c/BR-20 |

### UC-13 确认工作人员改期待办——场景组合表

**基本流**：1) 查看原新安排、影响和实时风险；2) 本人同意；3) 系统保存本人决定和时间；4) 未全同意仍待其他人；5) 全部同意进入 PENDING_APPROVER，页面更新时间线。

**备选流1**：2a. 拒绝需理由（≥1 字），改期 REJECTED 并解除冻结，原安排保留。

**备选流2**：3a. 重复相同决定返回当前结果，无重复事件。

**备选流3**：3b. 已决定后反向决定为 409 ALREADY_DECIDED。

**备选流4**：3c. 非名单人员或替别人确认拒绝（403）。

**备选流5**：3d. 活动已取消或申请已终结，不接受新决定。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-13-TC01 | 工作人员同意并推进状态 | 基本流 |
| UC-13-TC02 | 空理由拒绝被拒 | 基本流+备选流2a |
| UC-13-TC03 | 工作人员有效拒绝终止改期 | 基本流+备选流2a |
| UC-13-TC04 | 已决定后反向决定被拒 | 基本流+备选流3b+3d |
| UC-13-TC05 | 重复相同决定幂等 | 基本流+备选流3a |
| UC-13-TC06 | 非名单人员确认被拒 | 基本流+备选流3c |
| UC-13-TC07 | 停用工作人员确认被拒且不代签 | 基本流+备选流3c |

### UC-14 复审改期申请——场景组合表

**基本流**：1) 查阅不可变目标、影响和工作人员确认；2) 同意目标；3) 系统复核权限、状态及基础版本；4) 将决定绑定该改期并记录时间；5) 状态 PENDING_RESOURCE。

**备选流1**：2a. 驳回并说明理由（≥1 字），REJECTED，原预约保留且解除冻结。

**备选流2**：3a. 申请已撤回、被取消或越权，拒绝。

**备选流3**：4a. 目标变化需要新申请，旧意见不能复用。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-14-TC01 | 复审同意进入资源待确认 | 基本流 |
| UC-14-TC02 | 复审驳回保护原安排 | 基本流+备选流2a |
| UC-14-TC03 | 越权或范围不全复审被拒 | 基本流+备选流3a |
| UC-14-TC04 | 终态后复审被拒且意见不复用 | 基本流+备选流3a+4a |
| UC-14-TC05 | 所有者兼任批准人对本人改期复审被拒 | 基本流+备选流3a |

### UC-15 最终确认并切换改期——场景组合表

**基本流**：1) 查看原新安排、审批和实时风险；2) 管理员确认；3) 系统锁活动、改期及新旧资源并重查资格和资源；4) 资源可行时新建活动内容版本；5) 同一事务释放旧预约、写新预约、更新有效报名版本、置 APPLIED 及 appliedVersionNo，保存审计和 outbox；6) 提交后刷新有效安排和日历；7) 通知异步送达相关人员。

**备选流1**：2a. 管理员拒绝并说明理由（≥1 字），REJECTED、原安排不变。

**备选流2**：3a. 预览后资源被抢占：409 RESOURCE_CONFLICT、改期持久化 CONFLICTED 及冲突明细，原版本和预约保留（业务提交，非 SQL 回滚）。

**备选流3**：3b. 最新资格不符，拒绝切换并说明受限资源，原有效安排保留。

**备选流4**：3c. 活动取消已生效或基础版本过期，拒绝旧请求。

**备选流5**：5a. 任一步数据库异常，版本、预约、报名、改期、审计和 outbox 全部回滚。

**备选流6**：7a. 通知投递失败独立重试，不撤回已提交安排。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-15-TC01 | 最终确认原子切换到新安排 | 基本流 |
| UC-15-TC02 | 预览后被抢占置 CONFLICTED 且原安排保留 | 基本流+备选流3a |
| UC-15-TC03 | 切换中异常整体回滚且可重试 | 基本流+备选流5a |
| UC-15-TC04 | 管理员拒绝保护原安排 | 基本流+备选流2a |
| UC-15-TC05 | 最新资格不符或本人复核回避 | 基本流+备选流3b |
| UC-15-TC06 | 活动取消后旧确认请求被拒 | 基本流+备选流3c |
| UC-15-TC07 | 目标与自身旧占用重叠仍可生效 | 基本流+BR-25 |
| UC-15-TC08 | 资源确认范围仅覆盖部分必要资源被拒（API-19/API-32 双子例） | 基本流+备选流3b |

### UC-16 撤回改期——场景组合表

**基本流**：1) 用户查看目标和进度；2) 填写原因并确认撤回；3) 系统验证权限及状态；4) 改期 WITHDRAWN 并保存事件；5) 页面显示已撤回，报名冻结解除。

**备选流1**：3a. 已生效/已拒绝/已冲突等终态不能撤回，提示当前状态。

**备选流2**：3b. 与最终确认并发时以一致性检查后的状态处理，不撤销已提交的新安排。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-16-TC01 | 所有者撤回待处理改期 | 基本流 |
| UC-16-TC02 | 非所有者撤回被拒 | 基本流+BR-02 |
| UC-16-TC03 | 终态改期不能撤回 | 基本流+备选流3a |
| UC-16-TC04 | 撤回与最终确认并发保持一致终态 | 基本流+备选流3b |

### UC-17 取消活动——场景组合表

**基本流**：1) 展示取消将关闭改期、释放资源、取消有效报名；2) 所有者填写原因并确认；3) 系统保存原人员通知快照；4) 一次事务关闭待改期、释放预约、活动 CANCELLED、有效报名 CANCELLED 并写事件；5) 页面持续显示取消结果；6) 后续通知异步发送。

**备选流1**：2a. 用户关闭确认框不执行。

**备选流2**：3a. 其他人取消或活动已开始，拒绝。

**备选流3**：4a. 与改期最终确认竞争，最终取消不得被旧请求恢复。

**备选流4**：4b. 合法所有者再次取消返回当前结果，不增加释放和通知。

**备选流5**：4c. 执行异常整体回滚，可检查后重试。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-17-TC01 | 取消活动释放全部关联 | 基本流 |
| UC-17-TC02 | 重复取消返回当前结果不重复副作用 | 基本流+备选流4b |
| UC-17-TC03 | 取消与改期最终确认并发一致 | 基本流+备选流4a |
| UC-17-TC04 | 非所有者或已开始活动取消被拒 | 基本流+备选流3a |
| UC-17-TC05 | 取消异常整体回滚可重试 | 基本流+备选流4c |
| UC-17-TC06 | 已取消活动不得被旧请求复活 | 基本流+BR-28 |

### UC-18 登记故障及查看影响——场景组合表

**基本流**：1) 输入故障；2) 系统校验时间和数量；3) 保存故障记录；4) 从当前有效预约计算受影响活动；5) 展示清单和 AT_RISK；6) 日历显示不可用数量，授权申请人在详情看到实时风险并自行决定改期或取消。

**备选流1**：2a. 数量≤0、超过库存或场地数量不为 1，拒绝（400）。

**备选流2**：4a. 无受影响活动展示空清单。

**备选流3**：4b. 重叠故障使可用量最低为 0。

**备选流4**：5a. 普通用户日历只见『资源不可用』而非私有原因。

**备选流5**：6a. 后续改期失败，原预约仍保留风险。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-18-TC01 | 登记故障并计算受影响活动 | 基本流 |
| UC-18-TC02 | 非法故障参数被拒（逐项单变量） | 基本流+备选流2a |
| UC-18-TC03 | 越权登记故障被拒 | 基本流+权限 |
| UC-18-TC04 | 故障下改期最终冲突且原安排与风险保留 | 基本流+备选流6a |
| UC-18-TC05 | 重叠故障可用量下限与日历脱敏 | 基本流+备选流4b+5a |

### UC-19 读取本人通知——场景组合表

**基本流**：1) 系统加载本人通知；2) 展示事件、活动、关联版本、内容及时间；3) 用户打开消息；4) 持久化 readAt；5) 在仍有对象访问权限时，可跳转活动或改期详情执行对应操作。

**备选流1**：1a. 无通知展示空状态。

**备选流2**：3a. 他人通知拒绝访问（403/404）。

**备选流3**：4a. 重复已读不改变审批或确认。

**备选流4**：5a. 权限已变化或对象不可访问，解释原因，不因消息跳转绕过授权。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-19-TC01 | 读取并标记本人通知 | 基本流 |
| UC-19-TC02 | 他人通知拒绝读取与已读 | 基本流+备选流3a |
| UC-19-TC03 | 消费失败恢复与重复消费去重 | 基本流+BR-31 |
| UC-19-TC04 | 已读不替代工作人员确认 | 基本流+备选流4a |
| UC-19-TC05 | 空状态与跳转授权复核 | 基本流+备选流1a+5a |

### UC-20 查询报名及业务历史——场景组合表

**基本流**：1) 查询当前活动；2) 按职责请求名单或审计；3) 展示有效报名数及授权名单；4) 按记录展示操作者、时间、内容版本、决定和原因；5) 对比改期历史及其生效版本，核对当前安排来源。

**备选流1**：2a. 普通学生只看本人报名，不能请求全名单。

**备选流2**：2b. 工作人员只看到确认所需内容。

**备选流3**：3a. 列表达到 200 项提示截断，人数使用真实计数。

**备选流4**：4a. 无记录说明尚无操作，不能展示示例为真实历史。


| 场景编号 | 场景简称 | 事件流组合 |
|---|---|---|
| UC-20-TC01 | 名单、审计与改期历史查询 | 基本流 |
| UC-20-TC02 | 普通学生不能请求全名单 | 基本流+备选流2a |
| UC-20-TC03 | 工作人员视图裁剪影响名单 | 基本流+备选流2b |
| UC-20-TC04 | 审计越范围查询被拒 | 基本流+权限 |
| UC-20-TC05 | 无记录与截断语义如实 | 基本流+备选流3a+4a |

## 2 根据场景构造测试用例

每个场景组给出用例矩阵与逐条用例详情（前置条件、输入条件、操作步骤、预期结果）。

### UC-01 登录与退出——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-01-TC01 | 正常登录、访问本人信息并退出 | 正面 | P0 | 基本流 |
| UC-01-TC02 | 错误密码被统一拒绝且不建立会话 | 负面 | P0 | 基本流+备选流2a |
| UC-01-TC03 | 停用账号拒绝登录且旧令牌即时失效 | 负面 | P0 | 基本流+备选流2a+3a |
| UC-01-TC04 | 未登录访问业务接口被拒 | 负面 | P1 | 基本流+备选流3a |
| UC-01-TC05 | 会话过期后请求被拒 | 负面 | P1 | 基本流+备选流3a |
| UC-01-TC06 | 退出结果不明时不展示虚假成功 | 负面 | P2 | 基本流+备选流6a |

#### UC-01-TC01 正常登录、访问本人信息并退出

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) demo.json 已装载（含 2001 applicant_a）
  2) 测试 Clock=2030-10-19T00:00:00Z
  3) 客户端当前无有效令牌

- 输入条件：
  1) API-01 POST /auth/login，body={username:'applicant_a',password:$DEMO_PASSWORD}（口令仅环境变量注入）
  2) API-02 GET /auth/me（携带返回 token）
  3) API-03 POST /auth/logout

- 操作步骤：
  1) 1) 调用 API-01 登录，记录响应与 requestId
  2) 2) 递归扫描登录响应 JSON 的字段名
  3) 3) 携带 token 调用 API-02
  4) 4) 调用 API-03 退出后，用同一 token 再调用 API-02

- 预期结果：
  1) API-01 返回 200：data={token,expiresAt,user}，user.roles=['APPLICANT']、identity=TEACHER、organizationId=组织1001，expiresAt 为带时区 ISO8601 且约 8 小时后
  2) API-02 返回 200，data 即 Actor（无嵌套 user），data.id 与登录返回的 user.id 一致；登录/me 响应任意层级无 password、passwordHash 字段；auth_session 新增 1 行且 token_hash≠token 明文
  3) API-03 返回 200 {ok:true}；退出后同一 token 请求 API-02 返回 401 UNAUTHENTICATED（旧令牌失效）


#### UC-01-TC02 错误密码被统一拒绝且不建立会话

- 极性：负面；优先级：P0；事件流：基本流+备选流2a

- 前置条件：
  1) 同 TC01 前置；账号 2001 启用且口令正确
  2) 唯一改变变量：请求体 password 字段取错误值

- 输入条件：
  1) API-01 POST /auth/login，body={username:'applicant_a',password:'<错误值>'}
  2) 随后用 $DEMO_PASSWORD 对照登录一次

- 操作步骤：
  1) 1) 以错误密码调用 API-01
  2) 2) 检查错误信封字段
  3) 3) 查 auth_session 是否新增行
  4) 4) 用正确密码对照登录

- 预期结果：
  1) 401 UNAUTHENTICATED，错误信封 {error:{code,message,details},requestId}，message 为统一认证错误（不区分密码错或账号不存在）
  2) auth_session 无新增行；响应不含 passwordHash；对照登录 200（账号未被锁死）


#### UC-01-TC03 停用账号拒绝登录且旧令牌即时失效

- 极性：负面；优先级：P0；事件流：基本流+备选流2a+3a

- 前置条件：
  1) account_admin（2007）已登录；student_b（2009）已登录并持有旧令牌 T
  2) 唯一改变变量：2009 的 enabled

- 输入条件：
  1) API-06 PATCH /users/{2009}/enabled，body={enabled:false}（2007 令牌）
  2) 携旧令牌 T 调用 API-02 GET /auth/me
  3) API-01 POST /auth/login {username:'student_b',password:$DEMO_PASSWORD}

- 操作步骤：
  1) 1) 记录 T
  2) 2) 停用 2009
  3) 3) 用 T 调用 API-02
  4) 4) 尝试登录
  5) 5) 重新启用 2009

- 预期结果：
  1) API-06 返回 200 {ok:true}，app_user.enabled=false
  2) 旧令牌 T 请求 API-02 返回 401 UNAUTHENTICATED（停用即时使会话失效）
  3) 登录返回 401 UNAUTHENTICATED 且不建立会话


#### UC-01-TC04 未登录访问业务接口被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3a

- 前置条件：
  1) 客户端无任何令牌
  2) 唯一改变变量：Authorization 头缺失

- 输入条件：
  1) GET /activities?scope=mine（无 Authorization 头）
  2) API-37 GET /health（对照，应免登录）

- 操作步骤：
  1) 1) 无令牌调用 GET /activities
  2) 2) 无令牌调用 API-37

- 预期结果：
  1) GET /activities 返回 401 UNAUTHENTICATED，不返回任何业务数据
  2) API-37 返回 200 {status:'UP'}（免登录例外）


#### UC-01-TC05 会话过期后请求被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3a

- 前置条件：
  1) student_a（2005）已登录取得令牌 T
  2) 唯一改变变量：会话过期时间（测试支撑将 auth_session.expires_at 置为过去时刻，模拟 8 小时到期）

- 输入条件：
  1) 测试支撑：expires_at 置为 2030-10-19T00:00:00Z 之前
  2) 携 T 调用 API-02 GET /auth/me
  3) 重新 API-01 登录

- 操作步骤：
  1) 1) 使会话过期
  2) 2) 用 T 调用 API-02
  3) 3) 重新登录后再调用 API-02

- 预期结果：
  1) 过期令牌请求 401 UNAUTHENTICATED 并要求重新登录
  2) 重新登录 200 且新令牌可用，旧令牌不再有效


#### UC-01-TC06 退出结果不明时不展示虚假成功

- 极性：负面；优先级：P2；事件流：基本流+备选流6a

- 前置条件：
  1) E2E 环境（qa/tests），applicant_a 已登录
  2) 唯一改变变量：网络条件（拦截 API-03 超时）

- 输入条件：
  1) Playwright 路由拦截使 POST /auth/logout 超时
  2) UI 点击『退出』

- 操作步骤：
  1) 1) 拦截 logout
  2) 2) 点击退出，观察提示与跳转
  3) 3) 恢复网络后核实服务端会话状态

- 预期结果：
  1) 拒绝展示虚假成功：页面提示服务器退出结果待核实，不显示『已成功退出』类文案
  2) 本地令牌清除并回到 /login 页；恢复后旧令牌请求 API-02 按服务端实际会话状态如实展示，不捏造退出结果


### UC-02 管理账号身份与授权——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-02-TC01 | 创建账号并记录授权前后值 | 正面 | P0 | 基本流 |
| UC-02-TC02 | 用户名重复创建被拒 | 负面 | P0 | 基本流+备选流4a |
| UC-02-TC03 | 创建密码不足 8 字符被拒 | 负面 | P0 | 基本流+备选流4a |
| UC-02-TC04 | 仅有资源权限的管理员不能管理账号 | 负面 | P1 | 基本流+备选流4b |
| UC-02-TC05 | 授权整体替换与权限清空语义 | 正面 | P1 | 基本流 |
| UC-02-TC06 | 非法授权字段被拒 | 负面 | P1 | 基本流+备选流4a |

#### UC-02-TC01 创建账号并记录授权前后值

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) account_admin（2007）已登录
  2) 用户名 temp_user_01 不存在；组织 1001 存在

- 输入条件：
  1) API-38 GET /organizations
  2) API-05 POST /users，body=UserCreate{username:'temp_user_01',displayName:'临时用户01',password:$DEMO_PASSWORD,organizationId:组织1001,identity:'STUDENT',roles:['APPLICANT'],permissions:[],approvalScopes:[],managedResourceIds:[]}
  3) API-04 GET /users（无 purpose）

- 操作步骤：
  1) 1) 查询组织
  2) 2) 创建账号
  3) 3) 列出账号核对
  4) 4) 查 authorization_change

- 预期结果：
  1) API-38 返回 200 含组织 1001/1002
  2) API-05 返回 201 Actor，roles/identity/范围与请求一致；API-04 列表可见新账号
  3) authorization_change 新增 1 行，含 before_json/after_json


#### UC-02-TC02 用户名重复创建被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流4a

- 前置条件：
  1) 同 TC01 前置；applicant_a（2001）已存在
  2) 唯一改变变量：username 取已存在值

- 输入条件：
  1) API-05 POST /users，body=TC01 同构（差异：username:'applicant_a'）

- 操作步骤：
  1) 1) 提交创建
  2) 2) 查 app_user 行数

- 预期结果：
  1) 400 VALIDATION_ERROR，details 指向 username 重复
  2) 无新 app_user 行


#### UC-02-TC03 创建密码不足 8 字符被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流4a

- 前置条件：
  1) 同 TC01 前置
  2) 唯一改变变量：password 取环境变量 TEST_SHORT_PASSWORD（<8 字符）

- 输入条件：
  1) API-05 POST /users，body=TC01 同构（差异：password:$TEST_SHORT_PASSWORD）

- 操作步骤：
  1) 1) 提交创建
  2) 2) 查库并扫描响应

- 预期结果：
  1) 400 VALIDATION_ERROR（NFR-08：创建密码至少 8 字符）
  2) 无新 app_user 行；响应与日志不出现口令明文


#### UC-02-TC04 仅有资源权限的管理员不能管理账号

- 极性：负面；优先级：P1；事件流：基本流+备选流4b

- 前置条件：
  1) resource_admin（2004，无 USER_MANAGE）已登录
  2) 唯一改变变量：调用者令牌

- 输入条件：
  1) API-05 POST /users（2004 令牌，body 同 TC01）
  2) API-39 PATCH /users/{2009}/authorization（2004 令牌）

- 操作步骤：
  1) 1) 两次调用
  2) 2) 核对 2009 授权数据

- 预期结果：
  1) 两次均 403 FORBIDDEN
  2) app_user/user_role/user_permission 无变化，authorization_change 无新增


#### UC-02-TC05 授权整体替换与权限清空语义

- 极性：正面；优先级：P1；事件流：基本流

- 前置条件：
  1) account_admin 已登录；2009 当前 roles=['APPLICANT']
  2) 唯一改变变量：API-39 请求体授权字段

- 输入条件：
  1) API-39 PATCH /users/{2009}/authorization，body=AuthorizationInput{identity:'STUDENT',roles:['APPLICANT','ADMIN'],permissions:['AUDIT_READ'],approvalScopes:[],managedResourceIds:[]}
  2) 再次以 roles=['APPLICANT'],permissions:[] 整体替换

- 操作步骤：
  1) 1) 赋予 ADMIN+AUDIT_READ
  2) 2) 读取 Actor 核对
  3) 3) 再整体替换为仅 APPLICANT
  4) 4) 查 user_permission 与 authorization_change

- 预期结果：
  1) 两次均 200 Actor；第二次后 user_permission 无 AUDIT_READ 行（完整替换、不隐式保留）
  2) authorization_change 记录两次修改的前后值


#### UC-02-TC06 非法授权字段被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流4a

- 前置条件：
  1) account_admin 已登录；2009 授权已知
  2) 唯一改变变量：approvalScopes 内容（星号通配）

- 输入条件：
  1) API-39 PATCH /users/{2009}/authorization，body=TC05 第二次同构（差异：approvalScopes:[{organizationId:'*',resourceCategories:['*']}]）

- 操作步骤：
  1) 1) 提交
  2) 2) 核对授权数据

- 预期结果：
  1) 400 VALIDATION_ERROR（不接受星号通配，组织/类别必须有效）
  2) 授权数据无变化


### UC-03 查询活动及资源日历——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-03-TC01 | 本人/公开列表与活动详情 | 正面 | P0 | 基本流 |
| UC-03-TC02 | 私有占用在日历脱敏且直连详情被拒 | 负面 | P0 | 基本流+备选流5a |
| UC-03-TC03 | 日历区间超过 7 天被拒 | 负面 | P1 | 基本流+备选流2a |
| UC-03-TC04 | 日历展示布撤场、设备同时用量与故障条目 | 正面 | P1 | 基本流 |
| UC-03-TC05 | 列表截断标志与提示 | 正面 | P2 | 基本流+备选流1b |

#### UC-03-TC01 本人/公开列表与活动详情

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) applicant_a 已登录；4001（OPEN）、4002（SCHEDULED）已装载

- 输入条件：
  1) API-13 GET /activities?scope=mine
  2) API-13 GET /activities?scope=public
  3) API-14 GET /activities/{4001}

- 操作步骤：
  1) 1) 分别查询 mine 与 public
  2) 2) 打开 4001 详情
  3) 3) 核对版本、状态、计数与风险字段

- 预期结果：
  1) mine 返回本人所有、作为工作人员参与及本人报名历史的并集；public 返回公开活动
  2) 4001 详情含 status=OPEN、versionNo=1、effectiveVersionNo=1、registrationCount（按 ACTIVE 计数）、resourceRisk（查询时计算）


#### UC-03-TC02 私有占用在日历脱敏且直连详情被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流5a

- 前置条件：
  1) student_b 已登录；测试数据建立一条 student_b 无权查看的私有活动占用 3102（D 日）
  2) 唯一改变变量：查看者对该活动的权限

- 输入条件：
  1) API-09 GET /resources/calendar?from=2030-10-20T00:00:00+08:00&to=2030-10-21T00:00:00+08:00&resourceId=3102
  2) API-14 GET /activities/{该私有活动id}

- 操作步骤：
  1) 1) 查询日历并检查条目字段
  2) 2) 直接读取该活动详情

- 预期结果：
  1) 无权占用条目 label 为『已占用』，不含标题、所有者、名单（BR-05 脱敏）
  2) 直连详情 403/404 且不泄露对象信息（ID 可猜测不构成授权）


#### UC-03-TC03 日历区间超过 7 天被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流2a

- 前置条件：
  1) 任一已登录用户
  2) 唯一改变变量：from/to 跨度（8 天）

- 输入条件：
  1) API-09 GET /resources/calendar?from=2030-10-20T00:00:00+08:00&to=2030-10-28T00:00:00+08:00
  2) 对照：同接口 7 天跨度

- 操作步骤：
  1) 1) 发起 8 天请求
  2) 2) 以 7 天跨度对照

- 预期结果：
  1) 8 天请求 400 VALIDATION_ERROR（NFR-19：日历最多 7 天）并提示调整范围
  2) 7 天对照请求 200


#### UC-03-TC04 日历展示布撤场、设备同时用量与故障条目

- 极性：正面；优先级：P1；事件流：基本流

- 前置条件：
  1) 4001 占用 3101/3201；resource_admin 已登记 3201 故障（见 UC-18）
  2) student_a 已登录

- 输入条件：
  1) API-09 GET /resources/calendar?from=2030-10-20T00:00:00+08:00&to=2030-10-21T00:00:00+08:00&resourceId=3201

- 操作步骤：
  1) 1) 查询 3201 日历
  2) 2) 核对 segment 与数量字段

- 预期结果：
  1) 条目 segment 区分 SETUP/EVENT/TEARDOWN/FAULT；设备行 quantity 为该时段同时用量
  2) FAULT 条目 quantity=故障数量、activityId=null、label='资源不可用'，不显示登记人与私有原因


#### UC-03-TC05 列表截断标志与提示

- 极性：正面；优先级：P2；事件流：基本流+备选流1b

- 前置条件：
  1) 测试数据构造 >200 条可列表活动
  2) 任一已登录用户

- 输入条件：
  1) API-13 GET /activities?scope=public

- 操作步骤：
  1) 1) 查询
  2) 2) 统计 data 长度与 truncated 标志

- 预期结果：
  1) data 最多 200 项且 truncated=true（NFR-19）；前端提示被截断，不显示为完整总数


### UC-04 维护资源及申请资格——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-04-TC01 | 新增场地并自动纳入创建者管理范围 | 正面 | P0 | 基本流 |
| UC-04-TC02 | 非法资源资料被拒（逐项单变量） | 负面 | P0 | 基本流+备选流3a |
| UC-04-TC03 | 资格调整记录前后值且无基本资料修改入口 | 正面 | P0 | 基本流步骤5+5c |
| UC-04-TC04 | 学生所有者申请仅教师资源被拒且不占资源 | 负面 | P0 | 基本流+备选流6a |
| UC-04-TC05 | 资格关闭后新申请被拒但既有预约保留 | 负面 | P1 | 基本流+备选流5a+5b+6a |
| UC-04-TC06 | 资格八组合矩阵（2 身份×4 组合） | 正面 | P1 | 基本流+备选流6a |

#### UC-04-TC01 新增场地并自动纳入创建者管理范围

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) resource_admin（2004）已登录
  2) 资源名『测试会议室X』未占用

- 输入条件：
  1) API-08 POST /resources，body=ResourceCreate{kind:'VENUE',name:'测试会议室X',category:'VENUE',capacity:80,quantity:1,openFrom:'08:00',openTo:'22:00',allowTeacher:true,allowStudent:true}

- 操作步骤：
  1) 1) 创建资源
  2) 2) 查 user_resource_scope
  3) 3) API-07 GET /resources 核对

- 预期结果：
  1) 201 ResourceView 含 id；校验通过（场地 quantity=1、capacity>0、时段递增）
  2) user_resource_scope 新增 (2004,新资源) 行（同事务自动加入管理范围）；API-07 可见该资源


#### UC-04-TC02 非法资源资料被拒（逐项单变量）

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) resource_admin 已登录；有效资源基准同 TC01
  2) 唯一改变变量：每次仅改一个校验字段

- 输入条件：
  1) API-08 POST /resources 依次提交：①场地 quantity=2；②capacity=0；③EQUIPMENT 携带 capacity=5；④quantity=0；⑤openFrom:'22:00'、openTo:'08:00'

- 操作步骤：
  1) 1) 逐项提交 5 种非法组合（每项只含一个违规字段）
  2) 2) 查 resource 行数

- 预期结果：
  1) 5 项均 400 VALIDATION_ERROR 且 message 指向具体字段（BR-08/10、契约附加校验）
  2) 无新增 resource 行


#### UC-04-TC03 资格调整记录前后值且无基本资料修改入口

- 极性：正面；优先级：P0；事件流：基本流步骤5+5c

- 前置条件：
  1) resource_admin 已登录；3113（仅学生）存在
  2) 唯一改变变量：eligibility 两布尔值

- 输入条件：
  1) API-40 PATCH /resources/{3113}/eligibility，body={allowTeacher:true,allowStudent:false}
  2) API-07 GET /resources（对照）
  3) PATCH /resources/{3113}（验证 5c：无此路由）

- 操作步骤：
  1) 1) 调整资格
  2) 2) 查 resource_eligibility_change
  3) 3) 尝试修改基本资料

- 预期结果：
  1) API-40 返回 200 ResourceView；resource_eligibility_change 新增 before_json/after_json 行（BR-04 可追踪）
  2) 基本资料无修改路由（404/405），首版不提供修改入口


#### UC-04-TC04 学生所有者申请仅教师资源被拒且不占资源

- 极性：负面；优先级：P0；事件流：基本流+备选流6a

- 前置条件：
  1) applicant_b（2002，STUDENT）已登录；3112/3212 为仅教师资源；BASE_ACTIVITY_INPUT 有效
  2) 唯一改变变量：plan 资源指向受限资源

- 输入条件：
  1) API-12 POST /activities，body=BASE_ACTIVITY_INPUT（差异：plan.venueId=3112、equipment=[{resourceId:3212,quantity:2}]）
  2) API-16 POST /activities/{id}/submit，body=VersionCommand{expectedLockVersion:0}
  3) API-24 POST /activities/{id}/schedule-preview（同计划，对照）

- 操作步骤：
  1) 1) 创建草稿（应成功）
  2) 2) 提交申请
  3) 3) schedule-preview 对照
  4) 4) 查 reservation

- 预期结果：
  1) 提交返回 403 RESOURCE_INELIGIBLE，details 含受限资源（3112/3212）与所有者身份 STUDENT（按所有者最新身份校验）
  2) schedule-preview 同样 403 RESOURCE_INELIGIBLE（只读预览遇资格不足也拒绝）；reservation 0 条


#### UC-04-TC05 资格关闭后新申请被拒但既有预约保留

- 极性：负面；优先级：P1；事件流：基本流+备选流5a+5b+6a

- 前置条件：
  1) 4001 已生效预约（3101+3201）；applicant_a 登录；resource_admin 登录
  2) 唯一改变变量：3101 的 allowTeacher/allowStudent

- 输入条件：
  1) API-40 PATCH /resources/{3101}/eligibility，body={allowTeacher:false,allowStudent:false}
  2) API-12 POST /activities（BASE_ACTIVITY_INPUT）+ API-16 submit
  3) API-14 GET /activities/{4001}

- 操作步骤：
  1) 1) 关闭 3101 资格
  2) 2) 新建并提交含 3101 的申请
  3) 3) 查 4001 预约

- 预期结果：
  1) 新申请提交 403 RESOURCE_INELIGIBLE（6a 拒绝新增申请）
  2) 4001 既有 ACTIVE 预约仍在、未被自动删除（5b）


#### UC-04-TC06 资格八组合矩阵（2 身份×4 组合）

- 极性：正面；优先级：P1；事件流：基本流+备选流6a

- 前置条件：
  1) 3111-3114/3211-3214 按四种组合装载；教师所有者（2001）与学生所有者（2002）各一
  2) 唯一改变变量：资源资格组合与所有者身份（8 组）

- 输入条件：
  1) API-07 GET /resources（读取 8 资源 allowTeacher/allowStudent）
  2) API-12 POST /activities + API-16 submit ×8（教师所有者对 3111/3211、3112/3212、3113/3213、3114/3214；学生所有者对同样四组）

- 操作步骤：
  1) 1) 核对资格字段
  2) 2) 按 2 身份×4 组合各提交一次（BASE_ACTIVITY_INPUT 差异仅 venueId/equipment）
  3) 3) 统计结果

- 预期结果：
  1) 资格组合 (true,true)(true,false)(false,true)(false,false) 各两资源与数据字典一致
  2) 允许组合提交 200 进入待审批、禁止组合 403 RESOURCE_INELIGIBLE 且 0 条预约：教师仅组合①②可申请，学生仅组合①③可申请，组合④对两类均拒绝


### UC-05 创建或编辑活动草稿——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-05-TC01 | 创建草稿形成版本 1 且不占资源 | 正面 | P0 | 基本流 |
| UC-05-TC02 | 编辑草稿产生不可变新版本 | 正面 | P1 | 基本流 |
| UC-05-TC03 | 字段边界逐项校验拒绝非法草稿 | 负面 | P0 | 基本流+备选流4a |
| UC-05-TC04 | 非所有者编辑被拒 | 负面 | P0 | 基本流+BR-02 |
| UC-05-TC05 | 非草稿禁止直接修改 | 负面 | P1 | 基本流+备选流5a |
| UC-05-TC06 | 旧 lockVersion 编辑被拒且不覆盖 | 负面 | P1 | 基本流+备选流5b |

#### UC-05-TC01 创建草稿形成版本 1 且不占资源

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) applicant_a 已登录；BASE_ACTIVITY_INPUT 有效；3101/3201 允许两类身份

- 输入条件：
  1) API-12 POST /activities，body=BASE_ACTIVITY_INPUT

- 操作步骤：
  1) 1) 创建
  2) 2) 查 activity/activity_version/reservation
  3) 3) API-24 schedule-preview（可选预览）

- 预期结果：
  1) 201 ActivityView：status=DRAFT、versionNo=1、lockVersion=0、effectiveVersionNo=null
  2) activity_version 新增 1 行且 input_json 完整；reservation 0 条（草稿/提交均不占资源）


#### UC-05-TC02 编辑草稿产生不可变新版本

- 极性：正面；优先级：P1；事件流：基本流

- 前置条件：
  1) TC01 草稿存在（versionNo=1、lockVersion=0）
  2) 唯一改变变量：input.title

- 输入条件：
  1) API-15 PATCH /activities/{id}，body=ActivityEdit{expectedLockVersion:0,input:BASE_ACTIVITY_INPUT（差异：title='校园技术分享会（修订）'）}

- 操作步骤：
  1) 1) 编辑
  2) 2) 查 activity_version 两行内容

- 预期结果：
  1) 200 ActivityView versionNo=2；activity_version 两行且 v1 的 input_json 未被覆盖（内容版本不可变）


#### UC-05-TC03 字段边界逐项校验拒绝非法草稿

- 极性：负面；优先级：P0；事件流：基本流+备选流4a

- 前置条件：
  1) applicant_a 已登录；有效基准 BASE_ACTIVITY_INPUT
  2) 唯一改变变量：每次仅一个被测字段越界

- 输入条件：
  1) API-12 POST /activities 依次提交（差异项）：①title 空串；②title 101 字；③时长 29 分钟；④时长 6 小时 01 分；⑤setupMinutes=121；⑥expectedAttendees=19<registrationCapacity=20

- 操作步骤：
  1) 1) 逐项提交（每项单变量）
  2) 2) 查 activity 行数

- 预期结果：
  1) 各项 400 VALIDATION_ERROR 且 message 贴近字段（BR-06/07、规约 5.2）
  2) 无新活动行


#### UC-05-TC04 非所有者编辑被拒

- 极性：负面；优先级：P0；事件流：基本流+BR-02

- 前置条件：
  1) 活动 X 为 applicant_a 的 DRAFT（使用 4001 的同活动 fixture 变体置 DRAFT，或新建同构草稿）；applicant_b 已登录
  2) 唯一改变变量：调用者（非所有者）；expectedLockVersion 取 GET 读到的当前值，避免状态/版本遮蔽

- 输入条件：
  1) API-14 GET /activities/{X}（读取当前 lockVersion）
  2) API-15 PATCH /activities/{X}，body=ActivityEdit{expectedLockVersion:<GET 所得>,input:BASE_ACTIVITY_INPUT}

- 操作步骤：
  1) 1) GET 读取当前 lockVersion
  2) 2) applicant_b 提交编辑
  3) 3) 查活动版本

- 预期结果：
  1) 403 FORBIDDEN（仅所有者可编辑；目标为合法 DRAFT 且版本最新，不被 INVALID_STATE/STALE_VERSION 遮蔽）
  2) versionNo/lockVersion 不变，无 activity_version 新增


#### UC-05-TC05 非草稿禁止直接修改

- 极性：负面；优先级：P1；事件流：基本流+备选流5a

- 前置条件：
  1) 4001 为 OPEN（非 DRAFT）；applicant_a 已登录
  2) 唯一改变变量：目标活动状态；expectedLockVersion 取 GET 读到的当前值，避免旧锁触发 STALE_VERSION 遮蔽状态断言

- 输入条件：
  1) API-14 GET /activities/{4001}（读取当前 lockVersion）
  2) API-15 PATCH /activities/{4001}，body=ActivityEdit{expectedLockVersion:<GET 所得>,input:BASE_ACTIVITY_INPUT}

- 操作步骤：
  1) 1) 提交编辑
  2) 2) 读取响应

- 预期结果：
  1) 409 INVALID_STATE，提示已预约/已发布应走改期入口
  2) 版本与预约不变


#### UC-05-TC06 旧 lockVersion 编辑被拒且不覆盖

- 极性：负面；优先级：P1；事件流：基本流+备选流5b

- 前置条件：
  1) TC01 草稿经一次合法编辑生成 versionNo=2、lockVersion=1（编辑产生新内容版本并递增 lockVersion；草稿不能报名）
  2) 唯一改变变量：expectedLockVersion 取旧值 0

- 输入条件：
  1) API-15 PATCH /activities/{id}，body=ActivityEdit{expectedLockVersion:0,input:BASE_ACTIVITY_INPUT}

- 操作步骤：
  1) 1) 以旧 expectedLockVersion 提交
  2) 2) 重新 GET 活动

- 预期结果：
  1) 409 STALE_VERSION，details 至少含一项准确的当前 versionNo/lockVersion/status（02 §3 适用时）
  2) 无覆盖：重新 GET 显示 versionNo/lockVersion 与提交前一致，输入保留待重新提交


### UC-06 提交或撤回活动申请——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-06-TC01 | 提交进入待业务审批且不占资源 | 正面 | P0 | 基本流 |
| UC-06-TC02 | 资格受限提交被拒且不占资源 | 负面 | P0 | 基本流+备选流2a |
| UC-06-TC03 | 撤回生成新草稿并使旧审批失效 | 正面 | P1 | 基本流步骤5+备选流5b |
| UC-06-TC04 | 已发布活动禁止撤回式收回 | 负面 | P1 | 基本流+备选流5a |
| UC-06-TC05 | 陈旧版本提交被拒 | 负面 | P1 | 基本流+备选流2b |

#### UC-06-TC01 提交进入待业务审批且不占资源

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 草稿（DRAFT、versionNo=1、lockVersion=0，BASE_ACTIVITY_INPUT）；applicant_a 已登录

- 输入条件：
  1) API-16 POST /activities/{id}/submit，body=VersionCommand{expectedLockVersion:0}

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态/审批/预约/事件

- 预期结果：
  1) 200 ActivityView status=PENDING_APPROVER（绑定 versionNo=1）
  2) reservation 0 条；audit/outbox 产生提交事件（收件人为审批范围人员）


#### UC-06-TC02 资格受限提交被拒且不占资源

- 极性：负面；优先级：P0；事件流：基本流+备选流2a

- 前置条件：
  1) 学生所有者草稿含仅教师资源（同 UC-04-TC04 构造）
  2) 唯一改变变量：资源资格

- 输入条件：
  1) API-16 POST /activities/{id}/submit，body=VersionCommand{expectedLockVersion:0}

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态与预约

- 预期结果：
  1) 403 RESOURCE_INELIGIBLE（details 含受限资源与 STUDENT 身份）
  2) 状态保持 DRAFT、0 条预约


#### UC-06-TC03 撤回生成新草稿并使旧审批失效

- 极性：正面；优先级：P1；事件流：基本流步骤5+备选流5b

- 前置条件：
  1) 活动处于 PENDING_APPROVER（versionNo=1、lockVersion=n）；applicant_a 已登录

- 输入条件：
  1) API-17 POST /activities/{id}/withdraw，body=ReasonCommand{expectedLockVersion:n,reason:'内容需要调整'}

- 操作步骤：
  1) 1) 撤回
  2) 2) 查版本与状态
  3) 3) 重新提交后核对旧审批对新版本的生效性

- 预期结果：
  1) 200 ActivityView：新版本 versionNo=2、status=DRAFT（内容克隆）
  2) 旧 activity_approval 保留历史但对 v2 无效（BR-12），重新提交需重新审批


#### UC-06-TC04 已发布活动禁止撤回式收回

- 极性：负面；优先级：P1；事件流：基本流+备选流5a

- 前置条件：
  1) 4001 为 OPEN；applicant_a 已登录
  2) 唯一改变变量：目标活动状态

- 输入条件：
  1) API-17 POST /activities/{4001}/withdraw，body=ReasonCommand{expectedLockVersion:n,reason:'测试'}

- 操作步骤：
  1) 1) 提交
  2) 2) 读取响应

- 预期结果：
  1) 409 INVALID_STATE，提示应走改期或取消
  2) 状态与预约不变


#### UC-06-TC05 陈旧版本提交被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流2b

- 前置条件：
  1) 草稿 lockVersion 已递增至 1
  2) 唯一改变变量：expectedLockVersion 取旧值 0

- 输入条件：
  1) API-16 POST /activities/{id}/submit，body=VersionCommand{expectedLockVersion:0}

- 操作步骤：
  1) 1) 提交
  2) 2) 重新 GET 活动

- 预期结果：
  1) 409 STALE_VERSION，details 至少含一项准确的当前 versionNo/lockVersion/status（02 §3 适用时）；无状态变化；重新 GET 显示当前版本未被覆盖


### UC-07 审批活动申请——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-07-TC01 | 批准人同意（空意见合法） | 正面 | P0 | 基本流 |
| UC-07-TC02 | 本人回避与跨组织审批被拒 | 负面 | P0 | 基本流+备选流3a |
| UC-07-TC03 | 审批范围不全（缺设备类别）被拒 | 负面 | P0 | 基本流+备选流3a |
| UC-07-TC04 | 空理由驳回被拒 | 负面 | P1 | 基本流+备选流2a |
| UC-07-TC05 | 驳回回草稿并保留意见 | 正面 | P1 | 基本流+备选流2a |
| UC-07-TC06 | 撤回后旧审批请求被拒 | 负面 | P1 | 基本流+备选流3b |

#### UC-07-TC01 批准人同意（空意见合法）

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 活动 PENDING_APPROVER（versionNo=1、lockVersion=n，计划含 VENUE+PROJECTOR）；approver_a（2003，组织1001×VENUE/PROJECTOR）已登录
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-18 POST /activities/{id}/approver-decision，body=ApprovalCommand{expectedLockVersion:n,decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 以空意见同意
  2) 2) 查状态/审批/预约

- 预期结果：
  1) 200 ActivityView status=APPROVED_UNRESERVED；activity_approval 新增绑定 versionNo=1 的 APPROVE 记录（comment 允许空字符串）
  2) reservation 仍 0 条（业务批准不占资源）


#### UC-07-TC02 本人回避与跨组织审批被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 活动属 applicant_a；构造 2001 兼任 APPROVER（API-39 授予）；approver_other（2008，组织1002）已登录
  2) 唯一改变变量：调用者

- 输入条件：
  1) API-18 POST /activities/{id}/approver-decision（2001 令牌，APPROVE）
  2) 同请求（2008 令牌）

- 操作步骤：
  1) 1) 两者分别提交
  2) 2) 查 activity_approval 与状态

- 预期结果：
  1) 两次均 403 FORBIDDEN（所有者回避本人活动；组织不在审批范围）
  2) 无审批记录，状态保持 PENDING_APPROVER


#### UC-07-TC03 审批范围不全（缺设备类别）被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 构造批准人 2003b：审批范围仅组织1001×{VENUE}（API-39 调整或新建）；活动计划含 PROJECTOR 设备
  2) 唯一改变变量：批准人的范围类别

- 输入条件：
  1) API-18 POST /activities/{id}/approver-decision（2003b 令牌，APPROVE）
  2) 对照：approver_a（范围完整）同请求

- 操作步骤：
  1) 1) 2003b 提交
  2) 2) approver_a 对照提交

- 预期结果：
  1) 2003b 请求 403 FORBIDDEN（范围须覆盖组织及全部必要资源类别）
  2) 对照 approver_a 返回 200（唯一变量为范围）


#### UC-07-TC04 空理由驳回被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流2a

- 前置条件：
  1) 同 TC01 前置
  2) 唯一改变变量：decision/comment

- 输入条件：
  1) API-18 POST /activities/{id}/approver-decision，body={expectedLockVersion:n,decision:'REJECT',comment:''}

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态

- 预期结果：
  1) 400 VALIDATION_ERROR（拒绝理由至少 1 字）
  2) 状态保持 PENDING_APPROVER，无审批记录


#### UC-07-TC05 驳回回草稿并保留意见

- 极性：正面；优先级：P1；事件流：基本流+备选流2a

- 前置条件：
  1) 同 TC01 前置

- 输入条件：
  1) API-18 POST /activities/{id}/approver-decision，body={expectedLockVersion:n,decision:'REJECT',comment:'场地安排需调整'}

- 操作步骤：
  1) 1) 驳回
  2) 2) 查状态与意见
  3) 3) 重新提交后核对旧意见对新版本不自动生效

- 预期结果：
  1) 200 status=DRAFT，activity_approval 保留 REJECT 意见与时间
  2) 重新提交（新版本）后旧审批不对新版本生效（BR-12）


#### UC-07-TC06 撤回后旧审批请求被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3b

- 前置条件：
  1) 活动曾 PENDING_APPROVER 后被撤回（当前 DRAFT、lockVersion 为最新值）
  2) 唯一改变变量：活动当前状态

- 输入条件：
  1) API-18 POST /activities/{id}/approver-decision，body={expectedLockVersion:当前值,decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 提交审批
  2) 2) 查状态

- 预期结果：
  1) 409 INVALID_STATE（当前状态不允许审批），响应显示当前状态
  2) 无覆盖；历史审批仅作记录


### UC-08 确认首次资源预约——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-08-TC01 | 首次联合预约成功并建立完整占用 | 正面 | P0 | 基本流 |
| UC-08-TC02 | 设备缺件时联合确认失败且无部分预约 | 负面 | P0 | 基本流+备选流3a |
| UC-08-TC03 | 两活动并发抢同一场地恰一成功 | 负面 | P0 | 基本流+备选流3c |
| UC-08-TC04 | 同键同体重放返回原结果 | 正面 | P1 | 基本流+备选流6a |
| UC-08-TC05 | 同键不同体复用被拒 | 负面 | P1 | 基本流+备选流6a/BR-29 |
| UC-08-TC06 | 资格调整后确认被拒 | 负面 | P1 | 基本流+备选流3b |
| UC-08-TC07 | 写入异常整体回滚且允许重试 | 负面 | P2 | 基本流+备选流4a |
| UC-08-TC08 | 所有者兼任资源确认管理员对本人首次确认被拒 | 负面 | P0 | 基本流+备选流3a |

#### UC-08-TC01 首次联合预约成功并建立完整占用

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 活动 APPROVED_UNRESERVED（versionNo=1、lockVersion=n，BASE_PLAN）；resource_admin（2004）已登录且范围覆盖 3101/3201
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-19 POST /activities/{id}/resource-confirm，body=VersionCommand{expectedLockVersion:n}

- 操作步骤：
  1) 1) 确认
  2) 2) 查 activity/reservation/activity_version
  3) 3) API-09 日历对照

- 预期结果：
  1) 200 status=SCHEDULED、effectiveVersionNo=1
  2) reservation ACTIVE：3101 区间 [09:30,11:30)×1 与 3201 同区间×2（含布撤场），数量正确
  3) 日历显示对应占用；代码审查项：预约写入经 SchedulingPort 统一端口（NFR-26）


#### UC-08-TC02 设备缺件时联合确认失败且无部分预约

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 活动 APPROVED_UNRESERVED 需 3201×2；预置他人 ACTIVE 占用使 3201 可用量=1（如 [10:00,11:00)×1）
  2) 唯一改变变量：资源可用量

- 输入条件：
  1) API-19 POST /activities/{id}/resource-confirm，body=VersionCommand{expectedLockVersion:n}

- 操作步骤：
  1) 1) 确认
  2) 2) 查 reservation 与状态

- 预期结果：
  1) 409 RESOURCE_CONFLICT，details={conflicts:[Conflict…]} 含 EQUIPMENT_SHORTAGE，带精确区间、required=2、available=1（BR-17 单位解释）
  2) 状态保持 APPROVED_UNRESERVED、0 条新增 ACTIVE（联合预约全失败，无部分占用）


#### UC-08-TC03 两活动并发抢同一场地恰一成功

- 极性：负面；优先级：P0；事件流：基本流+备选流3c

- 前置条件：
  1) 两个 APPROVED_UNRESERVED 活动 X/Y：同 3101、同时段、各需 3201×2；两把独立数据库连接与起跑栅栏就绪
  2) 唯一改变变量：并发竞争（两请求参数等价）

- 输入条件：
  1) API-19 ×2（X、Y 各带独立 Idempotency-Key），ConcurrentRunner 栅栏同时发出

- 操作步骤：
  1) 1) 并发执行
  2) 2) 收集两响应
  3) 3) 按活动分组查库

- 预期结果：
  1) 恰 1 个 200（SCHEDULED）与 1 个 409 RESOURCE_CONFLICT
  2) 每资源不超额；失败活动 0 条 ACTIVE 且保持 APPROVED_UNRESERVED（无部分预约）


#### UC-08-TC04 同键同体重放返回原结果

- 极性：正面；优先级：P1；事件流：基本流+备选流6a

- 前置条件：
  1) 可确认活动；key=K、body=B 已首次成功
  2) 唯一改变变量：重复次数

- 输入条件：
  1) API-19 ×2（相同 Idempotency-Key=K 与 body=B）

- 操作步骤：
  1) 1) 连续两次提交
  2) 2) 比较两响应
  3) 3) 查 command_receipt 与 reservation

- 预期结果：
  1) 两次响应 status/body 一致且含同一首次 requestId（重放首次完整响应）
  2) command_receipt 仍 1 行；reservation 行数不变（不重复占用）


#### UC-08-TC05 同键不同体复用被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流6a/BR-29

- 前置条件：
  1) key=K 已用于 body=B
  2) 唯一改变变量：请求体内容

- 输入条件：
  1) API-19（Idempotency-Key=K，body=B'：expectedLockVersion 取不同值）

- 操作步骤：
  1) 1) 提交
  2) 2) 查库

- 预期结果：
  1) 409 IDEMPOTENCY_KEY_REUSED
  2) 不执行新业务写入（无新增预约/版本）


#### UC-08-TC06 资格调整后确认被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3b

- 前置条件：
  1) 活动已批准待确认（计划含 3101）；resource_admin 已登录
  2) 唯一改变变量：3101 资格改为两者均禁止

- 输入条件：
  1) API-40 PATCH /resources/{3101}/eligibility {allowTeacher:false,allowStudent:false}
  2) API-19 POST /activities/{id}/resource-confirm，body=VersionCommand

- 操作步骤：
  1) 1) 调整资格
  2) 2) 确认
  3) 3) 查预约

- 预期结果：
  1) 403 RESOURCE_INELIGIBLE（确认时按所有者最新身份重查资格）
  2) 0 条预约，状态保持 APPROVED_UNRESERVED


#### UC-08-TC07 写入异常整体回滚且允许重试

- 极性：负面；优先级：P2；事件流：基本流+备选流4a

- 前置条件：
  1) 可确认活动；测试上下文准备故障挂钩（替换 bean，非公网接口）
  2) 唯一改变变量：事务内注入 RuntimeException

- 输入条件：
  1) API-19 resource-confirm（预约写入点注入异常）

- 操作步骤：
  1) 1) 注入前读数据库快照
  2) 2) 触发确认并捕获异常
  3) 3) 再读快照比较
  4) 4) 移除故障后同 key 重试

- 预期结果：
  1) 快照一致：activity 状态/版本、reservation、outbox 均无部分写入（整体回滚）
  2) 移除故障后同 key 重试可成功（异常回滚后允许原命令重试）


#### UC-08-TC08 所有者兼任资源确认管理员对本人首次确认被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 活动 APPROVED_UNRESERVED 属 applicant_a（2001）
  2) 2001 经 API-39 授予额外 ADMIN+RESOURCE_CONFIRM（保留原 APPLICANT 角色，managedResourceIds 完整覆盖 3101/3201）
  3) API-39 完成后拍业务表快照（授权变更自身会写审计），拒绝请求后再比对
  4) 唯一改变变量：确认人与活动所有者为同一人

- 输入条件：
  1) API-39 PATCH /users/{2001}/authorization（授予额外角色与范围，保留 APPLICANT）
  2) API-19 POST /activities/{id}/resource-confirm，body=VersionCommand{expectedLockVersion:n}（2001 令牌）

- 操作步骤：
  1) 1) API-39 授予额外角色（保留 APPLICANT）
  2) 2) 授权后拍业务表快照
  3) 3) 以 2001 令牌对本人活动执行首次资源确认
  4) 4) 拒绝请求后比对快照：活动/预约/审计/outbox
  5) 5) 对照：resource_admin（独立授权）执行同一确认

- 预期结果：
  1) 本人确认被拒：403 FORBIDDEN（本人资源复核回避，；管理范围完整覆盖也不例外）
  2) 拒绝请求后快照比对：0 条新增预约；status 保持 APPROVED_UNRESERVED；无业务 audit/outbox 新增（API-39 授权变更自身的审计除外）
  3) 对照 resource_admin 同一确认成功（该成功结果不计入拒绝后无新增断言；唯一变量为是否本人）


### UC-09 发布活动——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-09-TC01 | 发布并对外可见 | 正面 | P0 | 基本流 |
| UC-09-TC02 | 未资源确认不得发布 | 负面 | P0 | 基本流+备选流3a |
| UC-09-TC03 | 资源风险 AT_RISK 时拒绝发布 | 负面 | P0 | 基本流+备选流3a |
| UC-09-TC04 | 非所有者发布被拒 | 负面 | P1 | 基本流+备选流3a |

#### UC-09-TC01 发布并对外可见

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 活动 SCHEDULED、未开始、resourceRisk=NORMAL（BASE_PLAN）；owner 已登录
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-20 POST /activities/{id}/publish，body=VersionCommand{expectedLockVersion:n}
  2) API-13 GET /activities?scope=public（对照）

- 操作步骤：
  1) 1) 发布
  2) 2) 查状态
  3) 3) 查公开列表与详情字段

- 预期结果：
  1) 200 status=OPEN；public 列表可见安排与 registrationCapacity
  2) 公开详情不含审批意见；学生视角 input.staffUserIds=[]（BR-05 裁剪）


#### UC-09-TC02 未资源确认不得发布

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 活动 APPROVED_UNRESERVED（0 条预约）
  2) 唯一改变变量：活动状态

- 输入条件：
  1) API-20 POST /activities/{id}/publish，body=VersionCommand

- 操作步骤：
  1) 1) 发布
  2) 2) 查状态与预约

- 预期结果：
  1) 409 INVALID_STATE（尚未资源确认，业务批准不等于预约）
  2) 状态不变、0 条预约


#### UC-09-TC03 资源风险 AT_RISK 时拒绝发布

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 活动 SCHEDULED 但 3201 故障与其占用重叠 → resourceRisk=AT_RISK
  2) 唯一改变变量：资源风险

- 输入条件：
  1) API-20 POST /activities/{id}/publish，body=VersionCommand

- 操作步骤：
  1) 1) 发布
  2) 2) 读取响应原因与活动风险

- 预期结果：
  1) 拒绝发布且无副作用：状态保持 SCHEDULED、活动持续显示 AT_RISK
  2) 精确业务码待接口契约明确；本用例断言拒绝、状态不变与无副作用，不硬断未定码


#### UC-09-TC04 非所有者发布被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3a

- 前置条件：
  1) 活动属 applicant_a；applicant_b 已登录
  2) 唯一改变变量：调用者

- 输入条件：
  1) API-20 POST /activities/{id}/publish，body=VersionCommand

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态

- 预期结果：
  1) 403 FORBIDDEN（仅所有者可发布）
  2) 状态不变


### UC-10 报名或退出活动——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-10-TC01 | 学生报名成功并更新并发版本 | 正面 | P0 | 基本流 |
| UC-10-TC02 | 20 个学生并发抢 1 个名额恰 1 成功 | 负面 | P0 | 基本流+备选流3b |
| UC-10-TC03 | 重复报名与重复退出返回原记录 | 正面 | P1 | 基本流+备选流4a+6a |
| UC-10-TC04 | 无报名历史退出被拒 | 负面 | P1 | 基本流+备选流6a |
| UC-10-TC05 | 待处理改期冻结报名与退出 | 负面 | P1 | 基本流+备选流3c |
| UC-10-TC06 | 陈旧 expectedVersionNo 报名被拒 | 负面 | P1 | 基本流+备选流3d |
| UC-10-TC07 | 身份或状态不符的报名被拒 | 负面 | P2 | 基本流+BR-15 |

#### UC-10-TC01 学生报名成功并更新并发版本

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 4001 OPEN、未开始、registrationCapacity=20、有效版本=1；student_b（2009，STUDENT）未报名
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-22 PUT /activities/{4001}/registration，body=RegistrationCommand{action:'JOIN',expectedVersionNo:1}

- 操作步骤：
  1) 1) 报名
  2) 2) 查 activity_registration/registrationCount/lockVersion

- 预期结果：
  1) 200 RegistrationView{status:'ACTIVE',versionNo:1}；(4001,2009) 唯一 ACTIVE 行
  2) registrationCount 按 ACTIVE 计数 +1；lockVersion 递增（BR-16）


#### UC-10-TC02 20 个学生并发抢 1 个名额恰 1 成功

- 极性：负面；优先级：P0；事件流：基本流+备选流3b

- 前置条件：
  1) 容量 1 的 OPEN 活动 4101（registration-concurrency 数据）；20 个 STUDENT 账号；独立连接+起跑栅栏
  2) 唯一改变变量：并发压力

- 输入条件：
  1) API-22 PUT /activities/{4101}/registration {action:'JOIN',expectedVersionNo:1} ×20（各账号）

- 操作步骤：
  1) 1) 栅栏并发发出
  2) 2) 收集 20 响应
  3) 3) 查库计数

- 预期结果：
  1) 恰 1 个 200 与 19 个 409 CAPACITY_FULL
  2) ACTIVE 恰 1 条，无超额（NFR-13）


#### UC-10-TC03 重复报名与重复退出返回原记录

- 极性：正面；优先级：P1；事件流：基本流+备选流4a+6a

- 前置条件：
  1) student_b 已 ACTIVE（TC01 后）
  2) 唯一改变变量：重复次数

- 输入条件：
  1) API-22 JOIN 重复一次；API-22 WITHDRAW 一次后再 WITHDRAW 一次

- 操作步骤：
  1) 1) 重复 JOIN
  2) 2) WITHDRAW 并重复
  3) 3) 计数与状态核对

- 预期结果：
  1) 重复 JOIN 返回原记录、ACTIVE 计数不变；重复 WITHDRAW 返回原记录，不重复释放名额
  2) 状态迁移 ACTIVE→WITHDRAWN 单向可查


#### UC-10-TC04 无报名历史退出被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流6a

- 前置条件：
  1) 4002 基准为 SCHEDULED（已资源确认，无需再确认）；owner（2002）先以 API-20 publish 将其置 OPEN
  2) student_a（2005）对该活动无报名历史、活动无待处理改期
  3) 唯一改变变量：报名历史（2005 无记录）

- 输入条件：
  1) API-20 POST /activities/{4002}/publish，body=VersionCommand{expectedLockVersion:n}（owner 操作）
  2) API-22 PUT /activities/{4002}/registration，body={action:'WITHDRAW',expectedVersionNo:n}（2005 令牌）

- 操作步骤：
  1) 1) owner 发布 4002 为 OPEN
  2) 2) 确认 2005 无报名记录且无待处理改期
  3) 3) 2005 提交 WITHDRAW
  4) 4) 查报名行

- 预期结果：
  1) publish 返回 200，4002 由 SCHEDULED 变 OPEN
  2) WITHDRAW 被拒绝（409 INVALID_STATE：无报名历史不可退出）；唯一被测变量为报名历史
  3) activity_registration 无 2005 行产生


#### UC-10-TC05 待处理改期冻结报名与退出

- 极性：负面；优先级：P1；事件流：基本流+备选流3c

- 前置条件：
  1) 活动存在待处理改期（AWAITING_CONFIRMATIONS/PENDING_APPROVER/PENDING_RESOURCE 之一）
  2) 唯一改变变量：是否存在待处理改期

- 输入条件：
  1) API-22 JOIN 与 WITHDRAW 各一次（expectedVersionNo 取当前有效版本）

- 操作步骤：
  1) 1) 冻结期请求
  2) 2) 改期到终态后重试

- 预期结果：
  1) 冻结期 409 CHANGE_PENDING 并说明冻结原因（BR-26）；冻结期报名数据无变化
  2) 终态（REJECTED/APPLIED）后按名额规则正常执行


#### UC-10-TC06 陈旧 expectedVersionNo 报名被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3d

- 前置条件：
  1) 改期生效后活动有效版本=2
  2) 唯一改变变量：expectedVersionNo 取旧值 1

- 输入条件：
  1) API-22 PUT /activities/{id}/registration，body={action:'JOIN',expectedVersionNo:1}

- 操作步骤：
  1) 1) 提交
  2) 2) 重新 GET 活动

- 预期结果：
  1) 409 STALE_VERSION，details 至少含一项准确的当前 versionNo/lockVersion/status（02 §3 适用时）；要求查看新安排后操作；报名无变化


#### UC-10-TC07 身份或状态不符的报名被拒

- 极性：负面；优先级：P2；事件流：基本流+BR-15

- 前置条件：
  1) applicant_a（TEACHER）对 OPEN 活动 4001；student_b 对 SCHEDULED 活动 4002
  2) 唯一改变变量：报名者身份或活动状态

- 输入条件：
  1) API-22 JOIN（2001 令牌）；API-22 JOIN（2009 对 4002）

- 操作步骤：
  1) 1) 教师身份报名
  2) 2) 学生对未发布活动报名

- 预期结果：
  1) 教师身份报名 403 FORBIDDEN（仅 STUDENT 可报名）
  2) 非 OPEN 活动报名 409 INVALID_STATE


### UC-11 查看冲突并比较备选——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-11-TC01 | 端点相接与设备峰值统计正确 | 正面 | P0 | 基本流 |
| UC-11-TC02 | 冲突解释精确到区间与数量 | 负面 | P0 | 基本流+BR-17 |
| UC-11-TC03 | 首选替代方案与排序元数据 | 正面 | P0 | 基本流步骤4-7+备选流7a |
| UC-11-TC04 | 自身旧占用被排除不自竞争 | 负面 | P1 | 基本流+备选流2a |
| UC-11-TC05 | 候选过滤资格受限资源且强制提交仍被拒 | 负面 | P1 | 基本流+备选流4c |

#### UC-11-TC01 端点相接与设备峰值统计正确

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 3101 已有占用 [10:00,11:00)；3201（库存 2）已有 [10:00,11:00)×1 与 [11:00,12:00)×1（错峰）
  2) 唯一改变变量：候选计划时间/数量

- 输入条件：
  1) API-24 POST /activities/{id}/schedule-preview，body=Proposal{baseVersionNo:1,targetPlan:BASE_PLAN（差异：11:00—12:00、setup/teardown=0）}
  2) 同接口（差异：10:00—12:00、equipment=[{resourceId:3201,quantity:1}]）

- 操作步骤：
  1) 1) 预览相邻时段
  2) 2) 预览跨两段的设备需求
  3) 3) 前后比对业务表

- 预期结果：
  1) 两个 CheckResult 均 feasible=true、conflicts=[]：端点相接不冲突（BR-09）；任一时刻设备并发恰 2≤库存 2，不得把错峰占用求和误算为 3
  2) 预览前后 reservation/change_request 无新增（BR-20 不写预约）


#### UC-11-TC02 冲突解释精确到区间与数量

- 极性：负面；优先级：P0；事件流：基本流+BR-17

- 前置条件：
  1) 4002 占用 3101 与 3201×1（D 日 14:30—16:30）；3201 库存 2
  2) 唯一改变变量：目标计划（改期目标）

- 输入条件：
  1) API-24 POST /activities/{id}/schedule-preview，targetPlan=BASE_CHANGE 的 targetPlan（15:00—16:00、布撤场各 30、3101+3201×2）

- 操作步骤：
  1) 1) 预览
  2) 2) 逐条比对 conflicts 与数据库实际占用

- 预期结果：
  1) feasible=false，同时含 VENUE_OCCUPIED（3101 与 4002 重叠区间）与 EQUIPMENT_SHORTAGE（3201 required=2、available=1，缺 1）
  2) 每条 conflict 含 resourceId/resourceName/startsAt/endsAt/required/available/中文 message；预览不写库


#### UC-11-TC03 首选替代方案与排序元数据

- 极性：正面；优先级：P0；事件流：基本流步骤4-7+备选流7a

- 前置条件：
  1) 同 TC02 数据；3102/3202 空闲且资格允许
  2) 唯一改变变量：无（候选生成）

- 输入条件：
  1) API-25 POST /activities/{id}/alternatives，body=Proposal（同 TC02 目标）

- 操作步骤：
  1) 1) 请求候选
  2) 2) 核对 items[0] 与元数据

- 预期结果：
  1) items[0]：timeShiftMinutes=0、venueChanged=true（venueId=3102）、replacedEquipmentLines=1（equipment=3202×2），代价元组 (0,1,1)
  2) items≤5 项；含 evaluatedCount≤300、truncated、offsetsMinutes=[0,-30,30,-60,60,-90,90,-120,120]、maxEvaluations=300；不出现『全局最优』表述（BR-19/NFR-18）


#### UC-11-TC04 自身旧占用被排除不自竞争

- 极性：负面；优先级：P1；事件流：基本流+备选流2a

- 前置条件：
  1) 活动 A 自身占用 [09:30,11:30)；目标计划（10:30—11:30、布撤场各 30 → 占用 [10:00,12:00)）与其部分重叠；无其他活动冲突
  2) 唯一改变变量：目标计划是否与自身旧占用重叠；自身排除由路径 /activities/{A} 推导，请求体不含 excludeActivityId

- 输入条件：
  1) API-24 POST /activities/{A}/schedule-preview，body=Proposal{baseVersionNo:1,targetPlan:目标计划}（请求体仅 baseVersionNo 与 targetPlan 两字段）

- 操作步骤：
  1) 1) 预览
  2) 2) 检查 conflicts 构成

- 预期结果：
  1) conflicts 不含 VENUE_OCCUPIED（自身旧占用按路径活动 ID 排除）；无其他冲突时 feasible=true
  2) 若未排除自身会误报 VENUE_OCCUPIED——此为该类缺陷的判别式


#### UC-11-TC05 候选过滤资格受限资源且强制提交仍被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流4c

- 前置条件：
  1) 学生所有者（2002）；3112/3212 仅教师且时段空闲
  2) 唯一改变变量：资源资格

- 输入条件：
  1) API-25 POST /activities/{id}/alternatives（学生所有者目标计划）
  2) API-27 POST /activities/{id}/changes（强制创建含 3112/3212 的目标，对照）

- 操作步骤：
  1) 1) 生成候选
  2) 2) 强制创建受限目标

- 预期结果：
  1) alternatives.items 不含 3112/3212（过滤不合资格组合，）
  2) 强制创建 403 RESOURCE_INELIGIBLE 且无 change_request 写入


### UC-12 预览并创建改期申请——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-12-TC01 | 改期影响预览并按最后选择重算 | 正面 | P0 | 基本流步骤1-4+备选流3a |
| UC-12-TC02 | 创建改期进入工作人员确认并冻结报名 | 正面 | P0 | 基本流步骤5-9 |
| UC-12-TC03 | 仅所有者为工作人员时直达复审 | 正面 | P1 | 基本流步骤8 |
| UC-12-TC04 | 每活动仅一个待处理改期 | 负面 | P0 | 基本流+备选流6b |
| UC-12-TC05 | 目标资格受限被拒且无冻结 | 负面 | P1 | 基本流+备选流6b |
| UC-12-TC06 | 陈旧版本创建改期被拒 | 负面 | P1 | 基本流+备选流6a |
| UC-12-TC07 | 带冲突目标仍可创建但不占位 | 正面 | P2 | 基本流+备选流6c/BR-20 |

#### UC-12-TC01 改期影响预览并按最后选择重算

- 极性：正面；优先级：P0；事件流：基本流步骤1-4+备选流3a

- 前置条件：
  1) 4001 OPEN、versionNo=1、lockVersion=n；applicant_a 已登录
  2) 目标一（固定集合）=3102+3202×2、D 日 15:00—16:00、布撤场各 30
  3) 目标二（改选用）=3102+3202×2、D 日 15:30—16:30、布撤场各 30（仅起止时间不同）

- 输入条件：
  1) API-26 POST /activities/{4001}/change-preview，body=Proposal{baseVersionNo:1,targetPlan:目标一}
  2) API-26 同接口，targetPlan=目标二

- 操作步骤：
  1) 1) 以目标一预览影响
  2) 2) 改选目标二后重新预览
  3) 3) 比对两次 impact 与数据库

- 预期结果：
  1) 目标一预览：ChangePreview 含 check/alternatives/impact——released=[3101,3201]、acquired=[3102,3202]、retained=[]、approvalReset=true、requiredStaffUserIds=[2006]（不含所有者）、registeredUserIds 真实、notificationUserIds=组织者∪工作人员∪有效报名（去重并集，BR-22）
  2) 目标二预览按目标二重新计算、不混用目标一快照：oldPlan 不变，newPlan.startAt/endAt=15:30/16:30，changedFields 按原有效安排与目标二比较，包含时间及场地/设备差异（不能仅比较目标一与目标二）；资源集合仍为 released=[3101,3201]、acquired=[3102,3202]、retained=[]
  3) 两次预览前后 change_request 均无新增（BR-20 不写预约）


#### UC-12-TC02 创建改期进入工作人员确认并冻结报名

- 极性：正面；优先级：P0；事件流：基本流步骤5-9

- 前置条件：
  1) 同 TC01 前置；目标为最后确认目标；reason='原时段设备紧张'
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-27 POST /activities/{4001}/changes，body=ChangeCreate{baseVersionNo:1,expectedLockVersion:n,targetPlan:目标,reason:'原时段设备紧张'}
  2) API-22 PUT registration JOIN（冻结验证）

- 操作步骤：
  1) 1) 创建改期
  2) 2) 查 change_request/impact 快照
  3) 3) 尝试报名
  4) 4) 查活动有效安排

- 预期结果：
  1) 201 ChangeView status=AWAITING_CONFIRMATIONS（存在其他工作人员 2006）；target_plan_json/impact_json 创建后不可变
  2) 报名 409 CHANGE_PENDING（冻结）
  3) 原有效安排不变（versionNo=1、原预约 ACTIVE）


#### UC-12-TC03 仅所有者为工作人员时直达复审

- 极性：正面；优先级：P1；事件流：基本流步骤8

- 前置条件：
  1) 活动 staff 仅含所有者本人（2001），或 staff=[]；其余同 TC01
  2) 唯一改变变量：工作人员构成

- 输入条件：
  1) API-27 POST /activities/{4001}/changes（同 TC02）
  2) API-34 GET /notifications（通知核对）

- 操作步骤：
  1) 1) 创建改期
  2) 2) 查 confirmations 与状态
  3) 3) 查通知

- 预期结果：
  1) status 直接 PENDING_APPROVER；confirmations 不含所有者本人（requiredStaff 排除所有者，不向自己确认）
  2) 通知仍按快照发给相关人员（含所有者）


#### UC-12-TC04 每活动仅一个待处理改期

- 极性：负面；优先级：P0；事件流：基本流+备选流6b

- 前置条件：
  1) TC02 改期处于待处理状态
  2) 唯一改变变量：重复创建

- 输入条件：
  1) API-27 POST /activities/{4001}/changes（新 Idempotency-Key，同目标）

- 操作步骤：
  1) 1) 再次创建
  2) 2) 查 change_request 待处理数量

- 预期结果：
  1) 拒绝重复创建且无副作用：仍仅一个待处理改期，无第二条 change_request
  2) 精确业务码待接口契约明确；本用例断言拒绝、无副作用且不生成待处理改期，不硬断未定码


#### UC-12-TC05 目标资格受限被拒且无冻结

- 极性：负面；优先级：P1；事件流：基本流+备选流6b

- 前置条件：
  1) 学生所有者（2002）；目标含仅教师资源 3112/3212
  2) 唯一改变变量：目标资源资格

- 输入条件：
  1) API-27 POST /activities/{4001}/changes（目标含 3112/3212）

- 操作步骤：
  1) 1) 创建
  2) 2) 查 change_request 与报名可用性

- 预期结果：
  1) 403 RESOURCE_INELIGIBLE（创建时按所有者最新身份校验）
  2) 无 change 写入、报名无冻结


#### UC-12-TC06 陈旧版本创建改期被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流6a

- 前置条件：
  1) lockVersion 已因报名递增
  2) 唯一改变变量：expectedLockVersion 取旧值

- 输入条件：
  1) API-27 POST /activities/{4001}/changes（expectedLockVersion=0，baseVersionNo=当前）

- 操作步骤：
  1) 1) 提交
  2) 2) 重新 GET 活动

- 预期结果：
  1) 409 STALE_VERSION，details 至少含一项准确的当前 versionNo、lockVersion 或 status（02 §3，不限定唯一字段）；无 change 写入；重新 GET 显示当前版本未被覆盖


#### UC-12-TC07 带冲突目标仍可创建但不占位

- 极性：正面；优先级：P2；事件流：基本流+备选流6c/BR-20

- 前置条件：
  1) 目标资源被 4002 占用（预览 feasible=false）；无待处理改期
  2) 唯一改变变量：目标资源可用性（与 TC01 相比）

- 输入条件：
  1) API-26 change-preview（对照）
  2) API-27 POST /activities/{4001}/changes（同冲突目标）

- 操作步骤：
  1) 1) 预览确认有冲突
  2) 2) 创建改期
  3) 3) 查预约与响应说明

- 预期结果：
  1) 创建成功（201）且响应/页面明确提示目标未占位、最终确认可能失败
  2) 无任何 reservation 新增（BR-20：预览/创建均不占位）


### UC-13 确认工作人员改期待办——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-13-TC01 | 工作人员同意并推进状态 | 正面 | P0 | 基本流 |
| UC-13-TC02 | 空理由拒绝被拒 | 负面 | P1 | 基本流+备选流2a |
| UC-13-TC03 | 工作人员有效拒绝终止改期 | 负面 | P0 | 基本流+备选流2a |
| UC-13-TC04 | 已决定后反向决定被拒 | 负面 | P1 | 基本流+备选流3b+3d |
| UC-13-TC05 | 重复相同决定幂等 | 正面 | P1 | 基本流+备选流3a |
| UC-13-TC06 | 非名单人员确认被拒 | 负面 | P1 | 基本流+备选流3c |
| UC-13-TC07 | 停用工作人员确认被拒且不代签 | 负面 | P1 | 基本流+备选流3c |

#### UC-13-TC01 工作人员同意并推进状态

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 改期 AWAITING_CONFIRMATIONS，requiredStaff=[2006]；staff_a 已登录
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-30 POST /changes/{id}/confirmation，body=ChangeDecision{decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 同意
  2) 2) 查 change_confirmation 与改期状态

- 预期结果：
  1) 200 ChangeView；change_confirmation 记录本人 decision/decidedAt（空意见合法）
  2) 全部工作人员同意后 status=PENDING_APPROVER；通知已读不替代确认


#### UC-13-TC02 空理由拒绝被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流2a

- 前置条件：
  1) 同 TC01 前置
  2) 唯一改变变量：decision/comment

- 输入条件：
  1) API-30 POST /changes/{id}/confirmation，body={decision:'REJECT',comment:''}

- 操作步骤：
  1) 1) 提交
  2) 2) 查 confirmations

- 预期结果：
  1) 400 VALIDATION_ERROR（拒绝理由至少 1 字）
  2) 无决定记录，状态保持 AWAITING_CONFIRMATIONS


#### UC-13-TC03 工作人员有效拒绝终止改期

- 极性：负面；优先级：P0；事件流：基本流+备选流2a

- 前置条件：
  1) 同 TC01 前置
  2) 唯一改变变量：decision 与理由

- 输入条件：
  1) API-30 POST /changes/{id}/confirmation，body={decision:'REJECT',comment:'时间冲突'}

- 操作步骤：
  1) 1) 拒绝
  2) 2) 查改期/活动/预约/报名

- 预期结果：
  1) status=REJECTED；报名冻结解除
  2) 原有效安排与预约完全保留（effectiveVersionNo 不变、无新 activity_version）


#### UC-13-TC04 已决定后反向决定被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3b+3d

- 前置条件：
  1) staff_a 已 APPROVE（TC01 后）
  2) 唯一改变变量：再次决定方向

- 输入条件：
  1) API-30 POST /changes/{id}/confirmation，body={decision:'REJECT',comment:'改主意'}

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态与决定

- 预期结果：
  1) 409 ALREADY_DECIDED；已有决定与状态不变（终态同样不接受新决定）


#### UC-13-TC05 重复相同决定幂等

- 极性：正面；优先级：P1；事件流：基本流+备选流3a

- 前置条件：
  1) staff_a 已 APPROVE
  2) 唯一改变变量：重复次数

- 输入条件：
  1) API-30 POST /changes/{id}/confirmation（同 body 重复一次）

- 操作步骤：
  1) 1) 重复提交
  2) 2) 比对事件与 decidedAt

- 预期结果：
  1) 返回当前结果；无重复事件/通知；decidedAt 不回退


#### UC-13-TC06 非名单人员确认被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3c

- 前置条件：
  1) student_b（2009）不在 requiredStaff
  2) 唯一改变变量：调用者

- 输入条件：
  1) API-30 POST /changes/{id}/confirmation（2009 令牌，APPROVE）

- 操作步骤：
  1) 1) 提交
  2) 2) 查 confirmations

- 预期结果：
  1) 403 FORBIDDEN（不能替他人确认）
  2) confirmations 无 2009 行；状态不变


#### UC-13-TC07 停用工作人员确认被拒且不代签

- 极性：负面；优先级：P1；事件流：基本流+备选流3c

- 前置条件：
  1) 改期 AWAITING_CONFIRMATIONS，requiredStaff=[2006]；change_confirmation 已预建 2006 行（decision=NULL、decided_at=NULL）
  2) account_admin 已停用 2006
  3) 唯一改变变量：工作人员账号启用状态

- 输入条件：
  1) API-06 PATCH /users/{2006}/enabled，body={enabled:false}（2007 令牌）
  2) API-30 POST /changes/{id}/confirmation，body={decision:'APPROVE',comment:''}（2006 停用前令牌）

- 操作步骤：
  1) 1) 停用 2006（API-06）
  2) 2) 以 2006 旧令牌调用 API-30
  3) 3) 查 change_confirmation 与改期状态/待办

- 预期结果：
  1) 停用即时失效旧会话：HTTP 以旧令牌调用 API-30 返回 401 UNAUTHENTICATED（NFR-07；本例为 HTTP 旧令牌场景，不以 403 替代）
  2) （补充注记：经非 HTTP 直接端口调用可为 403 越权拒绝，属端口层语义，不与本 HTTP 断言混用）
  3) 预建确认行保留 decision=NULL、decided_at=NULL（无代签、不伪造决定）
  4) 改期状态不推进（仍 AWAITING_CONFIRMATIONS），待办保留，可撤回重建


### UC-14 复审改期申请——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-14-TC01 | 复审同意进入资源待确认 | 正面 | P0 | 基本流 |
| UC-14-TC02 | 复审驳回保护原安排 | 负面 | P0 | 基本流+备选流2a |
| UC-14-TC03 | 越权或范围不全复审被拒 | 负面 | P0 | 基本流+备选流3a |
| UC-14-TC04 | 终态后复审被拒且意见不复用 | 负面 | P1 | 基本流+备选流3a+4a |
| UC-14-TC05 | 所有者兼任批准人对本人改期复审被拒 | 负面 | P0 | 基本流+备选流3a |

#### UC-14-TC01 复审同意进入资源待确认

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 改期 PENDING_APPROVER（工作人员已全同意）；approver_a 范围完整且非所有者
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-31 POST /changes/{id}/approver-decision，body=ChangeDecision{decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 同意
  2) 2) 查状态与 approverDecision

- 预期结果：
  1) 200 status=PENDING_RESOURCE；approverDecision 绑定该 changeId 与不可变 targetPlan（含 actorId/decidedAt）


#### UC-14-TC02 复审驳回保护原安排

- 极性：负面；优先级：P0；事件流：基本流+备选流2a

- 前置条件：
  1) 同 TC01 前置
  2) 唯一改变变量：decision

- 输入条件：
  1) API-31 POST /changes/{id}/approver-decision，body={decision:'REJECT',comment:'目标不可行'}

- 操作步骤：
  1) 1) 驳回
  2) 2) 查改期/预约/报名

- 预期结果：
  1) status=REJECTED；原预约与版本保留；报名冻结解除


#### UC-14-TC03 越权或范围不全复审被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) approver_other（2008，组织1002）或范围仅 VENUE 的批准人
  2) 唯一改变变量：调用者及其范围

- 输入条件：
  1) API-31 POST /changes/{id}/approver-decision（2008 令牌，APPROVE）

- 操作步骤：
  1) 1) 提交
  2) 2) 查 approverDecision

- 预期结果：
  1) 403 FORBIDDEN（组织/类别范围不满足）
  2) 无决定记录，状态不变


#### UC-14-TC04 终态后复审被拒且意见不复用

- 极性：负面；优先级：P1；事件流：基本流+备选流3a+4a

- 前置条件：
  1) 改期已 WITHDRAWN/REJECTED（终态）
  2) 唯一改变变量：改期状态

- 输入条件：
  1) API-31 POST /changes/{id}/approver-decision，body={decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 提交
  2) 2) 新建改期后核对旧意见复用情况

- 预期结果：
  1) 409 INVALID_STATE（终态不接受新决定）
  2) 若另建新改期，旧意见不可移用（新目标需重新复审）


#### UC-14-TC05 所有者兼任批准人对本人改期复审被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 改期 PENDING_APPROVER，所属活动 owner=2001
  2) 2001 经 API-39 授予额外 APPROVER（保留原 APPLICANT 角色），审批范围完整覆盖组织1001×{VENUE,PROJECTOR}
  3) API-39 完成后拍业务表快照（授权变更自身会写审计），拒绝请求后再比对
  4) 唯一改变变量：复审人与活动所有者为同一人

- 输入条件：
  1) API-39 PATCH /users/{2001}/authorization（授予额外 APPROVER，保留 APPLICANT）
  2) API-31 POST /changes/{id}/approver-decision，body=ChangeDecision{decision:'APPROVE',comment:''}（2001 令牌）

- 操作步骤：
  1) 1) API-39 授予额外 APPROVER（保留 APPLICANT）
  2) 2) 授权后拍业务表快照
  3) 3) 以 2001 令牌复审本人改期
  4) 4) 拒绝请求后比对快照与 approverDecision

- 预期结果：
  1) 403 FORBIDDEN（本人回避，）
  2) 无 approverDecision 决定记录；状态保持 PENDING_APPROVER；拒绝请求后无业务审计/outbox 新增（API-39 授权变更自身的审计除外）


### UC-15 最终确认并切换改期——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-15-TC01 | 最终确认原子切换到新安排 | 正面 | P0 | 基本流 |
| UC-15-TC02 | 预览后被抢占置 CONFLICTED 且原安排保留 | 负面 | P0 | 基本流+备选流3a |
| UC-15-TC03 | 切换中异常整体回滚且可重试 | 负面 | P0 | 基本流+备选流5a |
| UC-15-TC04 | 管理员拒绝保护原安排 | 负面 | P1 | 基本流+备选流2a |
| UC-15-TC05 | 最新资格不符或本人复核回避 | 负面 | P1 | 基本流+备选流3b |
| UC-15-TC06 | 活动取消后旧确认请求被拒 | 负面 | P1 | 基本流+备选流3c |
| UC-15-TC07 | 目标与自身旧占用重叠仍可生效 | 正面 | P1 | 基本流+BR-25 |
| UC-15-TC08 | 资源确认范围仅覆盖部分必要资源被拒（API-19/API-32 双子例） | 负面 | P1 | 基本流+备选流3b |

#### UC-15-TC01 最终确认原子切换到新安排

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 改期 PENDING_RESOURCE（目标 3102+3202×2、15:00—16:00、布撤场各 30）；resource_admin 已登录；4001 有 ACTIVE 报名（2005）
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-32 POST /changes/{id}/resource-decision，body=ChangeDecision{decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 确认
  2) 2) 查 activity/activity_version/reservation/activity_registration/change/outbox
  3) 3) API-09 日历与 API-29 改期详情对照

- 预期结果：
  1) 200 ChangeView status=APPLIED、appliedVersionNo=2；activity versionNo=2、effectiveVersionNo=2（活动保持 OPEN）
  2) 旧预约 RELEASED、新预约 ACTIVE（3102+3202×2 区间含布撤场）；报名仍 ACTIVE 且 versionNo=2
  3) audit/outbox/notification 关联 version2；日历显示新占用、旧占用消失（统一 SchedulingPort 切换，NFR-26）


#### UC-15-TC02 预览后被抢占置 CONFLICTED 且原安排保留

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) 改期 PENDING_RESOURCE；另一活动 Z 先占用目标资源（3102+3202 全量）
  2) 唯一改变变量：目标资源可用性

- 输入条件：
  1) API-32 POST /changes/{id}/resource-decision，body={decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 抢占后确认
  2) 2) 查库

- 预期结果：
  1) 409 RESOURCE_CONFLICT，details={conflicts:Conflict[],change:ChangeView}；改期 CONFLICTED 并保存 last_conflicts_json（业务提交正常落库，不等于 SQL 回滚）
  2) 原有效版本/预约保留、无新预约；需重新预览创建新改期，旧意见不移用


#### UC-15-TC03 切换中异常整体回滚且可重试

- 极性：负面；优先级：P0；事件流：基本流+备选流5a

- 前置条件：
  1) 改期 PENDING_RESOURCE；测试挂钩置于 activatePlan 之后、replaceReservations 中
  2) 唯一改变变量：注入异常

- 输入条件：
  1) API-32 POST /changes/{id}/resource-decision（APPROVE）

- 操作步骤：
  1) 1) 注入前读快照
  2) 2) 触发确认
  3) 3) 比对快照
  4) 4) 移除故障后同 key 重试

- 预期结果：
  1) 版本指针/预约/报名/改期/审计/outbox 全部回滚（快照一致，无部分提交）
  2) 改期保留 PENDING_RESOURCE；移除故障后同 key 重试可继续


#### UC-15-TC04 管理员拒绝保护原安排

- 极性：负面；优先级：P1；事件流：基本流+备选流2a

- 前置条件：
  1) 改期 PENDING_RESOURCE
  2) 唯一改变变量：decision

- 输入条件：
  1) API-32 POST /changes/{id}/resource-decision，body={decision:'REJECT',comment:'资源不足'}

- 操作步骤：
  1) 1) 拒绝
  2) 2) 查改期/预约

- 预期结果：
  1) status=REJECTED；原安排与预约不变；报名冻结解除


#### UC-15-TC05 最新资格不符或本人复核回避

- 极性：负面；优先级：P1；事件流：基本流+备选流3b

- 前置条件：
  1) 构造一：目标含 3113，资格已改为仅教师且所有者为 STUDENT；构造二：2001 所有者兼任 RESOURCE_CONFIRM 对本人改期确认
  2) 唯一改变变量：目标资格（构造一）或调用者（构造二）

- 输入条件：
  1) API-32（resource_admin，APPROVE）对应构造一；API-32（2001 令牌，APPROVE）对应构造二

- 操作步骤：
  1) 1) 构造一确认
  2) 2) 构造二确认

- 预期结果：
  1) 构造一 403 RESOURCE_INELIGIBLE：无切换、原安排保留（改期目标按最新资格重查）
  2) 构造二 403 FORBIDDEN（本人资源复核回避）


#### UC-15-TC06 活动取消后旧确认请求被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3c

- 前置条件：
  1) 活动已 CANCELLED（改期 CANCELLED_BY_ACTIVITY）
  2) 唯一改变变量：活动状态

- 输入条件：
  1) API-32 POST /changes/{id}/resource-decision，body={decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态与预约

- 预期结果：
  1) 409 INVALID_STATE（或按契约语义 404），显示当前状态；活动保持 CANCELLED、预约不得复活


#### UC-15-TC07 目标与自身旧占用重叠仍可生效

- 极性：正面；优先级：P1；事件流：基本流+BR-25

- 前置条件：
  1) 目标与自身旧占用部分重叠（如 10:30—11:30、布撤场各 30）；无其他活动冲突
  2) 唯一改变变量：目标时段

- 输入条件：
  1) API-32 POST /changes/{id}/resource-decision，body={decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 确认
  2) 2) 查新旧预约

- 预期结果：
  1) 200 APPLIED：重查排除自身旧预约后生效（不自竞争）；旧占用 RELEASED、新预约 ACTIVE


#### UC-15-TC08 资源确认范围仅覆盖部分必要资源被拒（API-19/API-32 双子例）

- 极性：负面；优先级：P1；事件流：基本流+备选流3b

- 前置条件：
  1) 双子例各自在独立数据集内执行（互不共享数据，允许隔离重复）；均复用 2004 resource_admin，经 API-39 临时把 managedResourceIds 缩减为部分必要资源
  2) 子例A 前置：活动 APPROVED_UNRESERVED、lockVersion=n、0 条预约，计划需 3101+3201，2004 范围仅含 3101
  3) 子例B 前置：改期 PENDING_RESOURCE、原有效安排与旧预约不变，目标需 3102+3202，2004 范围仅含 3102
  4) 唯一改变变量：RESOURCE_CONFIRM 管理范围覆盖度（复合资源须完整覆盖）；API-39 完成后先拍业务表快照，授权变更自身的审计记录不算业务失败

- 输入条件：
  1) API-39 PATCH /users/{2004}/authorization（临时缩减 managedResourceIds 为部分必要资源）
  2) 子例A：API-19 POST /activities/{id}/resource-confirm，body=VersionCommand{expectedLockVersion:n}（2004 令牌）
  3) 子例B：API-32 POST /changes/{id}/resource-decision，body={decision:'APPROVE',comment:''}（2004 令牌）

- 操作步骤：
  1) 1) 子例A独立数据集：API-39 将 2004 范围缩减为 [3101] 并拍业务表快照
  2) 2) 子例A 执行首次资源确认
  3) 3) 子例B独立数据集：API-39 将 2004 范围缩减为 [3102] 并拍业务表快照
  4) 4) 子例B 执行改期最终确认
  5) 5) 各自比对快照：活动/预约/审计/outbox

- 预期结果：
  1) 子例A 与子例B 均被拒绝（403 FORBIDDEN：RESOURCE_CONFIRM 须完整覆盖全部必要资源，）
  2) 子例A：status 保持 APPROVED_UNRESERVED、lockVersion=n、0 条预约
  3) 子例B：改期保持 PENDING_RESOURCE、原有效安排与旧预约不变
  4) 相对 API-39 后的快照，无 reservation/activity_version/业务 audit/outbox 变化；API-39 自身审计仅属前置准备，不纳入本次差异


### UC-16 撤回改期——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-16-TC01 | 所有者撤回待处理改期 | 正面 | P0 | 基本流 |
| UC-16-TC02 | 非所有者撤回被拒 | 负面 | P1 | 基本流+BR-02 |
| UC-16-TC03 | 终态改期不能撤回 | 负面 | P1 | 基本流+备选流3a |
| UC-16-TC04 | 撤回与最终确认并发保持一致终态 | 负面 | P2 | 基本流+备选流3b |

#### UC-16-TC01 所有者撤回待处理改期

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 改期 AWAITING_CONFIRMATIONS；owner 已登录
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-33 POST /changes/{id}/withdraw，body={reason:'重新安排'}

- 操作步骤：
  1) 1) 撤回
  2) 2) 查状态/冻结/活动安排

- 预期结果：
  1) 200 status=WITHDRAWN；报名冻结解除（JOIN 恢复）；原有效安排不变；可另建改期


#### UC-16-TC02 非所有者撤回被拒

- 极性：负面；优先级：P1；事件流：基本流+BR-02

- 前置条件：
  1) staff_a 已登录
  2) 唯一改变变量：调用者

- 输入条件：
  1) API-33 POST /changes/{id}/withdraw，body={reason:'测试'}

- 操作步骤：
  1) 1) 提交
  2) 2) 查状态

- 预期结果：
  1) 403 FORBIDDEN；改期状态不变


#### UC-16-TC03 终态改期不能撤回

- 极性：负面；优先级：P1；事件流：基本流+备选流3a

- 前置条件：
  1) 改期 APPLIED/REJECTED/CONFLICTED 之一
  2) 唯一改变变量：改期状态

- 输入条件：
  1) API-33 POST /changes/{id}/withdraw，body={reason:'测试'}

- 操作步骤：
  1) 1) 提交
  2) 2) 读取响应

- 预期结果：
  1) 409 INVALID_STATE 并显示当前状态；已生效的新安排不被撤销


#### UC-16-TC04 撤回与最终确认并发保持一致终态

- 极性：负面；优先级：P2；事件流：基本流+备选流3b

- 前置条件：
  1) 改期 PENDING_RESOURCE；两把独立连接+起跑栅栏
  2) 唯一改变变量：并发次序

- 输入条件：
  1) API-33 withdraw 与 API-32 resource-decision(APPROVE) 栅栏并发

- 操作步骤：
  1) 1) 并发执行
  2) 2) 双响应收集
  3) 3) 联合查库

- 预期结果：
  1) 一致终态：若 APPLIED 先提交则撤回 409 且新安排保留；若撤回先则确认 409；不得出现『已撤回但新预约生效』等部分状态（BR-30）


### UC-17 取消活动——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-17-TC01 | 取消活动释放全部关联 | 正面 | P0 | 基本流 |
| UC-17-TC02 | 重复取消返回当前结果不重复副作用 | 负面 | P0 | 基本流+备选流4b |
| UC-17-TC03 | 取消与改期最终确认并发一致 | 负面 | P0 | 基本流+备选流4a |
| UC-17-TC04 | 非所有者或已开始活动取消被拒 | 负面 | P1 | 基本流+备选流3a |
| UC-17-TC05 | 取消异常整体回滚可重试 | 负面 | P2 | 基本流+备选流4c |
| UC-17-TC06 | 已取消活动不得被旧请求复活 | 负面 | P2 | 基本流+BR-28 |

#### UC-17-TC01 取消活动释放全部关联

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 4001 OPEN：含 ACTIVE 预约、ACTIVE 报名与一个待处理改期；owner 已登录
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-21 POST /activities/{4001}/cancel，body=ReasonCommand{expectedLockVersion:n,reason:'活动取消'}

- 操作步骤：
  1) 1) 取消
  2) 2) 查 activity/reservation/change_request/activity_registration/outbox

- 预期结果：
  1) status=CANCELLED；reservation ACTIVE=0（全部 RELEASED）；待处理改期 CANCELLED_BY_ACTIVITY；有效报名 CANCELLED
  2) outbox 保存收件人快照（组织者∪工作人员∪报名人），通知异步发送


#### UC-17-TC02 重复取消返回当前结果不重复副作用

- 极性：负面；优先级：P0；事件流：基本流+备选流4b

- 前置条件：
  1) TC01 后活动已 CANCELLED
  2) 唯一改变变量：重复次数/幂等键

- 输入条件：
  1) API-21 cancel（同 key 重放）；再以新 key 重复取消

- 操作步骤：
  1) 1) 同 key 重放
  2) 2) 新 key 重复
  3) 3) 计数对比

- 预期结果：
  1) 两次均返回当前结果（合法所有者重复取消先校验所有权再幂等，BR-29）
  2) RELEASED 行数、outbox、版本数均不增长；活动不得复活


#### UC-17-TC03 取消与改期最终确认并发一致

- 极性：负面；优先级：P0；事件流：基本流+备选流4a

- 前置条件：
  1) 活动存在 PENDING_RESOURCE 改期；两把独立连接+起跑栅栏
  2) 唯一改变变量：并发次序

- 输入条件：
  1) API-21 cancel 与 API-32 resource-decision(APPROVE) 栅栏并发

- 操作步骤：
  1) 1) 并发执行
  2) 2) 双响应收集
  3) 3) 联合查库

- 预期结果：
  1) 一致终态：CANCELLED、0 条 ACTIVE 预约、0 个有效待处理改期
  2) 不得出现『改期生效后预约残留』或活动被旧请求恢复的部分状态


#### UC-17-TC04 非所有者或已开始活动取消被拒

- 极性：负面；优先级：P1；事件流：基本流+备选流3a

- 前置条件：
  1) applicant_b 登录（非所有者）；以及活动已开始（Clock 越过 startAt）
  2) 唯一改变变量：调用者（或开始时间）

- 输入条件：
  1) API-21 cancel（2002 令牌）；API-21 cancel（对已开始活动）

- 操作步骤：
  1) 1) 非所有者提交
  2) 2) 已开始活动提交

- 预期结果：
  1) 非所有者 403 FORBIDDEN；已开始 409 INVALID_STATE
  2) 均无状态变化


#### UC-17-TC05 取消异常整体回滚可重试

- 极性：负面；优先级：P2；事件流：基本流+备选流4c

- 前置条件：
  1) 可取消活动；测试挂钩注入取消事务异常
  2) 唯一改变变量：注入异常

- 输入条件：
  1) API-21 cancel（事务内注入 RuntimeException）

- 操作步骤：
  1) 1) 注入前后读快照
  2) 2) 移除故障后同 key 重试

- 预期结果：
  1) 整体回滚：活动/预约/报名/改期均恢复原状（无部分释放）
  2) 移除故障后同 key 重试成功


#### UC-17-TC06 已取消活动不得被旧请求复活

- 极性：负面；优先级：P2；事件流：基本流+BR-28

- 前置条件：
  1) 活动 CANCELLED
  2) 唯一改变变量：后续旧请求类型

- 输入条件：
  1) API-16 submit、API-18 approver-decision、API-22 JOIN、API-27 POST /activities/{4001}/changes（旧流程请求）

- 操作步骤：
  1) 1) 逐项发起旧请求
  2) 2) 查状态

- 预期结果：
  1) 各项 409 INVALID_STATE（或按语义 403/404），状态保持 CANCELLED、0 条 ACTIVE 预约


### UC-18 登记故障及查看影响——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-18-TC01 | 登记故障并计算受影响活动 | 正面 | P0 | 基本流 |
| UC-18-TC02 | 非法故障参数被拒（逐项单变量） | 负面 | P0 | 基本流+备选流2a |
| UC-18-TC03 | 越权登记故障被拒 | 负面 | P1 | 基本流+权限 |
| UC-18-TC04 | 故障下改期最终冲突且原安排与风险保留 | 负面 | P1 | 基本流+备选流6a |
| UC-18-TC05 | 重叠故障可用量下限与日历脱敏 | 负面 | P2 | 基本流+备选流4b+5a |

#### UC-18-TC01 登记故障并计算受影响活动

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) resource_admin 已登录；4001 占用 3201×2（09:30—11:30）
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-10 POST /resources/{3201}/faults，body=FaultInput{startAt:'2030-10-20T10:00:00+08:00',endAt:'2030-10-20T11:00:00+08:00',unavailableQuantity:1,reason:'灯泡更换'}
  2) API-11 GET /resources/{3201}/faults?from=2030-10-20T00:00:00+08:00&to=2030-10-21T00:00:00+08:00
  3) API-14 GET /activities/{4001}

- 操作步骤：
  1) 1) 登记故障
  2) 2) 查询故障与受影响清单
  3) 3) 查活动风险

- 预期结果：
  1) 201 FaultView；affectedActivities 含 4001（title/versionNo/resourceRisk=AT_RISK）
  2) 4001 resourceRisk=AT_RISK（查询时计算）且原 reservation 仍 ACTIVE（故障不删预约）
  3) API-11 可查故障；日历 FAULT 条目 quantity=1


#### UC-18-TC02 非法故障参数被拒（逐项单变量）

- 极性：负面；优先级：P0；事件流：基本流+备选流2a

- 前置条件：
  1) resource_admin 已登录；3201 库存 2、3101 为场地
  2) 唯一改变变量：每次仅一个 FaultInput 字段

- 输入条件：
  1) API-10 依次提交（差异项）：①unavailableQuantity=0；②unavailableQuantity=3（超库存）；③场地 3101 且 unavailableQuantity=2；④endAt≤startAt

- 操作步骤：
  1) 1) 逐项提交
  2) 2) 查 resource_fault 行数

- 预期结果：
  1) 各项 400 VALIDATION_ERROR（BR-10：数量正、单条≤库存、场地仅 1、start<end）
  2) 无 resource_fault 新增


#### UC-18-TC03 越权登记故障被拒

- 极性：负面；优先级：P1；事件流：基本流+权限

- 前置条件：
  1) account_admin（2007，仅 USER_MANAGE）已登录
  2) 唯一改变变量：调用者权限

- 输入条件：
  1) API-10 POST /resources/{3201}/faults，body=FaultInput（同 TC01）

- 操作步骤：
  1) 1) 提交
  2) 2) 查故障记录

- 预期结果：
  1) 403 FORBIDDEN（需 RESOURCE_MANAGE 且在资源管理范围内）
  2) 无故障记录


#### UC-18-TC04 故障下改期最终冲突且原安排与风险保留

- 极性：负面；优先级：P1；事件流：基本流+备选流6a

- 前置条件：
  1) 4001 原预约 ACTIVE 且受故障影响 AT_RISK；改期已创建并推进至 PENDING_RESOURCE，目标落在故障时段（可用量不足）
  2) 唯一改变变量：故障占用可用量

- 输入条件：
  1) API-24 POST /activities/{4001}/schedule-preview（目标含故障资源，对照）
  2) API-27 POST /activities/{4001}/changes（创建改期，BR-20 冲突目标仍可创建）
  3) API-32 POST /changes/{id}/resource-decision，body={decision:'APPROVE',comment:''}

- 操作步骤：
  1) 1) 预览目标
  2) 2) 创建改期并推进至 PENDING_RESOURCE
  3) 3) 最终资源确认
  4) 4) 查库

- 预期结果：
  1) 预览为只读成功：HTTP 200 且 feasible=false，conflicts 含 RESOURCE_FAULT 或 EQUIPMENT_SHORTAGE 精确区间与数量（不得把预览写成 HTTP 失败）
  2) 按 BR-20 创建改期仍返回 201（带冲突目标可创建、不占位）
  3) 最终 API-32 返回 409 RESOURCE_CONFLICT，details={conflicts:Conflict[],change:ChangeView}，改期置 CONFLICTED
  4) 原预约仍 ACTIVE 且实时 resourceRisk=AT_RISK（不承诺原安排可正常使用，BR-27）


#### UC-18-TC05 重叠故障可用量下限与日历脱敏

- 极性：负面；优先级：P2；事件流：基本流+备选流4b+5a

- 前置条件：
  1) 3201 已有重叠故障（合计不可用>库存）；student_b 已登录
  2) 唯一改变变量：故障叠加

- 输入条件：
  1) API-10 再登记一条重叠故障
  2) API-09 GET /resources/calendar?...&resourceId=3201（student_b 视角）

- 操作步骤：
  1) 1) 登记
  2) 2) 查日历

- 预期结果：
  1) 重叠故障拒绝把可用量算为负数：可用量下限为 0
  2) 普通用户日历只显示 label='资源不可用'，拒绝泄露登记人与私有原因（BR-05）


### UC-19 读取本人通知——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-19-TC01 | 读取并标记本人通知 | 正面 | P0 | 基本流 |
| UC-19-TC02 | 他人通知拒绝读取与已读 | 负面 | P0 | 基本流+备选流3a |
| UC-19-TC03 | 消费失败恢复与重复消费去重 | 负面 | P1 | 基本流+BR-31 |
| UC-19-TC04 | 已读不替代工作人员确认 | 负面 | P1 | 基本流+备选流4a |
| UC-19-TC05 | 空状态与跳转授权复核 | 正面 | P2 | 基本流+备选流1a+5a |

#### UC-19-TC01 读取并标记本人通知

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) student_a 有改期/取消事件产生的通知
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-34 GET /notifications?unreadOnly=true
  2) API-35 POST /notifications/{id}/read

- 操作步骤：
  1) 1) 列未读通知
  2) 2) 标记已读
  3) 3) 再查未读列表

- 预期结果：
  1) 列表仅本人条目，含 eventId/activityId/versionNo/title/body/createdAt
  2) API-35 返回 200 且 readAt=当前时间；unreadOnly 列表不再含该条


#### UC-19-TC02 他人通知拒绝读取与已读

- 极性：负面；优先级：P0；事件流：基本流+备选流3a

- 前置条件：
  1) student_b 已登录；存在 student_a 的通知
  2) 唯一改变变量：通知归属

- 输入条件：
  1) API-34 GET /notifications（对照）
  2) API-35 POST /notifications/{他人通知id}/read

- 操作步骤：
  1) 1) 查本人列表
  2) 2) 尝试已读他人通知

- 预期结果：
  1) 列表仅含本人条目（不泄露他人通知）
  2) 已读他人通知 403/404 且他人 readAt 不变


#### UC-19-TC03 消费失败恢复与重复消费去重

- 极性：负面；优先级：P1；事件流：基本流+BR-31

- 前置条件：
  1) 测试上下文注入通知消费者首次失败；业务提交（如改期创建）已发生
  2) 唯一改变变量：消费者故障

- 输入条件：
  1) 业务提交→消费首次失败→恢复→对同一 event 重复消费

- 操作步骤：
  1) 1) 触发事件
  2) 2) 注入首次失败
  3) 3) 恢复并重复消费
  4) 4) 查 notification/outbox

- 预期结果：
  1) 业务提交不回滚（投递失败不撤销已提交安排）
  2) 最终每 (eventId,recipientId) 恰 1 条通知；重复消费不新增；outbox attempts/状态如实


#### UC-19-TC04 已读不替代工作人员确认

- 极性：负面；优先级：P1；事件流：基本流+备选流4a

- 前置条件：
  1) 工作人员有待确认改期且收到相关通知
  2) 唯一改变变量：read 操作

- 输入条件：
  1) API-35 POST /notifications/{id}/read
  2) API-29 GET /changes/{id}（对照确认状态）

- 操作步骤：
  1) 1) 已读通知
  2) 2) 查 change_confirmation

- 预期结果：
  1) 仅 readAt 变化；change_confirmation 的 decision/decided_at 不变（已读≠确认）


#### UC-19-TC05 空状态与跳转授权复核

- 极性：正面；优先级：P2；事件流：基本流+备选流1a+5a

- 前置条件：
  1) 新账号无通知；以及一条指向无权对象的历史通知
  2) 唯一改变变量：通知数据/权限

- 输入条件：
  1) API-34 GET /notifications（新账号）
  2) 点击历史通知的跳转链接

- 操作步骤：
  1) 1) 查列表
  2) 2) 点击跳转

- 预期结果：
  1) 空状态提示（非报错）；跳转遇权限变化解释原因且不绕过授权（NFR-10）


### UC-20 查询报名及业务历史——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UC-20-TC01 | 名单、审计与改期历史查询 | 正面 | P0 | 基本流 |
| UC-20-TC02 | 普通学生不能请求全名单 | 负面 | P0 | 基本流+备选流2a |
| UC-20-TC03 | 工作人员视图裁剪影响名单 | 负面 | P1 | 基本流+备选流2b |
| UC-20-TC04 | 审计越范围查询被拒 | 负面 | P1 | 基本流+权限 |
| UC-20-TC05 | 无记录与截断语义如实 | 负面 | P2 | 基本流+备选流3a+4a |

#### UC-20-TC01 名单、审计与改期历史查询

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) owner 已登录；4001 有报名与改期历史
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-23 GET /activities/{4001}/registrations
  2) API-36 GET /activities/{4001}/audit
  3) API-28 GET /activities/{4001}/changes

- 操作步骤：
  1) 1) 查名单
  2) 2) 查审计
  3) 3) 查改期历史

- 预期结果：
  1) 名单 status 与真实计数一致（ACTIVE 计数）；审计含 actorId/eventType/versionNo/summary/createdAt 且只追加
  2) 改期历史含 baseVersionNo/appliedVersionNo，可核对当前安排来源


#### UC-20-TC02 普通学生不能请求全名单

- 极性：负面；优先级：P0；事件流：基本流+备选流2a

- 前置条件：
  1) student_b 已登录
  2) 唯一改变变量：调用者职责

- 输入条件：
  1) API-23 GET /activities/{4001}/registrations
  2) API-14 GET /activities/{4001}（对照本人状态）

- 操作步骤：
  1) 1) 请求全名单
  2) 2) 查本人报名状态

- 预期结果：
  1) 名单请求 403 FORBIDDEN；本人仅可见 myRegistrationStatus（不泄露他人名单）


#### UC-20-TC03 工作人员视图裁剪影响名单

- 极性：负面；优先级：P1；事件流：基本流+备选流2b

- 前置条件：
  1) staff_a 查看改期详情
  2) 唯一改变变量：查看者角色

- 输入条件：
  1) API-29 GET /changes/{id}

- 操作步骤：
  1) 1) 查询
  2) 2) 核对三个字段

- 预期结果：
  1) registeredUserIds 与 notificationUserIds 均为空数组；registeredCount 保持真实（BR-05）


#### UC-20-TC04 审计越范围查询被拒

- 极性：负面；优先级：P1；事件流：基本流+权限

- 前置条件：
  1) resource_admin 的管理范围不含该活动涉及资源；或 AUDIT_READ 管理员查询范围外活动
  2) 唯一改变变量：活动与管理范围的关系

- 输入条件：
  1) API-36 GET /activities/{范围外活动}/audit

- 操作步骤：
  1) 1) 查询
  2) 2) 对照范围内活动查询

- 预期结果：
  1) 范围外 403 FORBIDDEN（AUDIT_READ 仅限范围内相关活动）；审计记录不可改写


#### UC-20-TC05 无记录与截断语义如实

- 极性：负面；优先级：P2；事件流：基本流+备选流3a+4a

- 前置条件：
  1) 全新活动无操作记录；以及名单/记录 >200 项场景
  2) 唯一改变变量：数据量

- 输入条件：
  1) API-36 GET /activities/{新活动}/audit
  2) API-23 GET /activities/{大名单活动}/registrations

- 操作步骤：
  1) 1) 查空记录
  2) 2) 查大名单

- 预期结果：
  1) 空记录拒绝以示例冒充真实历史：显示『尚无操作』
  2) 超 200 项 truncated=true 且人数用真实计数，拒绝把截断结果当全量（NFR-19）


## 3 构造其它测试用例


### 3.1 用户界面测试用例


### UI-01 用户界面与导航（界面专项）——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| UI-01-TC01 | 11 类页面节点逐页加载 | 正面 | P0 | 基本流步骤1 |
| UI-01-TC02 | 核心导航路径往返 | 正面 | P1 | 基本流步骤2 |
| UI-01-TC03 | 未登录访问受保护路由 | 负面 | P0 | 基本流+备选流a |
| UI-01-TC04 | 视口降级与可访问性底线 | 负面 | P1 | 基本流步骤3+备选流b |
| UI-01-TC05 | 409/403 后保留输入并说明原因 | 负面 | P1 | 基本流步骤4+备选流d |
| UI-01-TC06 | 刷新与重启后状态来自持久化 | 正面 | P0 | 基本流步骤5 |

#### UI-01-TC01 11 类页面节点逐页加载

- 极性：正面；优先级：P0；事件流：基本流步骤1

- 前置条件：
  1) 三角色账号可登录；E2E Chromium 1366×768；真实前后端（BASE_URL）
  2) 唯一改变变量：页面节点

- 输入条件：
  1) 逐页访问 /login、/activities、/activities/new、/activities/:id、/resources/calendar、/resources/manage、/activities/:id/change、/changes/:id、/reviews、/notifications、/admin/users

- 操作步骤：
  1) 1) 逐页访问并等待加载
  2) 2) 检查交互元素（文本框/按钮/下拉）可显示可操作
  3) 3) 收集控制台错误

- 预期结果：
  1) 11 类页面均正确加载，交互元素可用；无未处理控制台错误
  2) /reviews 按角色显示待办；/admin/users 仅 ADMIN 且 USER_MANAGE 可见（NFR-06、05 界面规范）


#### UI-01-TC02 核心导航路径往返

- 极性：正面；优先级：P1；事件流：基本流步骤2

- 前置条件：
  1) 已登录 applicant_a
  2) 唯一改变变量：导航路径

- 输入条件：
  1) 路径 /activities→/activities/:id→/activities/:id/change→/changes/:id→/notifications→/activities

- 操作步骤：
  1) 1) 沿路径点击导航
  2) 2) 浏览器后退逐步返回
  3) 3) 核对状态

- 预期结果：
  1) 路径可正常导航且后退可用；面包屑/返回入口有效；页面状态不丢失


#### UI-01-TC03 未登录访问受保护路由

- 极性：负面；优先级：P0；事件流：基本流+备选流a

- 前置条件：
  1) 清空本地令牌
  2) 唯一改变变量：登录态

- 输入条件：
  1) 直接访问 /admin/users、/resources/manage、/reviews

- 操作步骤：
  1) 1) 逐个直连
  2) 2) 观察跳转与渲染

- 预期结果：
  1) 未登录请求被拒绝：跳转 /login 并提示登录；拒绝渲染任何业务数据（NFR-11）


#### UI-01-TC04 视口降级与可访问性底线

- 极性：负面；优先级：P1；事件流：基本流步骤3+备选流b

- 前置条件：
  1) 同一数据集；分别以 1366×768、1920×1080、390×844 打开
  2) 唯一改变变量：视口

- 输入条件：
  1) 390×844 查看活动与通知并执行工作人员确认；1366×768 检查长活动名、0 条与 5 条方案页面、弹窗

- 操作步骤：
  1) 1) 逐视口截图
  2) 2) 检查溢出/滚动/焦点/关闭按钮

- 预期结果：
  1) 窄视口下拒绝溢出与截断：1366 无全页横向溢出（仅日历容器滚动）；390 可完成查看与确认
  2) 状态拒绝仅靠颜色表达（含文字或图标）；弹窗有标题、焦点管理与可达关闭按钮（NFR-04/05）


#### UI-01-TC05 409/403 后保留输入并说明原因

- 极性：负面；优先级：P1；事件流：基本流步骤4+备选流d

- 前置条件：
  1) 制造旧版本提交（提交前他方更新活动）
  2) 唯一改变变量：版本

- 输入条件：
  1) UI 提交改期/活动表单触发 409 STALE_VERSION；以及 403 越权操作

- 操作步骤：
  1) 1) 触发错误
  2) 2) 检查提示、requestId 与表单内容

- 预期结果：
  1) 显示中文原因与 requestId，不出现原始异常/堆栈（NFR-01/02）
  2) 未提交表单输入保留并可重试（重试复用同一幂等键）


#### UI-01-TC06 刷新与重启后状态来自持久化

- 极性：正面；优先级：P0；事件流：基本流步骤5

- 前置条件：
  1) UI 主线完成一次改期生效（version2）
  2) 唯一改变变量：刷新/重启动作

- 输入条件：
  1) 浏览器刷新→docker compose restart server→重新登录查看

- 操作步骤：
  1) 1) 完成改期
  2) 2) 刷新
  3) 3) 重启后端
  4) 4) 新会话查看

- 预期结果：
  1) 状态来自真实持久化数据（version2/新安排/通知可见），无仅内存成功；失败场景重启后仍如实展示（NFR-16）


### 3.2 性能评测用例


### PERF-01 性能与容量评测（性能专项）——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| PERF-01-TC01 | 调度策略对比评测 | 正面 | P0 | 基本流 |
| PERF-01-TC02 | 候选预算、上限与排序稳定 | 正面 | P1 | 基本流+备选流b |
| PERF-01-TC03 | 列表与日历上限拒绝越界请求 | 负面 | P1 | 基本流+NFR-19 |
| PERF-01-TC04 | 20 并发响应与最终一致性测量 | 正面 | P1 | 基本流+NFR-20 |
| PERF-01-TC05 | 评测缺数据时拒绝生成假结果 | 负面 | P2 | 基本流+备选流c |

#### PERF-01-TC01 调度策略对比评测

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 独立数据集：先装载基准资源集 scheduling-reference.json，再装载 scheduling.json（8 场景）
  2) 唯一改变变量：策略（FIRST_FIT/RANKED）

- 输入条件：
  1) 调用策略评测入口 evaluateStrategies，读取基准资源集与 8 个调度场景

- 操作步骤：
  1) 1) 每场景每策略预热 1 次、测量 10 次
  2) 2) 生成 server/target/scheduling-evaluation.json
  3) 3) 运行 evaluate 输出 artifacts/evaluation/results.csv 与环境说明

- 预期结果：
  1) results.csv 字段=scenarioId/strategy/feasible/timeShiftMinutes/venueChanged/replacedEquipmentLines/evaluatedCount/elapsedMs；相同数据/候选/约束下两策略结果如实（成功率相同也报告）
  2) elapsedMs=elapsedNanos/1_000_000.0；记录机器、版本、输入文件 SHA-256；无解场景三代价字段为 null


#### PERF-01-TC02 候选预算、上限与排序稳定

- 极性：正面；优先级：P1；事件流：基本流+备选流b

- 前置条件：
  1) 多组合场景（含无解场景）
  2) 唯一改变变量：重复执行次数

- 输入条件：
  1) API-25 POST /activities/{id}/alternatives ×重复执行

- 操作步骤：
  1) 1) 多场景请求候选
  2) 2) 重复执行比对
  3) 3) 核对元数据

- 预期结果：
  1) evaluatedCount≤300、items≤5；排序按 (abs 时间偏移,是否换场地,替换项数,startAt,venueId,设备 JSON)；相同输入重复执行结果稳定（NFR-18/BR-19）
  2) truncated/maxEvaluations/offsetsMinutes 正确；无结果时说明仅当前搜索范围未找到


#### PERF-01-TC03 列表与日历上限拒绝越界请求

- 极性：负面；优先级：P1；事件流：基本流+NFR-19

- 前置条件：
  1) 任一已登录用户
  2) 唯一改变变量：请求范围/数据量

- 输入条件：
  1) API-09 GET /resources/calendar（8 天跨度）
  2) API-13 GET /activities（>200 项数据）

- 操作步骤：
  1) 1) 发起越界日历请求
  2) 2) 查询大列表

- 预期结果：
  1) 日历 8 天请求 400 VALIDATION_ERROR（最多 7 天）；前端提示缩小范围
  2) 列表 data≤200 且 truncated=true，不把截断结果伪装为全量（NFR-19）


#### PERF-01-TC04 20 并发响应与最终一致性测量

- 极性：正面；优先级：P1；事件流：基本流+NFR-20

- 前置条件：
  1) 真实服务与真实 PostgreSQL；20 个并发请求（登录、列表、日历、备选搜索、资源确认各一组）
  2) 唯一改变变量：并发负载

- 输入条件：
  1) ConcurrentRunner 20 并发（独立连接+起跑栅栏）对上述接口发起请求

- 操作步骤：
  1) 1) 并发执行
  2) 2) 记录各请求响应时间
  3) 3) 核对最终一致性
  4) 4) 统计中位数与范围

- 预期结果：
  1) 记录数据规模、环境、计时范围与原始记录；报告中位数与范围（NFR-20）
  2) 补充规约未规定秒数/CPU 上限：仅给『建议阈值（待项目经理/团队确认，不作为放行判据）』；出现错误响应或数据不一致即判失败


#### PERF-01-TC05 评测缺数据时拒绝生成假结果

- 极性：负面；优先级：P2；事件流：基本流+备选流c

- 前置条件：
  1) 移除或损坏调度场景数据文件
  2) 唯一改变变量：输入数据完整性

- 输入条件：
  1) 运行 npm --prefix qa run evaluate

- 操作步骤：
  1) 1) 缺数据执行
  2) 2) 检查退出码与产物

- 预期结果：
  1) 脚本失败退出非 0 并报告缺失；不生成虚构的评测结果文件


### 3.3 安全测试用例


### SEC-01 安全与访问控制（安全专项）——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| SEC-01-TC01 | 令牌生命周期与哈希存储 | 正面 | P0 | 基本流 |
| SEC-01-TC02 | 越权与 ID 枚举访问被拒 | 负面 | P0 | 基本流+备选流a |
| SEC-01-TC03 | 响应与日志敏感信息扫描 | 负面 | P0 | 基本流+备选流b |
| SEC-01-TC04 | 注入与非法输入不被执行 | 负面 | P1 | 基本流+备选流b |
| SEC-01-TC05 | 浏览器令牌存储与同源约束 | 负面 | P1 | 基本流+备选流c |
| SEC-01-TC06 | 健康检查免登录且不泄露配置 | 正面 | P1 | 基本流+NFR-23 |

#### SEC-01-TC01 令牌生命周期与哈希存储

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) applicant_a 可登录；数据库可查 auth_session
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) API-01 登录→auth_session 查询→API-03 退出→再查会话

- 操作步骤：
  1) 1) 登录并查库
  2) 2) 退出并查库

- 预期结果：
  1) 会话有效期 8 小时（NFR-07）；auth_session 仅存 token_hash 且≠明文；退出删除会话


#### SEC-01-TC02 越权与 ID 枚举访问被拒

- 极性：负面；优先级：P0；事件流：基本流+备选流a

- 前置条件：
  1) student_b 已登录
  2) 唯一改变变量：目标对象/操作

- 输入条件：
  1) API-23 GET /activities/{他人活动}/registrations
  2) API-36 GET /activities/{他人活动}/audit
  3) API-35 POST /notifications/{他人通知}/read
  4) API-14 GET /activities/{他人DRAFT}

- 操作步骤：
  1) 1) 逐项直接调用
  2) 2) 检查响应内容

- 预期结果：
  1) 逐项 403/404 且不泄露对象内容；ID 可猜测不构成授权（NFR-10）


#### SEC-01-TC03 响应与日志敏感信息扫描

- 极性：负面；优先级：P0；事件流：基本流+备选流b

- 前置条件：
  1) 可触发 400/401/403/409/500 的请求各一
  2) 唯一改变变量：响应类型

- 输入条件：
  1) 登录失败、校验失败、越权、冲突、内部异常（测试注入）各一次

- 操作步骤：
  1) 1) 收集响应
  2) 2) 递归扫描字段名与值
  3) 3) 检索服务端日志

- 预期结果：
  1) 无 password/passwordHash/token/数据库凭据出现；错误响应不含 SQL 与堆栈（NFR-08）


#### SEC-01-TC04 注入与非法输入不被执行

- 极性：负面；优先级：P1；事件流：基本流+备选流b

- 前置条件：
  1) 可创建草稿的账号
  2) 唯一改变变量：输入内容

- 输入条件：
  1) API-12 POST /activities（差异：title/description 含 <script>、引号与 SQL 片段；另试超长字符串）

- 操作步骤：
  1) 1) 提交注入内容
  2) 2) 读取展示
  3) 3) 触发一次 500 并检查响应

- 预期结果：
  1) 内容按数据原样存储/转义显示，脚本不执行、SQL 不生效；超长输入 400 VALIDATION_ERROR
  2) 错误响应不暴露 SQL（NFR-08/21）


#### SEC-01-TC05 浏览器令牌存储与同源约束

- 极性：负面；优先级：P1；事件流：基本流+备选流c

- 前置条件：
  1) E2E 浏览器已登录
  2) 唯一改变变量：存储位置/来源检查

- 输入条件：
  1) 开发者工具检查 sessionStorage/localStorage/cookie；检查 API 请求来源（同源代理）

- 操作步骤：
  1) 1) 登录后检查存储
  2) 2) 退出后复查
  3) 3) 检查请求 URL

- 预期结果：
  1) 拒绝把令牌持久化到不安全位置：仅 sessionStorage（不落 localStorage/长效 cookie），退出清除（NFR-11）
  2) API 经同源代理访问，拒绝任意来源携带敏感凭据


#### SEC-01-TC06 健康检查免登录且不泄露配置

- 极性：正面；优先级：P1；事件流：基本流+NFR-23

- 前置条件：
  1) 无令牌客户端
  2) 唯一改变变量：无

- 输入条件：
  1) API-37 GET /health

- 操作步骤：
  1) 1) 调用
  2) 2) 扫描响应

- 预期结果：
  1) 200 {status:'UP'}（免登录例外）；响应不含数据库连接、密钥或版本配置等信息


### 3.4 安装测试用例


### INSTALL-01 安装与部署（安装专项）——测试用例


| 用例编号 | 场景简称 | 极性 | 优先级 | 事件流 |
|---|---|---|---|---|
| INSTALL-01-TC01 | 干净环境按发布说明启动并跑通主线 | 正面 | P0 | 基本流 |
| INSTALL-01-TC02 | 缺配置启动失败并明确提示 | 负面 | P0 | 基本流+备选流a |
| INSTALL-01-TC03 | 迁移失败不删数据卷掩盖 | 负面 | P1 | 基本流+备选流b |
| INSTALL-01-TC04 | 检查命令对缺测试/零测试失败 | 负面 | P1 | 基本流+备选流c/NFR-29 |
| INSTALL-01-TC05 | 版本一致与备份恢复核对 | 正面 | P1 | 基本流+NFR-28/30 |

#### INSTALL-01-TC01 干净环境按发布说明启动并跑通主线

- 极性：正面；优先级：P0；事件流：基本流

- 前置条件：
  1) 干净机器/空数据库；仅 release/ 发布包与发布说明（含 manifest）
  2) 唯一改变变量：无（有效基准）

- 输入条件：
  1) docker compose up -d（db、server、web）；API-37 GET /health；按角色执行 主线

- 操作步骤：
  1) 1) 启动
  2) 2) 健康检查
  3) 3) 按角色完成主线
  4) 4) 记录命令输出

- 预期结果：
  1) 真实后端/数据库连通；主线可完成且无手改数据库才可继续的步骤；UI 与 API 状态一致（NFR-27）


#### INSTALL-01-TC02 缺配置启动失败并明确提示

- 极性：负面；优先级：P0；事件流：基本流+备选流a

- 前置条件：
  1) 同 TC01 环境
  2) 唯一改变变量：环境变量（缺 DEMO_PASSWORD 或数据库连接串）

- 输入条件：
  1) docker compose up -d server（缺变量）

- 操作步骤：
  1) 1) 缺变量启动
  2) 2) 观察退出与日志

- 预期结果：
  1) 启动失败并输出明确缺失项；不半初始化、不返回伪造成功页面（NFR-27）


#### INSTALL-01-TC03 迁移失败不删数据卷掩盖

- 极性：负面；优先级：P1；事件流：基本流+备选流b

- 前置条件：
  1) 测试环境已存在数据卷与已应用迁移
  2) 唯一改变变量：迁移脚本（注入坏迁移）

- 输入条件：
  1) 追加一个语法错误的 Flyway 迁移后启动 server

- 操作步骤：
  1) 1) 启动观察 Flyway 输出
  2) 2) 检查数据卷保留
  3) 3) 修复后重新启动

- 预期结果：
  1) 迁移失败明确报错；数据卷保留不被删除（NFR-28）
  2) 修复后可继续升级且核心关系可核对


#### INSTALL-01-TC04 检查命令对缺测试/零测试失败

- 极性：负面；优先级：P1；事件流：基本流+备选流c/NFR-29

- 前置条件：
  1) 测试集合可运行
  2) 唯一改变变量：测试集合完整性（临时移除某模块测试）

- 输入条件：
  1) pwsh -File scripts/check.ps1 -Scope all

- 操作步骤：
  1) 1) 缺测试执行
  2) 2) 检查退出码

- 预期结果：
  1) 缺测试/零测试时拒绝绿色通过：退出码非 0 并指明缺失测试；不吞退出码（NFR-29）


#### INSTALL-01-TC05 版本一致与备份恢复核对

- 极性：正面；优先级：P1；事件流：基本流+NFR-28/30

- 前置条件：
  1) 已启动环境；release manifest 与文档齐备
  2) 唯一改变变量：无（核对性操作）

- 输入条件：
  1) 核对 release manifest 版本号；执行备份→恢复→核心关系抽查

- 操作步骤：
  1) 1) 核对版本
  2) 2) 备份恢复演练
  3) 3) 抽查核心关系

- 预期结果：
  1) 版本号与测试报告、用户手册一致（NFR-30）；恢复后核心关系可核对（NFR-28）；过程证据留存 artifacts/


## 4 用例统计

| 指标 | 数值 |
|---|---|
| 场景组数（业务 20 + 专项 4） | 24 |
| 用例总数 | 138 |
| 正面用例 | 47 |
| 负面用例 | 91 |
| 状态「未执行」 | 138 / 138 |
