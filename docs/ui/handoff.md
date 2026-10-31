# UI 交接单 · 2026-10-31

成员：杨畅（17231018），界面设计师。任务包 v3.0；本人分支 `work/17231018-ui`。

## 基准与保护

- 实际 main 基准：`feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`。
- 原远端UI分支：`c637653`，落后main 53提交、无独有提交。本地工作区最初干净，无用户未提交/Word改动；从该分支 `merge --ff-only origin/main` 安全快进，未reset、未强推。
- 本轮初次fetch网络失败，随后重试成功；main仍为feb0d70，UI远端仍为c637653。
- 已读根AGENTS/taskbook、agents入口、common五份及六份个人任务书；仅执行本人UI工作包。修改范围为web、docs/ui及本人截图；后端/公共契约/QA资产/课程原件/Word/Dockerfile未改。
- 交付版本：web/package.json `0.1.0`，实际commit由本交接单所在Git提交确定；不自动合并main，不自审自批。

## 本次交付与完成边界

| 任务 | 已实际交付 | 状态与未完成项 |
|---|---|---|
| UI-01 | Vue3/TS/Vite/Router/Pinia/Element Plus/Vitest、package-lock、11产品路由、公共布局/权限导航、40项API客户端、统一DTO、四核心交互开发原型 | 工程与组件原型已交；三视口登录/原型检查完成，真实产品页面视觉验收待联调 |
| UI-02 | 登录/me/退出、sessionStorage、401/403、三角色与细分权限导航、路由守卫 | 首批认证界面已写；账号创建/启停/API-39授权编辑尚未实现；真实认证联调未执行 |
| UI-03 | 我的/公开活动与review列表、四步创建/保存草稿、详情/资源预览、提交/批准/联合预约/发布/取消/撤回、学生报名/退出及testid | 首批页面及调用已写；草稿编辑、名单、完整历史、改期待办聚合未实现；真实预约/报名闭环未执行 |
| UI-04 | 真实日历查询、分段/数量明细、移动列表、冲突/候选对比组件原型 | 设备同时用量与故障叠加汇总、资源管理/资格编辑、候选真实接入未完成 |
| UI-05 | 差异/影响/确认时间线原型；通知列表/已读真实调用；版本化用户手册 | 改期申请/人员确认/复审/最终确认/审计页面未实现；真实通知服务未联调；最终E2E/截图待完成 |

`/resources/manage`、`/admin/users`、`/activities/:id/change`、`/changes/:id` 是明确标注“正在接入”的路由骨架，不提供假成功。

原型只在开发 `/__prototype` 加载，页面显著标“演示数据 / 未连接后端”，不发API、不创建会话。正式build条件剔除，产物扫描再次验证。没有生产fixture/mock。

## 文件/API交接

- 工程及运行：`web/package.json`、`package-lock.json`、`vite.config.ts`、`web/README.md`。
- 共享消费：`web/src/types/api.ts` 从common/02生成；`src/api/client.ts`统一信封、Bearer、超时、错误与幂等；`src/api/index.ts`提供40项类型化调用。
- UI代码：`src/router.ts`、`src/stores/session.ts`、`src/domain/`、`src/pages/`、`src/components/`、`src/dev/`。
- 接口逐项状态及testid：`docs/ui/api-matrix.md`。真实联调全部为未执行，不以Controller存在或单测通过宣称联调成功。
- 手册：`docs/ui/user-manual.md`；原型说明：`docs/ui/prototype/README.md`。原有界面说明v1.1仍作为设计依据，本版实现边界以这两份补充为准。

## 实际验证

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

## 依赖与下一步

| 提供方 | 具体依赖 | UI影响 / 下一步 |
|---|---|---|
| CM 杨瀚霖 | `EventPort`及独立`ChangePort`生产实现；change/notification/audit生产包；可启动环境 | 当前main无这些生产包；AN状态变化/已预约视图及完整改期/通知链路不能宣称可运行 |
| QA 汤雨润 | 新版隔离测试数据、解除全量测试数据阻塞、真实服务E2E | 接收本次testid和界面路径；不得把原型或单元Mock用作联调 |
| PM 冀项阳 | 认证/授权真实请求与失败示例、统一环境和OpenAPI状态协调 | 使用既有Actor，不自行扩展字段；补账号管理后做身份/范围联调 |
| AN 冯玉琪 | API-12～23真实环境接收，接入CM端口 | 补草稿编辑/名单/待办聚合，跑首次预约与报名/冻结/容量/旧版 |
| DS 黄方勇 | 日历/资格/候选真实示例，故障依赖CM事件 | 补设备峰值/故障可用量、具体资源资格反馈与0/5候选布局 |
| UI 杨畅 | 剩余UI-02～05代码与前端测试 | 后端依赖未到仍可独立补账号/资源表单及改期组件；稳定后真实联调并更新截图/手册 |

本机当前`127.0.0.1:8080`连接拒绝，浏览器已验证登录失败反馈。未启动其他成员聊天，未发送跨成员消息；并行推进建议通过本交接清单表达，不代做QA/CM工作。

下一接收人：QA核对testid及测试边界；PM/CM接收构建和锁文件；AN/DS/CM核对接口矩阵。**接收确认：尚未接收。** 需要交叉评审，PR未经放行不得合并。
