# UI 评审修复与最新 main 复验 · 2026-11-01

成员：杨畅（17231018），界面设计师；任务 UI-02/03/05，补充 UI-01 构建验证。依据根 AGENTS.md、taskbook.md、common 五份及本人 agent.md，响应 [PR #17 预审](https://github.com/Rito-492/campus-sched/pull/17#pullrequestreview-5726456316)。

## 基线与交付状态

- 起点：`a327b16`，本地工作区干净，fork 分支与本地一致。
- 已 fetch 并合并 `origin/main=107a301ba1ea040a0c322656789f078ff81093f2`，本地合并提交 `9fe38e9`；无冲突、无 reset、无强推。同步取得的后端和共享契约来自上游，本轮未编辑这些文件。
- 本轮 UI 修复保留在 `work/17231018-ui` 本地工作区；源文件 SHA-256 见 `artifacts/test-results/ui-review-2026-11-01/manifest.json`。用户明确要求更新 PR 前确认，**未推送、未更新 PR #17、未转 Ready、未合并**。
- 原 feb0d70/c4f3d41 日志、截图和 Word/课程原件保留。PR #18 已合并，旧 CM 503/404 不能再作为最新基线结论。

## 实际修复

| 评审项 / 规则 | 实现 | 验证 |
|---|---|---|
| P2：API-14 拒绝后保留旧详情 | `ActivityDetailPage.vue` 在 401/403/404 清空活动、资源、编辑状态和意见，关联人员记录随实体卸载。网络故障保留明确标记的旧快照，禁用办理且隐藏关联记录 | 单测覆盖首次成功→401/403/404→旧内容/记录消失→重试恢复；真实跨组织批准人撤销范围后刷新 403，标题、说明和人员记录均消失 |
| P2：API-04 越权查询候选人 | `ActivityRecords.vue` 仅允许启用、具有 APPLICANT 且与活动同组织的账号补充姓名；其余按 API-14 已授权 ID 展示。姓名查询失败/截断/停用人员不遮住 ID | 单测覆盖纯批准人、纯资源复核管理员、跨组织兼任、停用、缺项、失败及迟到响应；前三种真实账号均无候选接口请求且能查看人员 |
| 建议：409 结构化冲突 | 等待实体刷新后恢复本次 ConflictList，失权时不恢复；成功后清除 | 延迟 GET 的组件测试核对冲突资源、原因及需要/可用数量 |
| 最新 main 密码规则接收 | 新建密码按 Unicode 码点计数最少 8 字符，继续检查 UTF-8 最多 72 字节 | 7/8 个补充平面字符边界；账号创建真实复验 |
| 真实页面核对发现的文案 | 历史改期影响卡不再一律宣称目标“尚未生效”，引导查看确认结果及服务端当前安排 | 重启后的真实生效页面截图核对 |

生产仍只调用统一 API 客户端；未增加接口、DTO、认证或权限捷径。新增稳定标识：`activity-refresh`、`activity-stale`、`activity-staff-{id}`、`staff-name-unavailable`。

## 环境、命令与真实结果

Windows；Node **22.23.0**，由该 Node 直接运行 `C:/Program Files/nodejs/node_modules/npm/bin/npm-cli.js`；Java **21.0.9**，PostgreSQL **17.6**（本地隔离 `campus_test:55432`），Edge **152.0.4191.66**。后端为最新 main 原样代码，profile=`dev,e2e`、固定业务时钟 `2030-10-19T00:00:00Z`、关闭重新播种；前端 5173 代理后端 8080。密码只从忽略目录的本地配置注入进程环境，不写入报告。

以下 npm 命令为上述 Node 22 调用方式的简写，在 `web/` 运行。报告均在 `artifacts/test-results/ui-review-2026-11-01/`。

| 实际命令 | 结果 / 原始报告 |
|---|---|
| `npm run types:check` | 通过，`types-check.log` |
| `npm run typecheck` | 通过，`typecheck.log` |
| `npm run test:unit -- --maxWorkers=2 --reporter=default --reporter=json --outputFile=../artifacts/test-results/ui-review-2026-11-01/unit-results.json` | **19 文件、90 项通过**，`unit.log` / `unit-results.json`；比原 78 项增加 12 项回归 |
| `npm run build` | 通过，含 vue-tsc 和生产 fixture 剔除；最终 `build-final.log`。主块 1,050.69 kB 告警仍在，不隐藏、不作为本轮阻塞 |
| `npm run test:review -- --reporter=list,json`（等价直接调用 Playwright CLI） | 首轮两项在首次批准时遇后端空意见缺陷，`review-integration.log` / `review-integration-results.json`；明确填写意见后 **2 项通过**，`review-integration-with-comment.log` / 对应 JSON |
| `npm run test:integration -- --reporter=list,json` | **5 项通过**，`integration.log` / `integration-results.json`；认证、账号、资源、草稿和具体资格错误在新基线上复验 |
| 实际重启后 `playwright test --config playwright.integration.config.ts persistence.spec.ts review-restart.spec.ts --reporter=list,json` | **2 项通过**，`persistence.log` / `persistence-results.json`；实际旧/新进程及关闭播种记录在 `restart.json` |
| `server/mvnw.cmd -f server/pom.xml -DskipTests package` | 首次被运行中 JAR 占用阻断；核实并停止本任务旧后端后重跑成功，`backend-package*.log`。**仅为启动联调，不是 JDK21 verify 通过** |

依赖和锁文件未改，本轮未重跑 npm ci；可安装证据仍明确属于前轮。前轮 13 项开发布局检查也不冒记为本轮执行。这里的真实浏览器检查由 UI 编写，使用真实服务及 API 建立独立资源、活动和最小权限账号，不调用 route.fulfill、不使用前端 fixture、不直接改业务表。测试数据仅留在本地隔离库用于截图和重启核对，不清空演示数据。

## 已验证的真实流程

1. 纯 APPROVER、仅 ADMIN/RESOURCE_CONFIRM 且只管理新建场地的管理员、所属组织不同但具备活动组织审批范围的 APPLICANT+APPROVER：正常查看工作人员 ID，不请求 `/users?purpose=staff`。撤销跨组织账号的审批范围后，旧页面刷新真实 403 并清除实体和记录。
2. API 建立草稿和工作人员关系后，通过正式页面执行提交→填写意见批准→联合预约→发布→学生报名→退出→再次报名。
3. 所有者通过正式改期工作台调整时间→真实预览→填写原因创建；待处理时报名退出按钮冻结→工作人员本人确认→纯批准人复审→最小资源范围管理员确认→APPLIED。重新读取当前安排、日历及通知，报名仍为 1 人并更新至生效版本。
4. 浏览器刷新及**实际后端重启**后，改期状态/有效版本/目标安排/报名版本/日历/通知仍可读取；旧草稿、账号、资源持久化检查也通过。

截图在 `artifacts/screenshots/ui-review-2026-11-01/`，每组 1366×768、1920×1080、390×844。根目录是首轮停止于空审批意见前的权限截图；`with-comment/` 是第二轮角色/失权/生效截图；`existing-flows/` 是 5 项原有流程的新基线截图；`restarted/` 是最终文案和真实重启后的生效页。已检查页面无横向溢出，人工核对了桌面人员页、移动失权页和移动生效页；这不是 QA 交叉视觉验收签字。

## 当前后端缺陷（供 AN/CM 接收，尚未接收）

**API-18 合法空批准意见返回 400。** 新建含工作人员的活动并提交，在批准人页面保持意见为空，点击“批准”。请求含当前 `expectedLockVersion`、`decision:"APPROVE"`、`comment:""`；收到 `400 VALIDATION_ERROR / 事件字段不完整`，未批准成功。首轮追踪编号：`6967727b-b541-4e80-945a-09edb1788000`、`36ea7c23-c074-4c4b-897e-c5d059ecdaa4`，具体活动 URL 见原始日志。

契约只要求**拒绝**意见非空。源码定位：AN `ActivityService.java` 将 `command.comment()` 直接作为事件 summary/body；CM `EventPortAdapter.java` 拒绝空 summary/body。建议 AN/CM 对齐事件默认摘要和校验，而不是 UI 擅自伪造意见或把批准意见改为必填。本轮未修改后端；第二轮填写真实意见仅用于验证有效输入的后续链路，**没有将这个缺陷登记为已修复**。

## 后续验收边界

- 已解决 PR 预审的两项 UI 必须改及结构化 409 建议；最终放行仍需交叉复审。
- 最新服务的改期冲突保留原安排、拒绝/撤回解除冻结、故障 AT_RISK、真实网络结果未知后同 key/body 重试、全部自审/范围组合、候选比较和通知已读仍待更完整真实验收；已有组件测试不能代替这些证据。
- 本轮未运行全量 QA E2E、完整 D-AT/N-AT、Compose 干净环境发布及最新 main 的 JDK21 verify。D-AT-23 已增加核心成功主线及重启证据，不登记整项签收；D-AT-24 未验收。旧 QA 数据阻塞报告保留历史基线，不直接当作当前失败。
- 接收人：QA 汤雨润复验 testid/角色/主线；AN 冯玉琪与 CM 杨瀚霖接收空审批意见失败；PM 冀项阳接收契约规则落地及构建结果。均**尚未接收**，未自动联系成员。
- 下一步：用户确认后提交 UI 修复并推送现有 PR #17；维持 Draft，附本报告等待交叉评审，不自动合并。
