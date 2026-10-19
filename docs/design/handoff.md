# 设计岗位交接单（handoff）

> 维护：设计师（55230315）。格式依据 `agents/common/04-协作与集成规程.md` §4 固定模板。运行时信息填真实值；未执行的检查如实标注，不虚构通过。

## 最新交付（2026-10-19：DS-03/05 纯逻辑；DS-02～05 接入依赖核对）

- 成员：黄方勇 55230315；基线 v3.0；实际 `origin/main=68158cf`。本人 `work/55230315-scheduling` 开始时干净，由 `bbeb984` 快进同步到 main，未 reset、未推送、未创建 PR、未向 main 合并。
- 新增 scheduling 包 `TimeInterval.java`、`CapacitySweep.java`、`CandidateRanking.java` 及对应三个 `*Test.java`。实现半开区间/布撤场/上海日/开放时间、设备同时峰值与故障下限、候选代价及规范设备排序；不改 V1 schema/SchemaIT、公共 contract 或他人文件。
- Java 21.0.6 `javac --release 21` 编译退出码 0；通过本机缓存 JUnit Jupiter 5.10.2 引擎直接运行，13 用例、0 失败、0 跳过，退出码 0。包含 200 组随机区间对照；完整命令、临时引擎启动器及输出见 [检查证据](scheduling-check-v1.0.md)。这不是 Maven 全工程或产品验收通过。
- `./scripts/check.ps1 -Scope scheduling` 退出码 1：缺 PM pom/Wrapper；`./server/mvnw.cmd -f server/pom.xml test` 退出码 1：命令不存在。`git diff --check` 退出码 0。
- 未执行数据库/SchemaIT/并发/回滚/HTTP/E2E/策略评测；30 个验收场景未执行。本轮不重复旧数据库烟测，不将旧 handoff 的历史结果当作新结果。
- DS-02 资源资格及接口、DS-03 日历/完整 check、DS-04 SchedulingPort 事务实现、DS-05 完整候选生成及 StrategyEvaluator/StrategyResult 仍待公共工程/DTO。纯逻辑不定义替身，也不返回伪成功。
- [接入清单](scheduling-progress-v1.0.md) 已一次列清 PM 所需文件/类型/签名、影响任务以及 AN/CM/QA 的交接点。实际 main 已有 CM Compose/check/load-env，旧记录中“脚本缺失”已不适用；本轮未验证数据库运行状态。
- 下一接收人：PM 冀项阳交公共 contract/构建/认证与事务入口；DS 随后接真实资源、候选及端口，AN/CM 接预约端口，QA 交叉验证。接收确认：尚未接收。用户随后授权提交、推送本人分支并创建 PR；交叉评审尚未进行，不自审自批，不自动合并或联系其他成员。

---

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
