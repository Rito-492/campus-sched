# 迁移登记表（migration-register）

> 维护：黄方勇 55230315（设计师）。规则：从 V1 起逐号登记；**已应用的版本不改**，需变更走 V2+（契章程 02 §4）；回滚不可用，错误修复以新版本追加。

## 登记清单

| 版本 | 文件 | 内容 | 状态 | 验证记录 |
|---|---|---|---|---|
| V1 | `db/migration/V1__schema.sql` | v3.0 契约 22 表（含 v3.0 前的 17 表基础上新增 user_permission / user_approval_scope / user_resource_scope / authorization_change / resource_eligibility_change）、identity 与资格列、外键/CHECK/索引、3 条不可变保护触发器 | ✅ 已完成并入库 | 2026-10-13 PostgreSQL 17（docker postgres:17，campus_test 独立 schema）：v2 形态（17表）与 v3.0 形态（22表）两轮等价 SQL 烟测均全量通过；明细见下 |

### V1 版本决策记录（2026-10-13）

- V1 的首次形态（17 表，v2.0 契约）只存在于本人分支 `work/55230315-scheduling` @ `f6bd329`，**从未合并 main、从未进基线**，QA 装载器未消费过。
- main 已按 02 契约 §7 规程（PM 组织一致更新）升到 v3.0 任务包与契约（`c637653`），QA 的 v3.0 装载器/数据以新字段名为准。
- 判定：将 V1 **原地升级**为 v3.0 形态，不产生 V2 补丁；升级以本轮烟测加验证，两形态验证记录都保留在下方，可追溯。
- 后续任何 schema 变更一律 V2+，不再原地修改。

## v3.0 烟测覆盖项（等价于 SchemaIT 断言，已真实执行）

1. **22 张表齐全**（information_schema 计数 + 名称核对，含 5 张新表）。
2. `uq_app_user_username`：重复用户名拒绝。
3. `ck_app_user_identity`：identity 非法值（'ORGANIZER'）拒绝。
4. `ck_user_role_role`：v2 旧角色 'TEACHER' 拒绝；APPLICANT/APPROVER/ADMIN 合法。
5. `ck_user_permission_permission`：四权限值之外拒绝。
6. `pk_command_receipt`：同 (actor, key) 第二次写入拒绝。
7. `uq_reservation_activity_version_resource`：同活动/版本/资源第二次预约拒绝。
8. `uq_notification_event_recipient`：同事件同收件人第二次插入拒绝。
9. `ck_resource_shape`：venue quantity=2 拒绝；equipment capacity 非 NULL 拒绝。
10. `ck_resource_open_window`：open_from ≥ open_to 拒绝。
11. 外键：resource_fault 引用缺失 resource 拒绝；user_resource_scope 引用缺失 resource 拒绝。
12. `uq_change_request_pending_activity`（待处理状态：AWAITING_CONFIRMATIONS / PENDING_APPROVER / PENDING_RESOURCE）：同活动第二个待处理改期拒绝；该改期至终态（APPLIED）后允许再建新待处理。
13. **更名核验**：`teacher_decision_json` 列不存在（information_schema 断言 =0）；`approver_decision_json` 可正常写入读取。
14. 延迟复合外键正路径：同一事务插入 activity 与首版本 → 提交成功。
15. 延迟复合外键负路径：预约 `(activity_id, version_no=99)` 引用缺失版本 → 登记 OK，**提交时**失败并整事务回滚。
16. `trg_activity_version_immutable`：UPDATE input_json 报"activity_version 不可修改或删除"。
17. `trg_change_request_plan_immutable`：UPDATE target_plan_json 报"不可修改"。
18. `trg_audit_log_append_only`：UPDATE summary 报"只追加"。
19. **资格/身份同事务变更**：resource allow_student 变更 + `resource_eligibility_change` 快照同事务写入；identity 变更 + `authorization_change` 快照同事务写入；终值核验（allow_student=false、identity=TEACHER）。
20. 5 张新表授权数据全链路正写（user_permission×2 / user_approval_scope / user_resource_scope / authorization_change / resource_eligibility_change 行数核账）。

执行方式：`psql -1 -v ON_ERROR_STOP=1 -f`（单事务）在 `campus_test` 独立 schema；执行后 schema 清理（无残留）。SQL 脚本等价物固化于 `server/src/test/java/com/campus/events/resource/SchemaIT.java`（tc01–tc08，v3.0 口径）。

## 尚待补做（阻塞说明）

- `SchemaIT`（Java 形态）**尚未编译执行**：阻塞于 PM 基础工程（`server/pom.xml`/mvnw/`contract` 包）。到位后运行 `./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchemaIT" verify`，结果补登本表。
- `StrategyEvaluator`/`StrategyResult`（02 契约 §8，签名含 `Identity applicantIdentity`）：类型依赖 `contract` 包（Plan/Alternative/Identity），**生产代码未创建**；替身按规程只允许测试代码。归入 DS-05 交付，QA-04 评测入口在 PM contract 包到位后交付。
- 后续版本（V2+）：M1 各模块如需新表/索引，由设计师分配编号追加（当前无 V2 需求）。

## 编号约定（给其他成员的说明）

- 迁移文件命名：`V<号>__<语义>.sql`（语义为英文 kebab-case，如 `V2__reservation-composite-index.sql`）。
- **任何成员需要 schema 变更时，不要自己建迁移文件**——在交接单提出，由设计师评估后出下号版本；索引/约束可能跨模块。
