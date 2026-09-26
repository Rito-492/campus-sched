# 测试岗位交接单（handoff）

> 维护：测试经理（55230316）。格式依据 03 契约 §5：每次记录含 commit/文件清单、基线、执行命令、真实通过/失败/阻塞、日志路径、已知问题、接收人及下一动作。**不记录未实际执行的检查结果**。

## 2026-09-26 QA-01 第一批：测试数据层 + 测试设计/计划 + 支撑代码 + qa 工程骨架

**commit**：`1cb2e86`（数据层）、本条对应后续提交（文档/Java/qa 工程）

**基线**：`baseline/task-package-v2.0`（9a80fa3 之后 main 同步至 9804722）

**文件清单**：
- `docs/testing/test-data-plan.md`、`test-design.md`、`test-plan.md`
- `test-data/demo.json`、`test-data/scenarios/registration-concurrency.json`、`test-data/scenarios/scheduling.json`
- `server/src/test/java/com/campus/events/support/{DbTestSupport,TestClockConfiguration,ConcurrentRunner}.java`
- `qa/package.json`、`qa/package-lock.json`、`qa/tsconfig.json`、`qa/playwright.config.ts`

**实际执行状态**：
- ✅ JSON 三个文件通过机器校验（合法性、ID 唯一、引用完整、字段与 02 契约一致、代价列名与 03 §4 一致）
- ✅ `npm install`（qa 工程依赖锁定，lockfile v3 27 包）
- ✅ `npx tsc --noEmit`（qa 配置可编译）
- ✅ `npx playwright test --list` 真实退出码 1（0 测试必失败语义正确；注意用管道捕获会吞退出码）
- ⬜ **未执行**：Java 支撑三件套的编译与运行（阻塞，见下）
- ⬜ **未执行**：全部 24 项 D-AT 场景（依赖产品实现，用例执行状态见 test-design.md §0，初始均"未执行"）

**阻塞（缺什么、谁提供）**：
1. `server/pom.xml` 与 `com.campus.events.contract` 包 —— **jxy（55230223，PM 的 M0 基础工程）**。DbTestSupport/ConcurrentRunner 依赖 JdbcTemplate/Flyway/Jackson/BCrypt（01 基线 §2 技术栈），contract 包到位后即可编译。**在共享工程缺失期间未自建第二套工程**（04-协作规程 §2）。
2. `server/src/main/resources/db/migration/V1__schema.sql` —— **hfy（55230315，DS）**。DbTestSupport 的 Flyway 迁移依赖它。
3. `scripts/load-env.ps1` / `check.ps1`、E2E Compose 环境（dev,e2e profile、campus_test、端口 8089、固定 APP_TEST_NOW/DEMO_DATE）—— **yhl（55231031，CM）**。QA-01 要求与 CM 约定该环境，**约定内容已写入 test-plan.md §3**，请 CM 按此实现并在自己的 handoff 确认。

**接收人及下一动作**：
| 接收人 | 交付物 | 下一动作 |
|---|---|---|
| yhl（CM） | test-data-plan.md §5 导入器派生规则 + demo.json | 实现 dev 种子导入器（APP_DEMO_SEED=true 门禁、幂等跳过）；实现 E2E 测试 Compose（test-plan.md §3） |
| jxy（PM） | DbTestSupport/ConcurrentRunner 将编译于其工程 | M0 交付 pom.xml + contract 包后知会我，我跑首次编译 |
| hfy（DS） | scheduling.json 8 场景 + test-data-plan.md §7 | 按 02 契约 §8 提供 StrategyEvaluator/StrategyResult（仅供测试调用）；核对场景几何与自测 TC-04/05/06/13 |
| yc（UI） | test-design.md TC-09-2/23-1、附表视觉核对 | E2E 需公共 data-testid；页面按 05 界面规范提供 |
| fyq（AN） | test-design.md TC-03/15/16 | 活动状态机自测；容量规则与报名冻结配合 |

**已知问题/风险**：
- Java 三件套未编译验证（如实记录，不写"通过"）；DbTestSupport 的 `input_json` 写入用 Jackson 序列化 input 节点，V1 schema 的 jsonb 列名以 02 契约 §4 为准，DS 若调整列名需同步。
- 故障场景（TC-18/TC-22）的冲突码取 EQUIPMENT_SHORTAGE 或 RESOURCE_FAULT 契约未唯一指定，用例不硬断言，待 PM 按 02 契约 §7 裁决。
- 本机 Node 26，契约运行时为 Node 22（≥22.18.0）；qa 工程 `engines` 已声明，`@types/node` 锁 22.x，团队用 Node 22 执行时如遇差异以契约为准。
