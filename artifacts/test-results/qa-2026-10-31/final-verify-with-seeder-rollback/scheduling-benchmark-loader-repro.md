# SchedulingBenchmarkIT 场景装载缺陷复验记录

- 前次真实库运行：QA源码基线 `a48367e861dfc06ec99e8da35fbff89b44926636` 加DbTestSupport profile修复；PostgreSQL 17.11，`campus_test`。
- 命令：默认 `./server/mvnw -f server/pom.xml verify`。
- 实际：`SchedulingBenchmarkIT.loadScenario` 插入SCHEDULED activity后，自动提交时还没有对应 activity_version，PostgreSQL拒绝 `fk_activity_current_version`；策略调用未开始。记录键：activity `00000000-0000-0000-0000-000000005001`, version=1。
- 根因：activity_current_version是延迟外键，但场景装载每条JdbcTemplate更新各自提交；测试程序同时把input_json字符串直接绑定到jsonb列。
- QA修复commit：`98796edf86e1b21ca43c7ef2964d1fbfa51630fc`，用单场景TransactionTemplate包住活动/版本/预约/故障写入，并对input_json使用`?::jsonb`。
- 最终真实库复验：commit `a697d90871c6126bf6f42f26c938be9a1e9792cc` 的默认`verify`中 `SchedulingBenchmarkIT` 1/1通过，产生8场景×2策略×10次=160条原始测量。
- 证据：复验机器报告 `failsafe-reports/TEST-com.campus.events.acceptance.SchedulingBenchmarkIT.xml`；原始JSON `scheduling-evaluation.json`。初次失败原始Failsafe XML仍在本地未跟踪目录`artifacts/test-results/qa-2026-10-31/rerun-after-db-support-fix/`，没有改写。

此缺陷仅针对QA-owned评测夹具事务，不代表DS策略正确；策略对照差异另见缺陷登记DEF-QA-2026-07。