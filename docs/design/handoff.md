# 设计岗位交接单（handoff）

> 维护：设计师（55230315）。格式依据 `agents/common/04-协作与集成规程.md` §4 固定模板。运行时信息填真实值；未执行的检查如实标注，不虚构通过。

## 本次交付（2026-10-13 DS-01：schema、设计文档、迁移验证）

- 成员、学号、任务ID：黄方勇 55230315 / DS-01
- 基线：v2.0；基准 commit/包版本：`work/55230315-scheduling`（本分支首个提交，基线代表 main 的 `b994ad3`）
- 交付文件与接口：
  - `server/src/main/resources/db/migration/V1__schema.sql`——17 表完整结构（外键/CHECK/索引 + 3 条不可变保护触发器），契约 §4 逐字段核对
  - `server/src/test/java/com/campus/events/resource/SchemaIT.java`——迁移验证集成测试（6 用例，等价于上述烟测断言的固化形态）
  - `docs/design/system-design.md`（初版：模块图、scheduling 模块结构、活动/改期状态机、首次确认时序 Mermaid 图源）
  - `docs/design/database-design.md`、`docs/design/migration-register.md`
- 实际运行命令与结果：
  - ✅ psql 等价烟测（PostgreSQL 17，docker 镜像 postgres:17；与 DbTestSupport 相同的库命名规则：campus_test 独立 schema `t_<32hex>`、单事务 `-1`、`ON_ERROR_STOP=1`）：**14 项断言全部通过**，含唯一键/CHECK/外键/待处理改期唯一/延迟复合外键正负路径/三条触发器保护（清单见 migration-register.md）
  - ⬜ **未执行**：`./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchemaIT" verify`（阻塞于 PM 基础工程 pom/mvnw/contract 包；`SchemaIT` 已写就，PM 到位即跑，结果补登 migration-register.md）
  - 环境：本机 Docker Desktop + postgres:17 容器（`ds-local-postgres`，宿主 5432，库 `campus_test`/`campus_dev` 与 `.env` 已就绪，`.env` 不入库）
- 验收编号与证据路径：本批为 M0 结构交付，暂无 D-AT 通过项；烟测断言清单 = `docs/design/migration-register.md` §烟测覆盖项
- 未完成项/外部依赖及负责人：
  1. `server/pom.xml` + `com.campus.events.contract` 包 → **jxy 55230223（PM）**，SchemaIT 编译依赖（Spring JDBC/Flyway/JUnit 均在 starter-test）
  2. `scripts/load-env.ps1` / `check.ps1` → **yhl 55231031（CM）**；我的 `.env` 已按 04 §5 白名单键就绪（另加 `TEST_DATABASE_USERNAME/PASSWORD` 双拼写，因 `DbTestSupport.java` 实际读的是这两个键，请 PM/CM 裁决统一）
  3. `docs/testing/test-data-plan.md` §7 的 blockers 派生规则需要 `demo.json` 的 organization…… QA 的 `DbTestSupport.loadTestData` 与 V1 兼容性 → 已通过烟测正向路径验证（org/user/resource/activity/version/reservation/registration 全链路写入一致）
- 下一接收人及其动作：**汤雨润**——`DbTestSupport` 的 Flyway locations `classpath:db/migration` 在 V1 就位后即可首次真实迁移（等你本机 Docker 环境或我的容器）；**jxy**——M0 工程落地后知会我跑 SchemaIT
- 接收确认：尚未接收（仅真实收到反馈后更新）
