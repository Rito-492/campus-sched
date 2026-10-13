# 迁移登记表（migration-register）

> 维护：黄方勇 55230315（设计师）。规则：从 V1 起逐号登记；**已应用的版本不改**，需变更走 V2+；回滚在业务上不可用（Flyway 校验），错误修复以新版本追加。

## 登记清单

| 版本 | 文件 | 内容 | 状态 | 验证记录 |
|---|---|---|---|---|
| V1 | `db/migration/V1__schema.sql` | 17 表、外键/CHECK/索引、三条不可变保护触发器 | ✅ 已完成并入库 | 2026-10-13 PostgreSQL 17（docker postgres:17，campus_test）：等价 SQL 烟测 14 项断言全部通过；明细见下 |

## V1 烟测覆盖项（等价于 SchemaIT 断言，已真实执行）

1. 17 张表齐全（information_schema 计数 + 名称核对）。
2. `uq_app_user_username`：重复用户名拒绝。
3. `pk_command_receipt`：同 (actor, key) 第二次写入拒绝。
4. `uq_reservation_activity_version_resource`：同活动/版本/资源第二次预约拒绝。
5. `uq_notification_event_recipient`：同事件同收件人第二次插入拒绝。
6. `ck_resource_shape`：venue quantity=2 拒绝；equipment capacity 非 NULL 拒绝。
7. `ck_resource_open_window`：open_from ≥ open_to 拒绝。
8. 外键：resource_fault 引用缺失 resource 拒绝。
9. `uq_change_request_pending_activity`：同活动第二个待处理改期拒绝；该改期至终态（APPLIED）后允许再建新待处理。
10. 延迟复合外键正路径：同一事务插入 activity 与首版本 → 提交成功。
11. 延迟复合外键负路径：预约 `(activity_id, version_no=99)` 引用缺失版本 → 登记 OK，**提交时**首验失败并回滚 entire 事务。
12. `trg_activity_version_immutable`：UPDATE input_json 报"activity_version 不可修改或删除"。
13. `trg_change_request_plan_immutable`：UPDATE target_plan_json 报"不可修改"。
14. `trg_audit_log_append_only`：UPDATE summary 报"只追加"。

执行方式：`psql -1 -v ON_ERROR_STOP=1 -f`（单事务）在 `campus_test` 独立 schema `t_<32hex>`；执行后 schema 清理。等价物固化于 `server/src/test/java/com/campus/events/resource/SchemaIT.java`。

## 尚待补做（阻塞说明）

- `SchemaIT`（Java 形态）**尚未编译执行**：阻塞于 PM 基础工程（`server/pom.xml`/mvnw/`contract` 包）。到位后运行 `./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchemaIT" verify`，结果补登本表。
- 后续版本（V2+）：M1 各模块首建表时若需新表/索引，由设计师分配编号追加（当前无 V2 需求）。

## 编号约定（给其他成员的说明）

- 迁移文件命名：`V<号>__<语义>.sql`（语义为英文 kebab-case，如 `V2__reservation-composite-index.sql`）。
- **任何成员需要 schema 变更时，不要自己建迁移文件**——在交接单提出，设计师评估后出下号版本；因为索引/约束可能跨模块。
