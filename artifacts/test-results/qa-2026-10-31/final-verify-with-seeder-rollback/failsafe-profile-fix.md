# Failsafe测试profile修复复验摘要

- 初次真实PG `verify`：代码基线`a48367e`，PostgreSQL 17.11，TEST_DATABASE_URL/TEST_DB_*及随机DEMO_PASSWORD已配置。
- 初次结果：Surefire 70项通过；Failsafe 119项中出现33个ApplicationContext Errors，根因是Spring验收上下文未激活`test` profile，导致`application-test.yml`未载入、Datasource URL缺失。原始输出保存在本机`artifacts/test-results/qa-2026-10-31/maven-cache-access-retry/default-verify.log`（如未纳入提交，详细诊断仍在当前工作区）。
- QA修复：commit `98796ed` 中的`DbTestSupport`在JUnit支撑对象创建时启用test profile，并将当前隔离schema写入Spring datasource URL及Flyway schema属性；没有改生产代码或绕过真实数据库。
- 复验：commit `a697d90871c6126bf6f42f26c938be9a1e9792cc` 的默认`verify`下，所有Spring上下文均启动，Failsafe 120项实际执行、0 skipped；剩余25 failures/2 errors是端口缺失/行为断言/评测比较问题，未再出现datasource URL错误。
- 最终证据：同目录`context.txt`、`default-verify.exit`、`failsafe-summary.xml`及逐类`TEST-*.xml`。
