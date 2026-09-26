# 测试计划 v0.1

> 归属：测试经理（55230316）。配套文档：`test-design.md`（用例）、`test-data-plan.md`（数据）。
> 依据：`agents/common/03-验收与演示数据.md` §3（统一验证入口）、`agents/common/01-开发基线.md` §9（质量目标）。
> 状态记录：执行结果汇总于 `test-report.md`（QA-05，真实执行后编写）；本计划不含任何未执行的「通过」标记。

## 1. 测试范围

| 层级 | 内容 | 执行者 | 入口 |
|---|---|---|---|
| 模块单元/集成测试 | 各业务模块自身逻辑（活动状态机、调度约束、幂等等） | 各模块负责人 | `./server/mvnw.cmd -f server/pom.xml test`（Surefire `*Test`） |
| 跨模块数据库与 HTTP 验收 | D-AT 权限/并发/回滚/取消/容量/通知六类（acceptance/ 六个 IT） | 测试经理 | `verify`（Failsafe `*IT`） |
| 契约检查 | openapi.yaml 与实现一致性（38 接口/字段/枚举/包装/认证/Idempotency-Key） | 测试经理 | `npm --prefix qa run test:contract` |
| 浏览器 E2E | 登录、改期主线、改期冲突、权限、界面布局（5 spec） | 测试经理 | `npm --prefix qa run test:e2e` |
| 策略评测 | FIRST_FIT vs RANKED（8 场景，D-AT-22） | 测试经理 + DS | `-Dit.test=SchedulingBenchmarkIT verify` + `npm --prefix qa run evaluate` |
| 发布验收 | D-AT-24 干净环境演练 | 全员 | `release/` 发布说明 + `scripts/check.ps1 -Scope all` |

不测：候补、签到、归还验收、统计报表等首版不实现项（01 §1）；不接校园身份/短信/支付。

## 2. 测试策略

- **真实优先**：集成与并发测试用真实 PostgreSQL（campus_test），**不用 H2、不用纯 Mock**（01 §2）；仅前端单元测试允许 Mock。
- **断言标准**：不只看 HTTP 200，核对持久化状态与数量；预期失败是断言对象；回滚测试在异常前后比较数据库快照。
- **并发规范**：独立连接/事务 + CountDownLatch 起跑屏障（ConcurrentRunner），禁止单连接循环冒充并发。
- **失败注入**：只在测试上下文替换 bean/挂钩；不提供公网「制造故障/重置数据库」接口（03 §2）。
- **时序处理**：`expect` 轮询等待业务状态与通知，不用长固定 sleep 掩盖时序问题。
- **E2E**：独立 Compose 测试项目（dev,e2e profile、campus_test、端口 8089、固定 APP_TEST_NOW 与 DEMO_DATE），后端与数据库必须真实，**禁止接真实部署库**；Chromium 默认 1366×768，必要页面另测 1920×1080 与 390×844。
- **评价口径**：同成功率下比较调整代价；不预设先进策略成功率一定更高；无解与搜索截断如实记录。

## 3. 测试环境与数据

| 项 | 约定 | 来源 |
|---|---|---|
| 数据库 | `campus_test`；每次测试独立 schema `t_<UUID去连字符>`（正则 `^t_[0-9a-f]{32}$`），Flyway 迁移后载入独立数据，只清自己建的 schema | 03 §3 |
| 环境变量 | `TEST_DATABASE_URL`/用户名/密码；`DEMO_PASSWORD`（BCrypt 编码，不入库）；`DEMO_DATE=2030-10-20`；Clock=`2030-10-19T00:00:00Z` | 03 §1/§3 |
| 测试数据 | `test-data/demo.json`、`scenarios/*.json`；装载与派生规则见 `test-data-plan.md` | QA-01 |
| 运行命令 | 03 §3 统一入口（load-env / compose up db / mvnw / npm / check.ps1） | 各负责人实现 |

## 4. 进入 / 退出标准

**进入（对应 M0 完成标准，04-协作规程 §2）**：后端可编译、数据库可迁移、前端可构建、测试支撑（DbTestSupport/固定 Clock/隔离 schema）可用、依赖版本锁定。缺共享工程时按 04 §2 规则在本人目录先行、替身仅放测试代码，交接单写明缺什么、谁提供。

**退出（QA 完成标准，任务书 §2）**：
1. 24 个 D-AT 场景均有明确执行状态（通过/失败/阻塞/未执行）；
2. 关键并发/回滚/权限场景通过；
3. E2E 使用真实后端；
4. 策略评测可复现（原始结果 + 环境说明）；
5. 报告不含虚构结果——产品未完成时如实保留未执行/阻塞项。

## 5. 缺陷管理

- 渠道：**GitHub Issues**（仓库约定）；提交信息引用 `fix: ... (#N)`。
- 记录字段：标题、版本（commit/构建号）、环境、复现步骤、预期/实际、证据（日志/截图）、严重程度、接收人、状态（QA-05）。
- 流转：业务缺陷 → 对应模块负责人；接口歧义 → PM/AN 协调；质量风险与发布建议 → PM。
- 回归：修复后对原缺陷回归，必要时覆盖相邻路径；不反复跑无关全套（01 §9 记录实际结果）。

## 6. 里程碑对应

| 里程碑 | 测试工作 |
|---|---|
| M0 | QA-01：测试计划/设计/数据、DbTestSupport、固定 Clock、ConcurrentRunner、qa 工程骨架 |
| M1 | QA-02：六个跨模块 IT；各模块自测协同；契约检查跑通 |
| M2 | QA-03：五条 E2E 主线 + 布局核对；D-AT-23 持久化验证 |
| M3 | QA-04：策略评测与复现文档；QA-05 缺陷回归 |
| M4 | QA-05：测试报告、需求追溯证据移交、D-AT-24 发布演练、handoff 归档 |

## 7. 风险与应对

| 风险 | 应对 |
|---|---|
| PM 基础工程（pom/contract 包）延期 | 支撑代码按 02 §5 端口签名先行编写；编译验证挂起并在 handoff 写明阻塞方，不造假通过 |
| 契约歧义（如故障场景冲突码） | 按 02 §7 走跨模块修改规程，由 PM 裁决；用例中不硬断言未唯一指定的取值（见 test-design TC-18/TC-22 注记） |
| E2E 时序脆弱 | expect 轮询 + 业务状态等待；失败截图自动留存 `artifacts/screenshots/` |
| 并发测试假并发起飞 | ConcurrentRunner 强制独立连接 + 屏障；用例标注检查点 |
| 时间依赖真实当天 | 全部测试走固定 Clock 与 DEMO_DATE，禁止改操作系统时间 |
