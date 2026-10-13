# 设计岗位交接单（handoff）

> 维护：设计师（55230315）。格式依据 `agents/common/04-协作与集成规程.md` §4 固定模板。运行时信息填真实值；未执行的检查如实标注，不虚构通过。

## 本次交付（2026-10-13 第二批：DS-01 升级 v3.0——22 表 schema + 烟测 + SchemaIT + 文档）

- 成员、学号、任务ID：黄方勇 55230315 / DS-01（v3.0 契约升级）
- 基线：v3.0；基准 commit/包版本：main `c637653`（已合并入本人分支）；本人分支 `work/55230315-scheduling`
- 升级依据与判定：转述任务与 main 的 v3.0 契约逐字核对一致（02 §2/§4/§8、01 契约 §2 三角色与资格、QA 装载器列名）；V1 首形态（17 表）从未合并、未进基线，**原地升级合法，无 V2 补丁**（判定记录见 migration-register.md）
- 交付文件与接口：
  - `server/src/main/resources/db/migration/V1__schema.sql`——22 表（新增 user_permission/user_approval_scope/user_resource_scope/authorization_change/resource_eligibility_change；app_user.identity NOT NULL；resource.allow_teacher/allow_student；teacher_decision_json→approver_decision_json；PENDING_TEACHER→PENDING_APPROVER 全链路枚举替换）
  - `server/src/test/java/com/campus/events/resource/SchemaIT.java`——v3.0 口径 8 用例（tc01 22 表齐全…tc08 更名核验）
  - `docs/design/database-design.md`（v3.0：ER 图 22 实体、三角色/身份/资格表结构表、更严实现清单）
  - `docs/design/system-design.md`（状态机 PENDING_APPROVER、三角色/资格规则、AccountPort）
  - `docs/design/migration-register.md`（v3.0 版本决策记录 + 20 项烟测覆盖清单）
- 实际运行命令与结果：
  - ✅ **v3.0 psql 等价烟测**（PostgreSQL 17 docker postgres:17；campus_test 独立 schema；`psql -1 -v ON_ERROR_STOP=1` 单事务）：**20 项断言全部通过**（退出码 0），含 22 表齐全、新表授权数据链路、身份/角色/权限枚举、更名核验（teacher_decision_json=0）、延迟外键正负路径、3 触发器、资格/身份同事务变更终值核验
  - ✅ 延迟外键负路径（提交期校验）单独复核通过（首次形态与 v3.0 两轮均通过）
  - ⬜ **未执行**：`./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchemaIT" verify`（阻塞于 PM 基础工程 pom/mvnw/contract 包；SchemaIT 已就绪 v3.0 口径）
  - ⬜ **未创建**：`StrategyEvaluator`/`StrategyResult`（02 §8 v3.0 签名含 `Identity`）——阻塞于 PM `contract` 包（Plan/Alternative/Identity 类型），按规程替身只允许进测试代码，生产口径归 DS-05 交付
  - 环境：Docker Desktop + postgres:17 容器（ds-local-postgres，宿主 5432）；`.env` 白名单键就绪（`.env` 不入库）；测试 schema 用后即清，campus_test/campus_dev 无残留污染
- 验收编号与证据路径：DS-01 验收项已在上列烟测中达成（35 条断言的断言清单见 migration-register.md）；暂无 D-AT 通道项
- 未完成项/外部依赖及负责人：
  1. `server/pom.xml` + `contract` 包（Plan/Alternative/Conflicts/Identity…）→ **jxy 55230223（PM）**——SchemaIT 编译、StrategyEvaluator 生产代码、DS-02/03/04 全链依赖
  2. `scripts/load-env.ps1`/`check.ps1` → **yhl 55231031（CM）**；`.env` 拼写差异（TEST_DATABASE_USERNAME vs TEST_DB_USERNAME）待 PM/CM 裁决（DbTestSupport 实读前者）
  3. `demo.json` 及装载器与 V1 已对齐（QA 装载器列名逐字段核对通过）；QA 首跑 DbTestSupport 迁移可用本人容器或自建环境
- 下一接收人及其动作：**评审人员（QA/PM 择一交叉）**——按 pr-review-checklist 交叉评审本 PR；**jxy（PM）**——contract 包落地后知会跑 SchemaIT 与 StrategyEvaluator
- 接收确认：尚未接收（仅真实收到反馈后更新）

---

## 历史交付（2026-10-13 第一批：DS-01 首形态 17 表）

- 成员、学号、任务ID：黄方勇 55230315 / DS-01（v2.0 契约口径）
- 基线：v2.0→v3.0 过渡期；基准 commit：本分支 `f6bd329`（基线 main `b994ad3`）
- 交付文件与接口：V1__schema.sql（17 表首形态）、SchemaIT.java（6 用例）、system-design/database-design/migration-register 初版
- 实际运行命令与结果：✅ psql 等价烟测 14 项断言全部通过（PostgreSQL 17，campus_test 独立 schema）；⬜ Maven verify（阻塞 PM pom）
- 后续：首形态从未进基线，已被本批 v3.0 形态**原地升级取代**（见本批）；验证记录保留于 migration-register.md 供追溯。
