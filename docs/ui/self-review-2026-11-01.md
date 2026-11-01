# UI 本地严格自查与测试记录 · 2026-11-01

成员：杨畅（17231018），界面设计师。此记录是本人修改的本地预检，**不替代交叉评审，也不是 GitHub Approve**。本轮没有 commit、push、更新 PR 或合并；PR #17 保持原远端版本及 Draft 状态，提交前仍须用户确认。

## 判断

原评审两项必须改（活动失权后残留旧实体、纯审核角色误查工作人员候选）已有修复、针对性单测和真实服务浏览器证据。本轮进一步用故意打乱响应顺序、撤销权限、网络断开和真实业务失败分支寻找反例，新增 8 个先失败后通过的回归用例。**对关闭原 UI 评审意见有较高把握；尚不能承诺整体验收或最终评审一定通过。**

不能从测试通过数量推算“评审通过概率”：这些用例并非随机抽样，评审还包含未覆盖场景、独立人员判断和项目放行门槛。当前前端检查通过，但 Java 21 全量 verify 确实失败，合法空批准意见的后端缺陷仍在，Compose/完整 QA 验收也未完成。不提供没有统计依据的百分比。

## 基准与范围

- 分支：`work/17231018-ui`；最新 `origin/main=107a301ba1ea040a0c322656789f078ff81093f2`，本轮 fetch 后未变化。
- 本地合并 HEAD：`9fe38e9c2d9ca9f2ae402acdee74e471ad4c0a48`；PR 原远端头：`a327b16b171ab40da1ef336b8f1c0ab8d21bd834`。本轮源码为其上的未提交修复，最终文件哈希见新证据 manifest，不能只凭 HEAD 复现前端修改。
- 按 AGENTS、taskbook、common 契约、本人 agent 和 `docs/testing/pr-review-checklist.md` 核对；只编辑 `web/`、`docs/ui/`、本人 `artifacts/`。后端、共享契约、QA、课程原件和用户 Word 未修改。
- Node 22.23.0 / npm 11.16.0；Edge 152.0.4191.66；Java 21.0.9；真实 PostgreSQL 隔离库。浏览器使用正式业务页面和真实服务，开发布局证据单独存放。

## 自查发现并修复

| 问题 | 修复与验证 |
|---|---|
| 活动详情并行查询中，资源网络错误先返回，可能掩盖稍后活动 403 | 先确认实体读取资格，再查资源；故意让资源先失败的回归用例由失败转为通过 |
| 409 后刷新与人工刷新交叠，旧成功响应可能在新 403 后恢复受保护实体 | 活动详情增加请求代次和卸载失效；旧响应不能写回状态 |
| 改期工作台刷新 401/403/404 后仍保留活动、目标、原因和错误快照 | 清除实体、表单及相关错误；3 个状态码用例分别验证；网络旧快照显著标注并禁用预览/创建 |
| 改期工作台迟到请求恢复已撤权数据 | 独立加载代次和作用域销毁保护；验证迟到响应不恢复目标 |
| 改期详情 409 后重读 403，原 mutation.details 仍可能展示敏感旧快照 | 失权时清除关联数据及 mutation 错误，反馈以有权读取实体为前提 |
| 改期详情网络失败仍展示数据但无过期说明 | 加入旧快照说明并保持办理禁用；加载链与关联日历/通知也有代次保护 |

对应代码：`ActivityDetailPage.vue`、`useChangeWorkspace.ts`、`ChangeWorkspacePage.vue`、`ChangeDetailPage.vue`。诊断证据为 `adversarial-before/after`（6 个新增用例）和 `change-before/after`（2 个新增用例）；完整单测含原有测试共 98 项。组件级错误注入只在测试内，不冒充真实授权接口验证。

原评审修复仍保留：同组织且有 APPLICANT 角色才查候选姓名；纯 APPROVER、仅 RESOURCE_CONFIRM、跨组织账号显示已授权 ID；活动 401/403/404 清空、网络失败隐藏关联记录；409 保留结构化冲突。密码规则和历史改期说明保持与最新契约一致。

## 实际检查结果

证据根目录：`artifacts/test-results/ui-self-review-2026-11-01/`。以下数量分别统计，不把复跑或组件布局叠加为新增业务覆盖。

| 命令 / 检查 | 结果 | 原始证据 |
|---|---|---|
| `npm ci --no-audit --no-fund` | 通过，安装 182 包，锁文件未改 | `npm-ci.log` |
| `npm run types:check` | 通过，公共 DTO 派生一致 | `types-check.log` |
| `npm run typecheck` | 通过；最后增加日历断言后再跑也通过 | `typecheck.log`、`typecheck-final.log` |
| Vitest 全量单测 | 20 文件、98 项通过 | `unit-results.json`、`unit.log` |
| `npm run build` | 通过；生产产物的开发原型/fixture 排除检查通过 | `build.log` |
| Playwright 布局检查 | 13 项通过，覆盖 1366×768、1920×1080、390×844；属于开发布局检查 | `layout-results.json`、`layout.log` |
| 真实服务 `real-service.spec.ts review-regression.spec.ts` | 10 项通过，无合成 API 成功响应 | `real-results.json`、`real.log` |
| 加强原预约日历断言后复跑两个失败分支 | 2 项通过，是前述场景复跑，不重复计数 | `reservation-results.json`、`reservation.log` |
| 实际后端重启后的两项持久化检查 | 2 项通过，禁用播种；PID 64172 → 65696 | `restart.json`、`restart-results.json`、`restart.log` |
| JDK 21 `mvnw.cmd -f server/pom.xml verify` | **失败，退出码 1**；Surefire 77 项全过；Failsafe 85 项中 9 errors、1 skipped、0 failures | `jdk21-verify.log`、`jdk21-suites.json`、`jdk21-failures.json` |
| Compose / 全量 QA E2E / 完整 D-AT-23/24 | **未执行 / 未签收**；本机无 Docker 命令 | 不登记为通过 |

前端命令均在 `web/` 使用 Node 22 执行。Vitest 使用 `run --reporter=default --reporter=json --outputFile=../artifacts/test-results/ui-self-review-2026-11-01/unit-results.json`；浏览器使用 `--config playwright.integration.config.ts --reporter=list,json`。`UI_DEMO_PASSWORD` 通过环境变量注入，`PLAYWRIGHT_CHANNEL=msedge`；`UI_EVIDENCE_DIR` 和 `PLAYWRIGHT_JSON_OUTPUT_NAME` 指向本轮目录。布局使用 `playwright.config.ts` 与 `UI_REUSE_SERVER=true`。密码、运行脚本和测试状态只留在被忽略的 `.integration-runtime/`。

Java 全量验证在由本地 HEAD 导出的隔离副本中执行，避免覆盖正在联调的 JAR。使用真实 `TEST_DATABASE_URL`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`、`DEMO_PASSWORD` 和 Java 21；Windows 设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<隔离副本绝对路径>`。没有跳过测试参数或改写后端。原日志保留 Windows 子进程部分中文乱码，结构化 suite/error 摘要依据原 JUnit XML 提取；未归档包含环境属性的完整 XML。

## 真实浏览器覆盖及边界

10 项由原有 5 项真实接口/页面检查和 5 项评审回归组成。新回归验证：

1. 纯批准人、最小资源复核权限和跨组织人员展示；撤销授权后详情 403 不保留旧实体。
2. 实际填写审批意见的活动提交、批准、首次预约、发布、报名/退出；改期本人确认、复审、资源确认、生效后的版本、计划、日历和通知。
3. 通知标为已读不会代替工作人员确认；工作人员拒绝、所有者撤回后报名解冻，原活动版本/安排与真实日历占用保持。
4. 另一真实预约抢占目标时段后，最终确认返回 409、改期 CONFLICTED，旧计划/原预约保持；真实故障登记使活动显示 AT_RISK。
5. 首次报名真实请求已落库后丢弃一次响应，界面进入未知结果；同 key、同 body 重试后报名数仍为 1。故障注入为测试中的 `route.fetch()` 后 `abort()`，重试直接 `continue()`；没有 `route.fulfill()` 或假成功，不能称为无故障的普通网络流程。

重启两项检查再次登录真实服务，确认草稿、资源资格、账号授权/停用，以及已生效改期版本、安排、报名、日历、通知仍存在。数据由真实 API 创建，使用专用隔离数据库及唯一命名资源/账号；有意保留用于重启复核，没有清空用户业务数据。后续复跑应继续用隔离库，不能指向生产。

截图位于 `artifacts/screenshots/ui-self-review-2026-11-01/`：`layout/` 只表示开发布局，`real/`、`reservations/`、`restarted/` 来自真实页面。人工抽查了手机风险页、桌面撤回页和重启后的改期生效页；自动布局检查不能替代团队视觉签收。未覆盖全部权限组合、全部并发交错、完整 D-AT/N-AT，也未运行 QA 全套浏览器工程。

## 尚未关闭的依赖问题

| 级别 / 责任方 | 位置与复现 | 影响 / 接收状态 |
|---|---|---|
| 必须解决后才能宣称全量 verify 通过；QA | `server/src/test/java/com/campus/events/support/DbTestSupport.java:236` 在资源插入前写 `user_resource_scope`，8 个 acceptance IT 初始化触发外键错误 | BookingConcurrency、CancellationRace、ChangeRollback、IdentityEligibility、NotificationConsistency、Permissions、RegistrationCapacity、SchedulingBenchmark 未进入完整验收；尚未接收 |
| 必须解决后才能宣称全量 verify 通过；AN/CM | `server/src/test/java/com/campus/events/activity/ActivityRegistrationIT.java:84` 注册测试 EventPort，同时扫描到生产 `eventPortAdapter`，Spring 报两个 Bean | ActivityRegistrationIT 启动失败；应由所有者对齐生产集成配置，不通过前端或跳过测试规避；尚未接收 |
| 真实接口缺陷；AN/CM | `ActivityService.java:141` 将合法空批准意见传给事件；`notification/EventPortAdapter.java:27` 起拒绝空摘要/正文 | API-18 空意见返回 400“事件字段不完整”；前轮 `ui-review-2026-11-01/review-integration.log` 保留失败。本轮主线填写实际意见，**未重测或修复空意见缺陷**；尚未接收 |
| 环境/正式验收；CM/QA/PM | Compose 与完整 D-AT-23/24、全权限组合及发布验收 | 本机无 Docker，未执行；需约定基线和环境后补验并交叉放行；尚未接收 |

Failsafe 的 1 项 skipped 来自 `ResourceSchedulingIT` 中仅适用于 CM 端口未到位的分支，原因为“CM端口已到位，改跑真实切换测试”。是现有测试条件跳过，本轮没有增加 skip；也不把它计作通过。

建议改（非阻塞）：Element Plus 主块约 1.05 MB，构建仍提示分块告警；后续做按需加载优化，未提高阈值掩盖。安装日志中的 esbuild 脚本许可提示保留，实际 build 成功。

## 本地评审维度与下一接收人

范围归属、DTO/接口一致、fixture 隔离、真实结果留痕和凭据扫描已核对；测试针对具体错误和实际状态/数量，不以 HTTP 200 作为唯一断言。原始失败日志未删除，旧 `ui-review-2026-11-01` 的报告/manifest 保留为当时快照，本轮另建 manifest，不冒充旧证据仍对应当前源码。

UI 负责的已发现问题在本地修复并复验；下一步由 QA/独立评审人核对本轮改动及原评审关闭情况，AN/CM/QA 处理各自失败项，PM/CM 明确整体验收门槛。**均尚未接收；本报告未对外发送。** 用户确认后才可提交/推送并更新 PR #17，交叉评审放行后由授权人员合并。
