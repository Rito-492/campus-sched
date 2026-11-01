# UI真实联调记录 · 2026-10-31

杨畅（17231018）；`work/17231018-ui`；main基准`feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`；本地HEAD`e100e0b`加尚未提交的UI修改。未推送、未创建PR。以下运行证据来自真实Java服务与PostgreSQL，不使用浏览器API拦截、生产端口替身或前端fixture。

以上为首轮联调时点记录。用户随后授权提交PR，已同步最新main=c4f3d41；新基线的复验证据另存`ui-pr-2026-10-31/`，提交进展见handoff顶部。资格用例现为独立创建真实测试资源/草稿，持久化用例按草稿实际场地核对，不依赖资格用例改写前一测试的数据。

## 本轮补齐

- 登录、会话刷新、退出、教师无隐式审批、禁止访问账号管理及真实403。
- 账号创建、API-39完整授权体保存（本次实际修改身份）、停用与刷新。其他授权组合由前端单测覆盖，不能据此宣称全部权限场景真实验收通过。
- 场地创建、教师/学生独立资格修改、资源列表、故障空列表和日历查询。
- 四步创建草稿、详情、修改标题与场地、资源预览、刷新后的lockVersion一致。
- 真实管理员将场地资格关闭后，申请人预览收到403，界面直接显示具体受限场地名称，保留草稿及requestId；检查结束恢复本轮创建场地的资格。
- 仅重启本轮启动的后端、关闭重新播种后，重新登录，确认草稿及版本、资源资格、账号身份与停用状态仍在数据库中。数据库进程未重启。
- 拒绝改期增加有标题的理由弹窗，理由必填，权限/状态失效禁止确认；补齐QA扩展定位标识。CM链路未到位，该弹窗目前只有组件验证。

## 环境与可复现入口

Windows；Node22.23.0；Edge152.0.4191.66；Playwright1.56.1；JDK21.0.9；Maven Wrapper3.9.11；PostgreSQL17.6。前端5173，后端8080，独立数据库55432/campus_test，全部绑定本机。Java服务运行未改动的main后端产物；数据库由Flyway和CM原有DemoDataSeeder初始化，未手改业务表推进流程。

PostgreSQL可执行文件取自Maven Central的`io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64:17.6.0`，下载后校验SHA-256，来源与摘要见`artifacts/test-results/ui-integration-2026-10-31/postgres-runtime.json`。这是独立本地运行环境，**不是Docker Compose交付**。运行目录和生成密码只保存在已忽略的`web/.integration-runtime/`，不提交、不写入截图和报告。

后端环境：`TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/campus_test`、`TEST_DB_USERNAME=campus_app`、`TEST_DB_PASSWORD`/`DEMO_PASSWORD`通过环境变量注入；`SPRING_PROFILES_ACTIVE=dev,e2e`、`APP_TEST_NOW=2030-10-19T00:00:00Z`、`DEMO_DATE=2030-10-20`。首次启动开启种子，持久化复验使用`--app.demo-seed=false`，防止重新导入覆盖既有状态。

本机默认TEMP使用Windows短路径，Java NIO出现`Unable to establish loopback connection / Invalid argument: connect`。将`-Djdk.net.unixdomain.tmpdir`指向已有的完整绝对路径后启动成功；未改JDK全局配置或业务代码。后端从仓库根目录执行`java -Djdk.net.unixdomain.tmpdir=<本地可写完整路径> -jar server/target/campus-events-0.1.0-SNAPSHOT.jar --server.address=127.0.0.1`。

前端和真实后端启动后，在web设置`UI_DEMO_PASSWORD`为隔离库账号密码，执行：

```powershell
$env:PLAYWRIGHT_CHANNEL = 'msedge'
npm run test:integration
# 操作者停止并重启自己的后端进程，关闭重新播种后：
npm run test:persistence
# 如需重现当前基线的服务缺口，仅在上述隔离测试库执行：
node scripts/probe-integration.mjs
```

`test:integration`默认只运行real-service，缺密码/服务即失败；`test:persistence`只复查上一轮实际创建的数据，不能在没有重启时宣称重启通过。测试创建少量唯一命名的UI测试账号/资源/草稿，保留用于复查；不清空数据库。trace/video关闭，避免保存认证请求体或Bearer。

## 实际检查结果

| 检查 | 实际结果与证据 |
|---|---|
| `npm ci` | 上轮Node22干净安装182包通过；本轮未改依赖/锁文件，未重复安装 |
| `npm run types:check`、`npm run typecheck` | 通过；DTO仍与common/02一致 |
| `npm run test:unit -- --maxWorkers=2` | 18文件、78项通过；`unit-results.json` |
| `npm run build` | 通过；产物无开发入口/fixture；`build.log`；仍保留约1.05MB组件库主块告警 |
| `npm run test:layout` | 13项通过；`layout-results.json`；为登录/开发预览检查 |
| `npm run test:integration` | 5项真实局部联调通过；`real-service.json` |
| 登录权限加强复验 | 1项通过；`auth-permissions.json`，校对实际菜单名并增加API403/路由禁止访问断言，不重复计入5项 |
| `npm run test:persistence` | 后端PID61788停止、PID59196启动后1项通过；`restart.json`、`persistence.json` |
| `server/mvnw.cmd --batch-mode --no-transfer-progress verify`，JDK21 | **失败**；70单元通过，93集成中85通过/8错误/0跳过；`jdk21-verify.log`、`jdk21-verify-status.json`、`jdk21-test-summary.json` |

报告均在`artifacts/test-results/ui-integration-2026-10-31/`。15张真实页面截图在`artifacts/screenshots/ui-integration-2026-10-31/`，覆盖日历、授权、资源、草稿、资格失败各3视口（1366×768、1920×1080、390×844）。使用demo.json写入真实数据库的演示账号，加上本轮经API创建的测试数据；与前端组件fixture明确区分。布局复验证据另放`ui-integration-layout-2026-10-31/`。UI检查了桌面草稿与手机资源/资格提示截图；未代替QA交叉视觉验收。

## 已复现的外部阻塞

完整请求、状态、错误及requestId在`endpoint-probes.json`。以下不是预期业务成功，也不计作E2E通过：

| 请求 | 实际响应 | 提供者与影响 |
|---|---|---|
| GET /activities?scope=public | 503，改期端口尚未就绪 | CM ChangePort；影响公开/已预约活动视图和后续报名 |
| POST /activities/{id}/submit | 503，活动事件端口尚未就绪 | CM EventPort、AN接入；首次审批/预约主线不能继续 |
| POST /resources/{id}/faults | 503，资源故障事件服务尚未就绪 | CM EventPort、DS接入；真实故障/风险联动未完成 |
| POST /activities/{id}/change-preview | 404 NOT_FOUND | CM改期生产服务缺失 |
| GET /activities/{id}/changes、/audit；GET /notifications | 404 NOT_FOUND | CM改期/审计/通知生产服务缺失 |

提交审批失败后重新读取，草稿仍为DRAFT且lockVersion不变；没有修改数据伪造下一状态。当前尚不能完成首次预约/报名、冲突候选业务闭环、改期确认/复审/原子切换、通知已读和完整状态恢复。

JDK21 verify的8个错误来自`BookingConcurrencyIT`、`CancellationRaceIT`、`ChangeRollbackIT`、`IdentityEligibilityIT`、`NotificationConsistencyIT`、`PermissionsIT`、`RegistrationCapacityIT`、`SchedulingBenchmarkIT`。`DbTestSupport.insertReferenceTables`在resource尚未插入时写入`user_resource_scope`，触发`fk_user_resource_scope_resource`；资源ID为`00000000-0000-0000-0000-000000003101`。这是QA测试数据装载阻塞；未越权修复QA代码或跳过失败测试。

**D-AT-23：仅草稿/账号/资源的刷新与后端重启子集已有证据，完整UI主线仍未通过。D-AT-24：未执行，Docker/Compose与干净环境发布由CM牵头。Java21已可用且已实际运行verify，但verify未通过。**

## QA接收路径

已补`login-error`、`change-entry`、`change-reject`、`reject-reason`、`reject-confirm`、`calendar-nav`、`registration-list`、`risk-banner`、`eligibility-error`、`alert-generic`；12个公共testid继续保留。改期不同步骤互斥，`change-reject`只对应当前可处理步骤。ApiFeedback还保留`api-error`及上下文错误标识。

QA现有脚本需要按真实页面调整，不能改变产品流程来制造通过：

- `qa/tests/helpers.ts`的fillNewActivityForm需逐步点“下一步”；报名容量标签为“报名容量”；Element Plus场地/设备选择使用点击及`getByRole('option')`，不能用原生selectOption。
- createActivityViaUi先点`activity-save`获取ID，在详情才点`activity-submit`；草稿保存不会自动提交审批。
- 拒绝改期使用`change-reject`→dialog中`reject-reason`→`reject-confirm`；发起改期入口为`change-entry`。
- 改期选择候选后要等待最终预览，再填原因并创建；原因是契约必填项。
- `qa/tests/booking.spec.ts:163`用studentApi取API-23完整报名名单不符合权限，建议用ownerApi查名单，学生仅断言myRegistrationStatus。

后端缺口解除后，由QA接收本轮真实操作脚本和testid运行完整E2E，UI继续修正联调发现。下一接收人：QA、CM及AN/DS/PM；**尚未接收**。不自动联系成员，不提交PR或合并。
