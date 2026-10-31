# 需求与界面说明交接记录

## 本次交付

- 日期、任务：2026年10月9日；按用户“阅读当前项目，完成软件需求规约及界面说明，先用Markdown”的要求编制需求材料，对应AN-01文档部分及界面说明。
- 基准：开发任务包v2.0；当前Git HEAD `364f07b`，分支`work/55230223-foundation`；最新开发计划v0.5。未切换分支、提交代码或建立需求基线。
- 交付：`software-requirements.md`、`use-cases.md`、`supplementary-specification.md`、`business-rules.md`、`traceability.md`及`docs/ui/interface-description.md`，本记录为配套交接。
- 完成范围：25条功能需求、20个用例、32条业务规则、30条补充需求、24个原验收场景及6个建议新增场景的追踪；11类页面、12幅文本线框及操作说明。
- 原资料读取：README、AGENTS、taskbook、5份公共契约、6份岗位任务、项目计划及交接、配置目录指南、测试计划／设计／数据计划／交接，QA工程、后端测试支撑及验收测试、演示与场景数据；课程Word／Excel内容及本地历史计划文档作背景核对。课程考核表通过Word只读提取，原件未改动；未开展原Office排版评审。
- 当前产物性质：需求评审稿和界面设计说明，非已批准基线、产品截图、测试报告或实现完成证明。

## 当前项目与差异

工作区前端仅有占位文件，后端有测试而未见生产源码、POM和Wrapper；OpenAPI、SQL迁移、Compose及运行脚本未见实际文件。测试存在不能证明业务通过，未尝试用Mock演示已实现。

最新计划要求三角色、独立教师／学生身份、按资源资格、审批及管理范围、本人的业务审批与资源复核回避。旧任务包和测试仍为五角色，没有身份、资格、范围字段。本次没有改旧契约、数据或测试；拟统一口径在需求规约CR-01～04列出，正式评审后由各所有者同步。

发现的具体既有差异：`server/src/test/java/com/campus/events/acceptance/CancellationRaceIT.java`对其他key再次取消的断言为409，而AN-03与基线要求合法所有者重复取消返回当前结果；本稿按基线写BR-29，建议QA修正断言，先保证所有权后处理终态。

初始项目计划历史材料中出现“需求分支已有需求和状态机”的描述，但当前工作区并无该实现，本稿不将其计入当前实现清单，也不推断其他分支已完成；正式集成时可另核实分支产物。工具表与公共任务书的缺陷渠道表述也有差异，本次不改工具选择原件；不影响本文业务规约。

## 待交接动作

| 接收岗位 | 下一动作 |
| --- | --- |
| 项目经理 | 组织CR-01～04及TBR评审，统一角色／身份／资格／范围契约，登记正式结论；明确最终项目名 |
| 系统分析员 | 补充真实制度及资源资格调研、审批复合类别及资格失效状态规则，维护主需求和追踪 |
| 设计师 | 将身份、权限、资源资格及历史建模；同步预览／候选过滤／最终检查，复核新旧资源事务边界 |
| 界面设计师 | 依据12幅线框制作可查看原型，确认字段与路由，后续替换真实截图并编写用户手册 |
| 测试经理 | 同步旧角色测试，落实N-AT-01～06，修正重复取消偏差，补身份变化、停用、撤回竞争和恢复测试 |
| 配置经理 | 正式评审后登记需求版本，关联实现及测试证据，核对发布文档；补充通知失败运维及恢复说明 |

## 检查结果

本次检查需求编号、交叉引用、本地链接、Markdown结构、表格列数和文档间状态／角色／数量一致性。文档结构检查脚本实际退出码0：本地链接均有效，表格列数一致，代码围栏闭合，FR25／UC20／BR32／NFR30／UI11编号数量及唯一性正确，12幅图编号唯一；`git diff --check`实际无报错。对新增未跟踪文档另由结构脚本覆盖检查；检查脚本通过PowerShell调用工作区依赖的Python执行，未作为业务测试写入证据目录。产品构建、Java集成、E2E、24项D-AT和新增N-AT未执行，无新增业务通过结论。

接收确认：尚未取得实际评审或接收反馈。


## 课程提交稿修订（2026年10月9日）

- 用户明确文档用于课程提交，应符合软件综合实践课程设计性质。
- 提交正文调整为正式需求与界面设计表述，移出仓库调查、旧角色迁移、接口缺口、测试偏差及编写建议。
- 保留三角色、教师／学生身份、具体资源资格、20个用例及补充需求；不改变公共契约或声称产品已实现。
- 界面线框仍为设计示意，未登记为真实产品截图。
- 以下保留上版需求第9章的差异和待决内容，供项目组内部评审，不作为课程正文。


### 9.1 版本差异及同步范围

| 编号 | 需统一的事项 | 本稿处理 | 需要同步的产物 |
| --- | --- | --- | --- |
| CR-01 | 五角色改为三角色，教师／学生独立为身份 | 正文使用最新计划口径；不自行定义替代Role枚举 | 公共DTO、认证、数据模型、OpenAPI、种子、页面与测试 |
| CR-02 | 具体资源身份资格维护 | 明确四种组合与校验节点；现有ResourceCreate／ResourceView没有资格字段 | 资源数据及管理接口、资格历史、候选过滤、最终确认 |
| CR-03 | 审批组织和类别范围、管理员细分权限与管理范围 | 采用范围检查要求；不把账号管理等同于业务权限 | 授权数据、查询过滤、复合资源审批策略、管理界面 |
| CR-04 | 本人资源复核回避 | 与最新计划一致，首次和改期均禁止本人确认 | 服务授权、负向用例与UI提示 |
| CR-05 | 测试与契约偏差 | 重复取消遵循任务包“合法所有者重复返回当前结果”；现有CancellationRaceIT对不同key再次取消预期409，需修正 | 对应测试设计、测试代码和验收记录 |

### 9.2 尚待确定的细节

| 编号 | 待决问题 | 评审方向与不变约束 |
| --- | --- | --- |
| TBR-01 | 三角色、身份、细分权限的字段及枚举 | 由项目经理组织契约修订；不得将TEACHER直接等同于批准人 |
| TBR-02 | 多类别资源由单一批准人完整覆盖还是多批准人联合审批 | 建议首版一个批准人覆盖全部必要类别，待评审；范围不足时不得审批 |
| TBR-03 | 最终复核时资格已变、批准人／工作人员被停用的处置 | 必须拒绝无资格或无权限操作且保留原安排；明确错误码、终态或撤回重建策略，避免永久冻结 |
| TBR-04 | 账号身份变更对待办与既有预约的影响 | 已生效预约不因资格调整自动删除；身份变更的生效节点和记录需统一 |
| TBR-05 | 通知具体收件人表、账号／资源名称长度、数据保留和性能阈值 | 保留既有事件语义和隐私限制，后续补齐可测边界；不填未经测量的性能承诺 |
| TBR-06 | 本校制度、具体资源名称及真实资格、最终产品名 | 需真实调研或项目组确认；本稿不构造访谈、制度或批准记录 |

以上事项不影响共同业务流程的描述，但相关接口与新增授权规则未统一前，不应宣称完整实现或正式需求基线已经成立。


### 课程提交稿检查结果

六份提交及配套文档统一为v1.1，需求编号FR25／UC20／BR32／NFR30保持完整；界面11类页面、12幅示意编号正确，20个用例均包含9项说明字段。本地链接、表格列数、代码围栏及内部CR／TBR引用移出正文检查通过，结构脚本退出码0；git diff --check无报错。未修改业务代码、公共契约或课程原始文件，未执行产品业务测试。

### 2026-10-10 Word 课程提交版迁移

按用户要求生成三份独立 Word 文档：软件开发计划 v0.5、软件需求规约 v1.1、用户界面说明文档 v1.1。需求规约合并用例规约、补充规约及业务规则、需求追踪附录；界面说明保留设计示意图及文字说明。全文使用宋体，仅封面标题与各级标题加粗，正文、表格表头和图中文字不加粗；附封面、可更新目录与页码。原 Markdown 及旧版 Word 保留。

检查记录：本机 Word 更新目录并导出预览，检查页面图像、分页、表格和示意图，修复封面装饰线、多余空白页及孤立表头。最终页数分别为20、55、17；文字字体与非标题加粗检查未发现异常。内置渲染工具因缺少 LibreOffice 未运行成功，改用 Word 预览。此轮仅迁移文档，未运行产品业务测试。

## 系统分析员开发交接（2026-10-13）

- 成员/任务：冯玉琪（22220314），AN-01～05；本轮起始基准为 `origin/main` 的 v3.0 任务包提交 `5097af7`。
- 同步方式：以 rebase 将本分支状态机提交移植到 `origin/main`，明确未带入旧需求材料提交 `bf0cef7`；保留 main 上需求规约 v1.1、追踪矩阵及新增个人任务书。
- 本轮状态机文件：`server/src/main/java/com/campus/events/activity/ActivityStateMachine.java`、`server/src/test/java/com/campus/events/activity/ActivityStateMachineTest.java`。按基线 v3.0 使用 `PENDING_APPROVER`；覆盖创建、草稿编辑、提交、批准、驳回回 DRAFT、待审/已批准撤回生成新版本、资源确认、发布、报名 lockVersion 递增、取消幂等和取消后所有业务转换均拒绝。
- 验证：`git diff --check` 无错误。
- 本轮实测：`java -version`、`javac -version` 均因命令不存在而失败；`Test-Path server/pom.xml`、`Test-Path server/mvnw.cmd` 均为 False；按个人任务书执行 `./server/mvnw.cmd test`，PowerShell 报命令不存在。因此 7 个 `ActivityStateMachineTest` 用例未执行，不能报告通过。
- 后续任务：AN-02～04 的 repository、service/controller、ActivityPortAdapter、审批、报名并发及取消联调，等待 PM 的 Java 公共类型/构建、DS 的 v3 schema/SchedulingPort、CM 的 ChangePort/EventPort；仅修改 `activity/`、`registration/` 和本人需求文档，不改共享类型或他人模块。
- 接收确认：尚未接收。

## 最新main同步与后续依赖（2026-10-13）

- 基准：已同步到 `origin/main` 的 `b96e4ca`（包含 DS-01 schema v3.0 与 CM-01 环境/配置计划/演示数据框架）；本人分支以 rebase 保留状态机两个提交，并明确不包含已被新版取代的 `bf0cef7` 旧需求提交。
- 本次复核：`server/src/main/java` 仍只有活动状态机与 CM 的 dev 种子代码；`server/pom.xml`、Maven Wrapper、PM 的 `contract`/`foundation`/`auth` 生产类型均未进入 main。Activity/registration 持久层、控制器及 ActivityPort、SchedulingPort、ChangePort、EventPort 接口也未进入 main。
- AN-01 已有状态机和测试源码、API 技术标识同步及本交接记录。Maven 命令仍无法实际执行：`server/mvnw.cmd` 与 `server/pom.xml` 不存在，且当前环境没有 `java`/`javac`；测试不得记为通过。
- AN-02～04 不能在缺少 PM 的共享 record/端口、CommandExecutor 与 Maven 工程时安全实现并编译；AN-03/04 还依赖 DS 的 SchedulingPort 与 CM 的 ChangePort/EventPort。已由 DS 合入的 schema v3.0 不代替这些 Java 接口。
- 下一步：PM 合入公共构建/DTO/端口后，先实现 API-12～17 与 ActivityPortAdapter；DS/CM 端口合入后实现 API-18～23 和取消事务，再运行本模块单测及真实 PostgreSQL IT。
- 接收确认：尚未接收。

## AN-02～05 活动与报名交付（2026-10-25）

- 成员：冯玉琪（22220314），系统分析员；分支 `work/22220314-activity`，实际 main 基准 `34dc4d5`。以 fast-forward 同步 main，PR #3 状态机生产实现保持原样；旧需求提交 `bf0cef7` 未重新引入。
- 生产交付：`activity/ActivityRepository.java`、`ActivityService.java`、`ActivityController.java`、`ActivityViews.java`、`ActivityPortAdapter.java`，以及 `registration/RegistrationRepository.java`。API-12～23 及 ActivityPort get/lock/activatePlan/bumpLockVersion 已有生产实现。使用现有共享 DTO、认证、幂等回执和响应，无公共类型/迁移/他人模块改动。
- 业务：草稿与不可变版本；数据库先过滤后截断的本人/公开/审核列表；HTTP 工作人员与名单裁剪；最新用户身份/审批组织及类别/资源复核范围与本人回避；提交资格重查、首次联合预约、发布风险检查；活动锁下报名容量与重复操作；取消关闭改期、释放预约、取消 ACTIVE 报名、收件人快照及事件共事务。
- 适配器：MANDATORY 写入口与显式活动锁要求；切换计划只复制元数据/替换计划、推进 current/effective 与 ACTIVE 报名版本，不写预约或通知。独立组件不依赖上层 Service，供 CM 调用；接口细节、API 请求和失败码见 [activity-api-handoff-v1.0.md](activity-api-handoff-v1.0.md)。
- 本次修复：真实并发测试暴露活动/版本 JOIN 在锁等待期间切换版本可能误报不存在，已改为先锁活动主行、再以新语句读取版本；确定性 PostgreSQL 锁等待回归与 3 次取消/切换竞争通过。旧 `ActivityStateMachineTest:49` 原本错误检查撤回前不可变快照，改为检查撤回后的新 DRAFT；不改状态机生产代码。
- 实际环境：本轮下载微软官方便携 JDK 21.0.12.1 与 PostgreSQL 17.6，在被忽略的 target 中使用；Maven Wrapper 3.9.11。真实库仅 `127.0.0.1:5440/campus_test` 随机 `an_<UUID>` schema；每轮清理 schema，结束停止本轮便携 PostgreSQL 实例。
- 默认检查：`./server/mvnw.cmd -B -ntp -f server/pom.xml -Dtest=ActivityStateMachineTest,ActivityServiceTest test` 退出 1，现有 CM `dev/DemoDataSeeder.java:118` 未处理 `seedActivity` 声明的受检异常；尚未进入默认测试。没有代改该文件或公共 POM。
- 模块实际检查：以 `server/target/an-build/pom.xml` 临时复制公共 POM，源码仍指向真实 server/src；仅排除上述 CM `DemoDataSeeder.java`，测试编译仅包含 activity/registration。执行 `./server/mvnw.cmd -B -ntp -f server/target/an-build/pom.xml -Dtest=ActivityStateMachineTest,ActivityServiceTest -Dit.test=ActivityRegistrationIT verify`（本地仓库缓存参数同本机 target 配置），退出 0：**18 项单测 + 17 项真实 PostgreSQL IT，0 失败、0 错误、0 跳过**。运行日志：本地 `server/target/an-default-test.log`、`an-verify.log`；Surefire/Failsafe 报告在 `server/target/an-build/target/`。详情和隔离构建复现说明见 [activity-module-validation-v1.0.md](activity-module-validation-v1.0.md)。`git diff --check` 无错误。
- 覆盖：真实认证/回执重放及全部 API-12～23 HTTP 路由、版本追加/旧版本拒绝、当前范围/身份/资格、公开隐私、20 学生竞争 1 名额恰 1 成功、重复报名/退出、改期冻结、取消全链路回滚、改期版本回滚、活动行锁等待期间的新版本读取和取消后不可复活。对应 D-AT-03/15/16/17/20、N-AT-02/05/06 的本模块行为；不等同于 QA 全套验收通过。
- 外部依赖：真实 SchedulingPortAdapter、AccountPort/CommandExecutor 已使用；**CM 的 ChangePort/EventPort 生产实现仍未进入 main**。测试源码中明确提供这两个端口的数据库测试替身；事件消费者/真实 CM 改期和通知集成未验证，不宣称 M2/M3/E2E 或应用重启全链路通过。生产缺所需端口返回 503 并回滚；草稿创建/编辑与未预约视图可独立使用，SCHEDULED/OPEN 视图须真实 ChangePort。
- 下一接收：CM 杨瀚霖接 ActivityPortAdapter/取消编排；UI 杨畅、QA 汤雨润接 API-12～23/失败码；PM 冀项阳接公共工程集成。待 CM 修复种子编译并交付真实 ChangePort/EventPort 后，用默认构建和真实全部端口复验，继续 M2/M3。交叉评审后才可合并，未自审自批。
- 实际交付：生产代码/测试提交 `9b76b2e` 已推送；以本人 GitHub 账号 `Damer-F` 创建 [PR #11](https://github.com/Rito-492/campus-sched/pull/11)，已实际请求 PM `Rito-492` 交叉评审，尚未评审或合并。
- 接收确认：尚未接收。

## PR #11 交叉评审修复（2026-10-29）

- 评审被审提交：9b76b2e、0886c8b；本轮fetch后main仍为34dc4d5。作者冯玉琪（22220314），Damer-F，继续在work/22220314-activity修复。
- 三条阻塞项：API-13补齐当前APPLICANT与所有者/工作人员/任意报名历史SQL并集及去重；API-14允许历史报名者读取对应私有/取消活动详情，仍裁剪人员并拒绝名单；发布的时间/状态/风险Service门禁补足负向断言及真实HTTP失败证据，追踪矩阵已更新。
- 关联建议：按A移除无依据的待改期禁止发布，保留UC-09和AN-03要求的无风险发布；按F先核对归属再锁工作人员；HTTP人数改count(*)、审批版本不变量补注释、404人员缺失文档补齐。原状态机生产实现不变。
- 实测：先运行新增单测复现旧代码问题（25项中2失败、2错误、0跳过）；修复后隔离verify退出0，25单测+22真实PostgreSQL17.6 IT，0失败/错误/跳过。默认test仍退出1于CM种子第118行，不报告默认构建通过。测试后清理schema并停止本轮实例。
- 证据：原始日志、3份JUnit XML/TXT、实际隔离POM和源码SHA-256归档[artifacts/test-results/an-2026-10-29](../../artifacts/test-results/an-2026-10-29/)，未含本轮数据库凭据。命令和具体覆盖见[验证记录](activity-module-validation-v1.0.md)复验节；逐条建议处理见[评审修复说明](activity-review-response-v1.0.md)。
- 文档交接：两份新增接口/验证文档补v1.0命名；UI/QA须使用mine并集、历史报名详情、发布不因改期阻断以及404分支的新说明。B/C/D/I及其他歧义留PM统一确认，未私改共享契约；完整CM端口、默认构建、E2E/重启恢复仍待集成。
- 下一接收：PM/QA等非作者成员复核PR #11三条必须改；作者不自审自批或自动合并。接收确认：尚未接收。

## AN-04/05 后续回归（2026-10-29）

- 冯玉琪22220314；work/22220314-activity；本地origin/main基准34dc4d5。此次fetch遇到GitHub连接重置，不宣称已获取更新后的远端基准；未reset或改他人文件。
- 仅扩展本人ActivityRegistrationIT：其余写路由幂等重放、同键异请求拒绝、非法容量创建/编辑回滚、过去安排资源确认拒绝；取消/改期竞争由3次增至10次。生产代码与公共契约无变更。
- 隔离verify真实通过25单测+33 PostgreSQL IT，0失败/错误/跳过；证据见artifacts/test-results/an-2026-10-29-followup，复现入口见activity-module-validation-v1.0.md。默认构建本轮未重跑，不将隔离构建等同产品完整验收。
- 下一接收：PM/QA复核PR #11；CM仍须交真实ChangePort/EventPort与种子编译修复。列表剩余N+1优化、PM契约待决项、默认构建及重启/E2E联调仍未完成。尚未接收。

## 标准构建与CM接入核验（2026-10-31）

- 冯玉琪22220314；AN-02～05；基线main=feb0d70，work/22220314-activity安全快进同步，无未提交修改丢失。PR #11已合入，不重复开发生产业务。
- 标准server/pom.xml回归已实际通过：25单测+35真实PostgreSQL IT，0失败/错误/跳过。未使用此前隔离POM，Seeder编译阻塞已解除；执行的是AN测试集合，不是全仓库验收。
- 本轮仅补强三种改期待处理状态的报名/退出冻结、详情activeChangeId清除，以及切换版本后旧版本请求拒绝且报名保留；已有取消、待办、版本回滚和API-12～23回归重跑。证据artifacts/test-results/an-2026-10-31，命令与限制见activity-cm-integration-v1.0.md。
- **真实CM接入阻塞**：feb0d70仍只有ChangePort/EventPort接口，生产实现未交付；现有IT继续使用显式测试替身。提供者CM杨瀚霖；需生产pendingId/hasPending/cancelPending/append及改期调用链后复验。不得宣称M2/M3完成。
- 下一接收：CM交生产端口，PM/QA交叉评审本轮结果，UI了解待办与版本字段仍受真实CM依赖限制。通知消费、重启一致性和E2E未验证，尚未接收。此前记录中的默认构建失败为历史结果，以本节标准AN回归通过状态为准。
