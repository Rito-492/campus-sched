# 测试报告 v0.1（执行状态快照）

> 归属：测试经理（55230316）。本报告仅汇总实际执行证据；测试代码存在、构建/合同脚本通过或临时支撑验证均不能折算为产品验收通过。

## 基本信息

- 日期：2026-10-26（Asia/Shanghai）
- QA分支：`work/55230316-qa` @ `0bb9aac`
- 最新main基准：`origin/main` @ `34dc4d5abd3d859e3a63ff476228593555f2e03b`（PR #9已合入）
- QA分支未同步最新main；本轮未修改生产模块。
- 环境：TEST_DB_*未设置；数据库/API/UI目标端口未监听；无真实后端或前端服务可做端到端业务验收。

## 本轮执行结果

| 类别 | 命令/观察 | 结果 | 证据 |
|---|---|---|---|
| API合同静态校验 | `node qa/scripts/check-contract.ts` | 通过：40 API、9组枚举、15 DTO | `artifacts/test-results/qa-2026-10-26/contract-check.log` |
| 测试数据引用 | `python3 artifacts/test-results/qa-2026-10-20/data-reference-check.py` | 通过：demo 2组织/9用户/12资源/2活动/1报名；并发20用户/1活动；调度8场景/2策略/300候选 | `artifacts/test-results/qa-2026-10-26/data-reference-check.log` |
| 测试数据引用 | `python3 artifacts/test-results/qa-2026-10-20/data-reference-check.py` | 通过：demo 2组织/9用户/12资源/2活动/1报名；并发20用户/1活动；调度8场景/2策略/300候选 | `artifacts/test-results/qa-2026-10-26/data-reference-check.log` |
| QA TypeScript | `npx tsc --noEmit`（qa目录） | 通过，exit 0 | 本轮复核；统一结果见数据引用日志 |
| Playwright用例列举 | `npx playwright test --list`（qa目录） | 29 tests / 7 files，exit 0；只列举未执行 | 本轮复核；统一结果见数据引用日志 |
| 默认Maven编译 | `./server/mvnw -f server/pom.xml -DskipTests compile` | 失败：`DemoDataSeeder.java:118`未处理受检异常，exit 1 | `artifacts/test-results/qa-2026-10-26/default-compile.log` |
| Maven默认测试 | `./server/mvnw -f server/pom.xml test` | 同一编译失败，未进入测试，exit 1 | `artifacts/test-results/qa-2026-10-26/default-test.log` |

历史2026-10-20的真实PostgreSQL DbTestSupport临时支撑探测仍是历史证据（非本轮、非Failsafe、非D-AT/N-AT验收）。

## 覆盖判定

- 验收总数 RfT=30；本轮完成场景 Tx=0；完全成功场景 Ts=0。
- 执行覆盖率 Tx/RfT=0/30=0%；成功覆盖率 Ts/RfT=0/30=0%。
- 合同/数据脚本通过和Playwright用例列举属于支撑资产验证，不是业务场景验收；默认Maven编译失败也不是D-AT-24发布演练结果。
- 30个D-AT/N-AT状态继续全部保持“未执行”。

## 主要阻塞及提供者

1. CM杨瀚霖（55231031）：修复main默认构建Seeder受检异常；与PM选定PR #8/#10唯一权威来源并消除IT硬编码密码，通过安全`DEMO_PASSWORD`环境变量运行正式Failsafe Seeder IT。当前PR #8分支较main落后，不能直接把其tree视为当前完整产品基线。
2. AN冯玉琪（22220314）：按PR #11评审处理API-13 `scope=mine`缺工作人员/本人报名历史并集、API-14详情访问缺本人报名历史授权；合入后由QA复验。
3. UI杨畅（17231018）：`web/`目前尚无`package.json`，Compose web镜像与E2E依赖前端工程交付。
4. QA/全组：TEST_DB_*、数据库/API/UI服务和Docker Compose需可用，才能运行正式Failsafe与真实服务E2E。当前测试支撑静态检查可独立执行。

## 备注

`StrategyEvaluator`、`StrategyResult`及Scheduling实现已随PR #9进入origin/main，不再列为当前main缺失依赖；但QA分支尚未同步该提交，且本轮默认构建被Seeder编译失败挡住，因此D-AT-22仍未执行。环境变量代码读取也尚未完全统一：DbTestSupport要求`TEST_DATABASE_URL`，用户名/密码用`TEST_DB_*`并回退旧别名，待CM统一模板和入口。

本报告不是最终课程验收报告；待真实后端、数据库和E2E运行后增量更新，并逐项附commit、环境、输出及状态。
