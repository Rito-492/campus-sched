# QA 独立支撑检查记录

- 基准：`origin/main` / 本地工作基准 `4fbda0ecf4f155ef1e05c385a677ebe7d13b087d`。
- 执行环境：Linux/WSL；Node.js 26.12.0；Java 27；Maven Wrapper 3.9.11；Docker 29.9；临时 PostgreSQL 容器 `postgres:17`，实际 PostgreSQL 17.11。
- 临时数据库：容器内 `campus_test`，端口仅绑定 `127.0.0.1:55439`；每次 DbTestSupport 运行创建随机 `t_<32 hex>` schema。容器使用 `--rm`，验证结束后已停止并自动移除；临时口令仅存在于本次进程环境，未写入仓库。
- 文本日志仅为满足仓库 `diff --check` 去除行尾空格；命令、退出结果和错误信息未改写。

## 检查及结果

| 检查 | 命令/方式 | 结果 | 证据 |
|---|---|---|---|
| API 契约 | `node qa/scripts/check-contract.ts` | 40 API、9 组枚举、15 DTO 通过 | `contract-check.log` |
| 测试数据引用 | `python3 artifacts/test-results/qa-2026-10-20/data-reference-check.py`：校验 demo、registration-concurrency、scheduling 的 UUID、实体引用、资源资格、场地/器材引用、场景数和搜索预算 | 通过；2 组织、9 用户、12 资源、2 活动、1 报名；20 并发用户；8 调度场景 | `data-reference-check.py`、`data-reference-check.log` |
| DbTestSupport 装载 | 使用 Maven 测试依赖编译当前 `DbTestSupport.java` 和 `DbTestSupportIT.java`，再由 `DbSupportProbe.java` 调用同一支撑类；数据库用 Flyway 应用 V1 | demo 装载通过：12 项资源范围、2 活动/版本及 jsonb 对象；参考数据装载不带活动/报名；无效报名外键失败后无部分行残留 | `support-compile.log`、`support-postgres.log`、`DbSupportProbe.java`、`atomic-fail.json` |
| 默认全工程构建 | `./server/mvnw -f server/pom.xml test` | 未进入测试；在 `DemoDataSeeder.java:118` 因未处理 `Exception` 编译失败（CM PR #8 尚未合入） | `default-test.log` |

`DbTestSupportIT.java` 已使用 Maven 测试依赖成功编译；因默认 Maven 工程先被 CM 编译错误阻断，本轮未通过 Failsafe 执行该 JUnit IT。真实 PostgreSQL 装载行为由临时运行器直接调用当前支撑类验证，不计为 30 个 D-AT/N-AT 的验收结果。覆盖矩阵仍保持全部未执行。

## 支撑代码修复说明

真实装载首轮复现 `fk_user_resource_scope_resource`（授权早于资源）；调整后又发现 `open_from`/`open_to` 以字符串绑定 SQL `time` 列失败。最终调整为资源先于管理范围写入、每个数据文件在单一事务中装载、`input_json` 显式 cast 为 jsonb，并将开放时间绑定为 `LocalTime`。修复后上述 demo/reference/回滚验证通过。
