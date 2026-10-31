# UI 交接单 · 2026-10-31

成员：杨畅（17231018），界面设计师。任务包 v3.0；本人分支 `work/17231018-ui`。

## PR提交准备与最新main同步（2026-10-31）

杨畅已明确授权提交PR。原始开发/首轮联调基准保留为feb0d70；本次fetch发现main已推进到`c4f3d413fa1214d127bfff1b3cca0e1ed4b68ddd`（调度检查一致性更新），已通过合并安全同步，未reset或覆盖本地修改。PR目标为`Rito-492/campus-sched:main`，源为`ychangz1/campus-sched:work/17231018-ui`；原仓库无写权限，通过已有fork交付，保持草稿供交叉评审，不自动合并。

提交检查修正了资格联调用例对前一用例/旧运行状态的依赖：现在经真实API创建独立资源和草稿，持久化检查单独记录草稿场地。最新main上的前端types:check/typecheck、78项unit、build、5项真实局部联调与1项后端重启检查均通过，记录放在`artifacts/test-results/ui-pr-2026-10-31/`，截图另存`artifacts/screenshots/ui-pr-2026-10-31/`。先前截图与日志保留，不改写为本次基线证据。

最新后端通过`mvnw -DskipTests package`重新打包，仅为启动本地联调；**此命令不是verify通过证据**。此前JDK21全量verify的8项QA数据错误对应feb0d70，完整日志仍保留。CM端口/改期通知缺口、完整D-AT-23/24和QA E2E继续列为待集成/验收。

## 最新：真实局部联调与交付边界（2026-10-31）

本轮继续保留全部本地修改，main仍为feb0d70；未改后端、QA、公共契约、课程原件或Word，未推送/创建PR。完整运行方法、失败请求、QA脚本适配和证据索引见[真实联调记录](real-integration-2026-10-31.md)。以下结论更新此前“无后端、真实联调全部未执行”的历史记录。

现已运行未修改的main后端（JDK21.0.9）和独立PostgreSQL17.6，完成真实登录/退出/权限导航/403、账号创建与授权/停用、资源创建/资格、日历、草稿创建编辑/预览、资格失败具体资源提示，以及后端重启后的上述数据保留。新增拒绝理由弹窗及QA扩展testid；生产build仍剔除fixture。

验证：types:check、typecheck、build通过；**18文件78项unit、13项布局、5项真实局部联调及1项重启持久化检查通过**。JDK21全量verify已运行，70项unit通过、93项IT中8项数据装载错误，整体失败。报告在`artifacts/test-results/ui-integration-2026-10-31/`，真实截图15张在`artifacts/screenshots/ui-integration-2026-10-31/`。没有把局部结果登记为完整E2E。

| 任务 | 当前状态 | 未完成边界 |
|---|---|---|
| UI-01 | 可安装/构建工程、11路由、布局、API/types、开发原型及3视口检查已完成 | 尚未通过PR交付 |
| UI-02 | 页面实现及上述认证/账号局部真实联调完成 | 全部权限组合、旧会话失效仍需QA完整验收 |
| UI-03 | 页面实现、草稿创建编辑/预览及刷新/重启复验完成 | 提交审批503，首次预约/发布/报名主线待CM EventPort/ChangePort及AN集成 |
| UI-04 | 页面实现，资源/资格/日历局部真实联调完成 | 故障登记503；真实故障风险联动、候选闭环待后端服务 |
| UI-05 | 页面、拒绝弹窗、稳定标识及文档完成 | CM改期/通知/审计返回404，完整真实闭环及截图待服务 |

D-AT-23仅完成局部持久化子集，D-AT-24/Compose发布未验收。Java21已可用，verify的8项错误归QA数据装载；CM优先交EventPort/ChangePort。UI独立可做的补齐已落地，完整完成仍需依赖到位及QA交叉验收。接收状态：**尚未接收**。PR已获用户授权，提交状态见顶部。

## 前轮：UI-03～05页面与请求流程（历史记录，未提交、未推送）

继续执行用户要求“完成未完成内容，提交PR前征询意见”。本轮fetch后origin/main仍为feb0d70，本地分支包含该基线；保留e100e0b后的预览和UI-02修改，未reset、未覆盖Word。没有新建PR、推送、合并或联系其他成员。

已补齐：

- UI-03：详情内草稿编辑/API-15、工作人员/报名名单/改期/审计页签；保存冲突等待最新lockVersion并保留输入。API-25候选可填入草稿，保存后再预览。待办按scope=review聚合待处理改期，关联请求最多6并发，部分失败和截断单独提示。
- UI-04：资源新增、管理范围、API-40教师/学生独立资格开关、故障登记与查询/受影响活动；日历半小时刻度、同时设备用量及叠加故障扣减，端点相接不重叠，最低可用0；候选最多5项、2～3项比较，展示具体场地、时间和设备。
- UI-05：正式改期工作台、最终目标再次预览、差异/影响/原因及创建；详情内人员本人确认、批准范围复审、全资源范围复核、撤回、时间线及失败冲突；生效后重新查询活动/日历/通知。通知已读使用同一个未知结果重试机制，不推进人员确认。
- 统一`useMutation`：网络未知结果保留原command并锁定其他提交；服务端确认写入后清除pending，后续查询失败不重做写入；409重新GET，输入保留。新增组件仍消费公共DTO与现有40项客户端，不修改后端或契约。

DEV工作台新增资源管理与改期外观，复用正式组件，页面始终标注演示数据，写操作禁用，无API与会话。预览中的“已生效”只是固定状态样例，不能作为改期成功证据。资源、候选、影响、确认结果及手机详情/通知截图另存新目录，不覆盖此前证据。

下一接收人为QA、AN/DS/CM；接收状态：**尚未接收**。真实服务仍为外部阻塞，不以单元测试或预览代替联调。

## 前轮前端验证（2026-10-31，历史结果）

环境：Windows、Node22.23.0、npm11.16.0、Edge152.0.4191.66、Playwright1.56.1。宿主npm入口会选择Node24，实际使用Node22直接运行`C:/Program Files/nodejs/node_modules/npm/bin/npm-cli.js`；下表采用等价npm命令简写。干净安装时短暂停止本地Vite，完成后已恢复5173。

| 实际检查 | 结果 |
|---|---|
| `npm ci --no-audit --no-fund` | 成功安装182包，锁文件未变 |
| `npm run types:check` | 通过，生成DTO与common/02原文一致 |
| `npm run typecheck` | 通过；最后一次build再次执行vue-tsc通过 |
| `npm run test:unit -- --maxWorkers=2 --reporter=default --reporter=json --outputFile=../artifacts/test-results/ui-completion-2026-10-31/unit-results.json` | **17文件75项通过**，无跳过 |
| `npm run build` | 通过，11个产品路由编译；生产产物检查无原型入口/fixture标记 |
| `PLAYWRIGHT_CHANNEL=msedge UI_REUSE_SERVER=true UI_EVIDENCE_DIR=../artifacts/screenshots/ui-completion-2026-10-31 playwright test --reporter=list,json` | **13项通过**；3视口、预览无API/无会话、按钮禁用与错误反馈 |
| `git diff --check` | 通过；改动范围为web、docs/ui和UI证据 |

报告：`artifacts/test-results/ui-completion-2026-10-31/{unit-results.json,layout-results.json,build.log}`。截图36张在`artifacts/screenshots/ui-completion-2026-10-31/`，含登录/原型、工作台/授权、资源管理/日历、方案影响、冲突/生效样例、详情/通知。manifest记录源码、报告及截图SHA-256。所有业务数据截图均为**开发预览样例**；其中“已生效”截图不能登记为真实改期成功。

新增测试实际覆盖：草稿候选只填表不预约、409重载期间禁止再次保存且保留输入、目标变化及迟到响应使旧预览失效、候选最终重预览、同命令重试/已确认写入后查询失败不重写、本人确认/自审回避/目标范围、待办最多6并发/取消旧查询、端点相接/叠加故障/最低可用0及资格四组合。

验证中修复了草稿冲突未等待父页刷新、模板多语句格式化导致构建解析失败、日历边界刻度重叠。浏览器测试改为点击可见复选标签，未通过force绕过交互检查。测试中的Vue响应对象快照改用JSON复制、UUID替身使用合法格式；未屏蔽错误或跳过测试。当前仍有Element Plus全量导入导致的约1.05MB主块告警，未提高阈值掩盖，后续可按需导入优化。

未执行且不宣称完成：真实认证/业务联调、QA E2E、D-AT/N-AT、数据库刷新/重启持久化、Docker完整发布构建和真实业务截图验收。本机8080仍无监听；CM生产ChangePort/EventPort及change/notification/audit服务是明确依赖。PR仅准备了`docs/ui/pr-draft.md`本地草案，须取得用户意见后再整理提交与推送。

## 前次本地追加：UI-02账号管理（历史记录，未提交、未推送）

2026-10-31继续按本人任务书推进。fetch成功，origin/main仍为feb0d70；保留UI-01后的所有本地预览修改，未reset、未覆盖Word。用户要求暂停PR仍有效。本次仅更新web、docs/ui及UI截图/测试证据。

已补齐 `/admin/users`：账号列表、筛选、创建、启停二次确认、API-39完整授权编辑；支持独立身份、三角色、四项管理权限、组织/类别批准范围和明确资源范围。API-04/05/06/07/38/39继续走同一客户端。Actor不含username，列表不猜字段。移除角色清除专属授权，截断列表不丢弃未返回的既有授权，编辑本人权限成功后刷新me并在失权时离开管理页。

代码：`web/src/pages/UsersPage.vue`、`components/UsersPanel.vue`、`components/AuthorizationEditor.vue`、`composables/useUserAdmin.ts`、`domain/authorization.ts`。开发工作台复用UsersPanel，只展示演示数据；保存/启停不可用，不发送API，不建立会话。正式构建剔除开发数据与入口。

验证：Node22.23.0；typecheck通过；unit **8文件46项通过**（新增19项）；build通过，生产原型剔除通过；浏览器 **6项通过**（账号管理及工作台各3视口），Edge152.0.4191.66，视口1366×768、1920×1080、390×844。实际命令为 `npm run typecheck`、`vitest run --reporter=default --reporter=json --outputFile=…`、`npm run build`；浏览器以 `PLAYWRIGHT_CHANNEL=msedge UI_REUSE_SERVER=true` 运行 `playwright test tests/browser/users-preview.spec.ts tests/browser/workspace-preview.spec.ts`。JSON报告归档至 `artifacts/test-results/ui-accounts-2026-10-31/unit-results.json`；新增截图及源码哈希在 `artifacts/screenshots/ui-accounts-2026-10-31/`。这是前端组件/请求测试与开发预览检查，不是业务E2E。

曾发现组件单测把Teleport替换为stub时，Element Plus Select在脱离真实DOM的结构中递归更新。测试改为挂载document.body、保留真实Teleport并查询实际dialog后5项全过，未屏蔽错误、未跳过断言、未改生产逻辑迎合测试。构建仍有全量组件库约1.05MB的分块警告，保留为后续优化项。

当时剩余的草稿编辑/名单/待办、资源管理/同时用量、改期与审计页面已在本轮补齐。仍待真实账号与业务服务联调、QA真实E2E与持久化验证；PR需先征得用户同意。下一接收人PM/QA；接收状态仍为“尚未接收”。

## 前次本地追加：登录后外观预览（未提交、未推送）

用户要求先查看登录后的界面，且已要求暂不提交PR。新增仅DEV路由 `/__prototype/workspace`，复用正式 `WorkspaceLayout` 和 `ActivityList`；可切换三角色/身份及管理员细分权限的导航外观，查看示例详情、资源日历与通知。页首持续标注“演示数据 / 未连接后端”；不发送API，不读写登录会话，不执行业务提交。

本轮 typecheck、27项unit、build及生产原型剔除检查通过；新增3项浏览器检查通过，覆盖1366×768、1920×1080、390×844，验证无页面溢出、无脚本异常、预览不发API/不创建令牌、退出预览后真实页面仍需登录。截图在 `artifacts/screenshots/ui-workspace-preview-2026-10-31/`，仅为外观证据。沿用本机Edge 152.0.4191.66、Node22.23.0，复用现有开发服务器时指定 `UI_REUSE_SERVER=true`。

Git交付现状补记：此前本地UI-01提交为 `e100e0b`；向原仓库推送被GitHub以账号无写权限拒绝。已建立个人fork `ychangz1/campus-sched`，但用户随后要求暂停，未推送该fork、未创建PR、未合并。本次预览改动保留在本地供查看。

## 基准与保护

- 实际 main 基准：`feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`。
- 原远端UI分支：`c637653`，落后main 53提交、无独有提交。本地工作区最初干净，无用户未提交/Word改动；从该分支 `merge --ff-only origin/main` 安全快进，未reset、未强推。
- 本轮初次fetch网络失败，随后重试成功；main仍为feb0d70，UI远端仍为c637653。
- 已读根AGENTS/taskbook、agents入口、common五份及六份个人任务书；仅执行本人UI工作包。修改范围为web、docs/ui及本人截图；后端/公共契约/QA资产/课程原件/Word/Dockerfile未改。
- 交付版本：web/package.json `0.1.0`，实际commit由本交接单所在Git提交确定；不自动合并main，不自审自批。

## 前轮交付范围（历史记录，现状见顶部）

| 任务 | 已实际交付 | 状态与未完成项 |
|---|---|---|
| UI-01 | Vue3/TS/Vite/Router/Pinia/Element Plus/Vitest、package-lock、11产品路由、公共布局/权限导航、40项API客户端、统一DTO、四核心交互开发原型 | 工程与组件原型已交；三视口登录/原型检查完成，真实产品页面视觉验收待联调 |
| UI-02 | 登录/me/退出、sessionStorage、401/403、三角色与细分权限导航、路由守卫；账号创建/启停/完整授权编辑 | 认证及账号创建/启停/API-39授权编辑已写；真实认证与账号变更联调未执行 |
| UI-03 | 创建/编辑/详情/办理/报名、工作人员/名单/历史、草稿冲突候选、6并发改期待办及testid | 前端实现已补齐；真实预约/报名、并发冲突和刷新持久化未联调 |
| UI-04 | 资源建档/资格/故障管理、半小时日历、设备同时用量及故障扣减、候选比较 | 前端实现已补齐；真实故障/资格/预约数据与风险联动待联调 |
| UI-05 | 改期工作台/最终重预览/影响/创建/人员确认/复审/复核/撤回/历史；通知及同key重试；生效刷新 | 前端实现已补齐；CM生产服务未到位，真实改期/通知闭环、E2E和真实业务截图未完成 |

`/resources/manage`、`/activities/:id/change`、`/changes/:id` 已替换为正式实现，11个产品路由不再用PendingPage占位；PendingPage仅保留连接失败、无权限、地址不存在等状态页。

原型只在开发 `/__prototype` 加载，页面显著标“演示数据 / 未连接后端”，不发API、不创建会话。正式build条件剔除，产物扫描再次验证。没有生产fixture/mock。

## 文件/API交接

- 工程及运行：`web/package.json`、`package-lock.json`、`vite.config.ts`、`web/README.md`。
- 共享消费：`web/src/types/api.ts` 从common/02生成；`src/api/client.ts`统一信封、Bearer、超时、错误与幂等；`src/api/index.ts`提供40项类型化调用。
- UI代码：`src/router.ts`、`src/stores/session.ts`、`src/domain/`、`src/pages/`、`src/components/`、`src/dev/`。
- 接口逐项状态及testid：`docs/ui/api-matrix.md`。真实联调全部为未执行，不以Controller存在或单测通过宣称联调成功。
- 手册：`docs/ui/user-manual.md`；原型说明：`docs/ui/prototype/README.md`；实际界面补充：`docs/ui/interface-implementation-0.1.md`。原有界面说明v1.1及Word保留，本版实现边界以补充文档为准。

## 初次UI-01验证（历史记录）

Windows；Node 22.23.0、npm 11.16.0；Edge 152.0.4191.66（Chromium内核）；Playwright 1.56.1。初次宿主Node24安装出现engine告警，随后使用Node22直接调用npm CLI完成干净安装与检查，未将Node24结果当作Node22证据。

| 命令（web目录） | 实际结果 |
|---|---|
| npm ci --no-audit --no-fund | 通过，182个包，锁文件可安装 |
| npm run types:check | 通过，DTO与公共契约一致 |
| npm run typecheck | 通过 |
| npm run test:unit | 5文件、27用例通过；权限/自审回避/范围/报名冻结容量/幂等重试/401/403/409/超时/表单/组件/40API契约 |
| npm run build | 通过；生产原型剔除检查通过 |
| PLAYWRIGHT_CHANNEL=msedge npm run test:layout | 4用例通过：登录/开发原型3视口，以及无会话守卫和缺后端登录错误反馈；不是业务E2E |
| git diff --check | 通过 |

首次契约测试遇到Vitest DOM环境的文件URL解析问题，修复为文件系统路径后通过。视觉检查发现候选卡文字继承组件零字号/行高，已修复并补浏览器断言。构建仍有Element Plus全量导入导致的单块约1.05MB警告，后续按需导入优化；未隐藏警告。

截图：`artifacts/screenshots/ui-2026-10-31/`，登录及开发原型各3张（1366×768、1920×1080、390×844）。数据为明确标注的2030-10-20组件fixture；登录页无账号数据。仅用于布局证据，不计作真实日历/改期成功。活动详情/通知的真实移动截图尚未产生。

未执行：真实认证/业务联调、QA E2E、D-AT/N-AT验收、数据库刷新和服务重启一致性、Docker完整构建及干净环境发布。

## 前轮依赖记录（现状及下一步见顶部真实联调记录）

| 提供方 | 具体依赖 | UI影响 / 下一步 |
|---|---|---|
| CM 杨瀚霖 | `EventPort`及独立`ChangePort`生产实现；change/notification/audit生产包；可启动环境 | 当前main无这些生产包；AN状态变化/已预约视图及完整改期/通知链路不能宣称可运行 |
| QA 汤雨润 | 新版隔离测试数据、解除全量测试数据阻塞、真实服务E2E | 接收本次testid和界面路径；不得把原型或单元Mock用作联调 |
| PM 冀项阳 | 认证/授权真实请求与失败示例、统一环境和OpenAPI状态协调 | 使用既有Actor，不自行扩展字段；补账号管理后做身份/范围联调 |
| AN 冯玉琪 | API-12～23真实环境接收，接入CM端口 | 对照已实现页面验证首次预约与报名/冻结/容量/旧版 |
| DS 黄方勇 | 日历/资格/候选真实示例，故障依赖CM事件 | 验证同时用量/故障扣减、资格变化及真实候选方案 |
| UI 杨畅 | 本轮前端实现与测试已补齐 | 服务稳定后接真实账号与数据，修复联调问题，配合QA完成E2E/持久化及真实截图；PR先征询用户 |

本机当前`127.0.0.1:8080`连接拒绝，浏览器已验证登录失败反馈。未启动其他成员聊天，未发送跨成员消息；并行推进建议通过本交接清单表达，不代做QA/CM工作。

下一接收人：QA核对testid及测试边界；PM/CM接收构建和锁文件；AN/DS/CM核对接口矩阵。**接收确认：尚未接收。** 需要交叉评审，PR未经放行不得合并。
