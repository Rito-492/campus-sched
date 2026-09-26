# 测试岗位交接单（handoff）

> 维护：测试经理（55230316）。格式依据 `agents/common/04-协作与集成规程.md` §4 固定模板。运行时信息填真实值；**不能为了填满模板虚构结果**，未执行的检查如实标注。

## 本次交付（2026-09-26 第二批，QA-03/QA-04 脚本）

- 成员、学号、任务ID：汤雨润 55230316 / QA-03（契约检查脚本部分）、QA-04（评测脚本部分）
- 基线：v2.0；基准commit/包版本：`work/55230316-qa` @ `824ef61`
- 交付文件与接口：
  - `qa/scripts/evaluate-scheduling.ts`——读 `server/target/scheduling-evaluation.json`，产出 `artifacts/evaluation/results.csv`（03 §4 八列）+ `summary.md`（机器/版本/数据/计时范围/中位数与范围/期望对照）
  - `qa/scripts/check-contract.ts`——核对 38 接口、7 组枚举、12 个 DTO 必需字段、响应包装、认证、Idempotency-Key
  - `docs/testing/test-plan.md` §4.1 覆盖判定规则（04 §3 判定权的规则化）
- 实际运行命令与结果：
  - ✅ `npx tsc --noEmit`（qa）——通过
  - ✅ evaluate 正路径：160 行合成夹具正确汇总、无解行代价 null、CSV 列名与 03 §4 一致；负路径：抽掉场景行后退出码 1 且不生成产物
  - ✅ check-contract 正路径：GOOD 夹具（38 接口/契约字段齐）退出码 0；负路径：BAD 夹具抓出 22 处差异退出码 1（缺接口/缺幂等键/枚举缺值/DTO 缺字段均命中）
  - ✅ 无输入时两者退出码 1（缺文件必失败，04 §5）
  - ⬜ **未执行**：对真实 `contracts/openapi.yaml` 与真实评测 JSON 的检查（等 PM 的 openapi.yaml、DS 的 StrategyEvaluator）
  - 夹具用后即删，`artifacts/` 无任何残留——**测试证据只能由真实运行产生**
- 验收编号与证据路径：暂无 D-AT 通过项；脚本自测输出仅本次命令行记录
- 未完成项/外部依赖及负责人：
  1. `contracts/openapi.yaml` → **jxy 55230223 / fyq 22220314**，到位后我跑真实契约检查（`npm --prefix qa run test:contract`）
  2. SchedulingBenchmarkIT 的原始 JSON 输出 → 依赖 **hfy 55230315** 的 StrategyEvaluator（02 契约 §8）；JSON 格式见 test-data-plan §9
  3. 其余同第一批（pom/contract 包、V1 schema、E2E Compose、data-testid）
- 下一接收人及其动作：jxy/fyq 提供 openapi.yaml 后通知我；hfy 的评测入口就绪后跑 QA-04 全链路
- 接收确认：尚未接收（仅真实收到反馈后更新）

### 流程观察（供 PM/CM 决策，非阻塞）

- 所有成员提交的 git author 均为 `Rito-492`，无法从提交溯源到人（本次 AN 分支的两提交因此被误认）。建议各成员设置独立 git 身份（如 `tyr-55230316` 拼音缩写，与公开仓库匿名化一致），由 CM 在配置管理计划中约定。

## 本次交付（2026-09-26 第一批，QA-01 全部）

- 成员、学号、任务ID：汤雨润 55230316 / QA-01（M0测试计划、固定数据和支撑代码）
- 基线：v2.0；基准commit/包版本：`baseline/task-package-v2.0`；分支 `work/55230316-qa` @ `4f5a7a1`
- 交付文件与接口：
  - `docs/testing/test-data-plan.md`（ID尾号/占位符/导入器派生规则 §5）、`test-design.md`（24项D-AT→33用例）、`test-plan.md`（含E2E环境约定 §3）
  - `test-data/demo.json`、`test-data/scenarios/{registration-concurrency,scheduling}.json`（8个评测场景）
  - `server/src/test/java/com/campus/events/support/{DbTestSupport,TestClockConfiguration,ConcurrentRunner}.java`
  - `qa/package.json`+`package-lock.json`（Playwright 1.63.0/TS 7.0.2 锁定）、`qa/tsconfig.json`、`qa/playwright.config.ts`
- 实际运行命令与结果：
  - ✅ JSON机器校验（合法性/ID唯一/引用完整/字段与02契约一致/代价列名与03§4一致）——通过
  - ✅ `npm install`（qa，lockfile v3 / 27包）——通过
  - ✅ `npx tsc --noEmit`（qa）——通过
  - ✅ `npx playwright test --list` ——真实退出码 1（0测试必失败语义正确；注意管道会吞退出码）
  - ⬜ **未执行**：Java三件套编译（阻塞于PM基础工程，见下）；24项D-AT场景执行（依赖产品实现，用例状态初始全部"未执行"，见 test-design.md §0）
- 验收编号与证据路径：本批为QA-01支撑交付，暂无D-AT通过项；证据目录约定 `artifacts/screenshots/`、`artifacts/test-results/`（03 §5）
- 未完成项/外部依赖及负责人：
  1. `server/pom.xml` + `com.campus.events.contract` 包 → **jxy 55230223（PM的M0基础工程）**。共享工程缺失期间未自建第二套工程（04 §2）
  2. `server/src/main/resources/db/migration/V1__schema.sql` → **hfy 55230315（DS）**，DbTestSupport的Flyway迁移依赖
  3. `scripts/load-env.ps1`/`check.ps1` + E2E测试Compose（dev,e2e profile、campus_test、端口8089、固定APP_TEST_NOW/DEMO_DATE）→ **yhl 55231031（CM）**，约定见 test-plan.md §3
  4. 故障场景冲突码（EQUIPMENT_SHORTAGE vs RESOURCE_FAULT）契约未唯一指定 → **jxy 按02契约§7裁决**，用例暂不硬断言
- 下一接收人及其动作：yhl（CM）按 test-data-plan.md §5 实现种子导入器与E2E Compose；jxy（PM）基础工程落地后知会我跑Java首编译；hfy（DS）按02契约§8提供StrategyEvaluator并核对scheduling.json场景几何
- 接收确认：尚未接收（仅真实收到反馈后更新）
