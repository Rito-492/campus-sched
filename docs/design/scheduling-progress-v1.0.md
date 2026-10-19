# DS 调度纯逻辑与接入清单 v1.0

日期：2026-10-19；黄方勇 55230315；依据开发任务包 v3.0；main 基准 `68158cf`。

## 本轮实现范围

本轮仅完成 DS-03、DS-05 的可独立逻辑，不将 DS-02～05 整项标完成。既有 V1 22 表迁移与 SchemaIT 未改动。

生产文件位于 `server/src/main/java/com/campus/events/scheduling/`，对应 JUnit 测试位于同名测试包：

- `TimeInterval`：半开区间、30～360 分钟活动、0～120 分钟布撤场、分钟精度、上海自然日及开放时间边界。占用结束点不得落入下一自然日。
- `CapacitySweep`：裁剪到申请区间后按端点扫描；同刻增减合并，避免相接预约产生虚假峰值；故障可用量最低 0；累加使用 long，输出精确不足区间、总需求及故障扣减后的容量。
- `CandidateRanking`：固定偏移序列及六字段代价排序；设备 JSON 按规范 UUID 字符串排序。UUID 使用字符串比较，避免 Java UUID 带符号比较与 PostgreSQL 顺序不同。调用方必须先拒绝重复设备 ID，再构造映射。

这些类型都是包内实现细节，不定义第二份 Plan、Identity 或公共端口。扫描器不读库、不排除活动、不负责身份鉴权；接入层必须查询 ACTIVE 预约、按 excludeActivityId 排除本活动，再传入真实数据。候选排序器不负责候选生成、资格过滤、去重或预算；这些能力尚未完成。

## 交给 PM 的依赖清单（一次列清，尚未发送）

以下文件均位于 `server/src/main/java/com/campus/events/contract/`，公共定义归冀项阳 55230223；完全沿用 common/02，不请求改字段或签名：

| 用途 | 所需文件/类型 | 影响任务 |
|---|---|---|
| 计划、身份及资源 | `Plan.java`、`EquipmentNeed.java`、`Identity.java`、`ResourceKind.java`、`ResourceEligibility.java`、`ResourceCreate.java`、`ResourceView.java` | DS-02/03/04/05 |
| 冲突及候选 | `ConflictCode.java`、`Conflict.java`、`CheckResult.java`、`Alternative.java`、`AlternativesView.java`、`Risk.java` | DS-03/04/05 |
| 故障与日历 | `FaultInput.java`、`FaultView.java`、`AffectedActivity.java`、`CalendarEntry.java` | DS-02/03 |
| 身份、范围及资源创建授权 | `Actor.java`、`Role.java`、`Permission.java`、`ApprovalScope.java`、`AccountPort.java` | DS-02/04；最终重查、创建者资源授权 |
| 查询预览及影响信息 | `Proposal.java`、`ActivitySnapshot.java`、`ActivityView.java`、`ActivityInput.java`、`ActivityStatus.java`、`ActivityPort.java` | API-24/25、故障影响可见性；实现由 AN 提供 |
| 公共端口和事件 | `SchedulingPort.java`、`EventPort.java`、`EventDraft.java`、`CommandResult.java` | DS-02/04；事件实现由 CM 提供 |

PM 还需交付 `server/pom.xml`、`server/mvnw`、`server/mvnw.cmd`、`server/.mvn/`，Java 21/JUnit 5/Spring JDBC/Flyway 工程与公共认证入口、业务异常映射、事务幂等执行器、统一响应。认证取当前 Actor 的具体 Java 入口及业务异常类文件名在契约中未固定，请 PM 随工程交接说明，DS 不自行创建另一套。

必须保持的签名（common/02 §5、§8）：

```java
// PM 定义，DS 实现
CheckResult check(Plan plan, int expectedAttendees, UUID excludeActivityId, Identity applicantIdentity);
AlternativesView alternatives(Plan plan, int expectedAttendees, UUID excludeActivityId, Identity applicantIdentity);
CheckResult lockAndCheck(UUID activityId, Plan targetPlan, int expectedAttendees, Identity applicantIdentity);
void replaceReservations(UUID activityId, int newVersionNo, Plan targetPlan);
void releaseReservations(UUID activityId);
Risk risk(UUID activityId);

// PM AccountPort
void grantResource(UUID userId, UUID resourceId);
Actor get(UUID userId);
Actor lock(UUID userId);

// PM foundation 公共能力（实际类文件由 PM 交接）
CommandResult execute(Actor actor, UUID key, String route, String canonicalJson, Supplier<CommandResult> action);
// Responses.success(status,data)、Responses.error(status,code,message,details)、Responses.list(items,truncated)

// DS 待 contract 到位后提供 scheduling/StrategyEvaluator.java 和 StrategyResult.java
public List<StrategyResult> evaluateStrategies(Plan plan, int attendees, UUID excludeActivityId, Identity applicantIdentity);
public record StrategyResult(String strategy, boolean feasible, Alternative choice,
                             int evaluatedCount, long elapsedNanos) {}
```

接入后顺序：资源资格/范围及历史记录 → 真实读取和硬约束映射 → SchedulingPort 事务适配器 → 候选生成及 StrategyEvaluator。最终预约检查锁新旧资源并按 UUID 排序，与资格更新使用同一行锁；replace/release 使用 MANDATORY 事务。资源创建经 AccountPort.grantResource，不直接写用户授权表。

DS-05 将共用确定性候选集合，执行偏移→场地 UUID→原设备及单项同类替换的枚举，最多检查 300 个完整组合，过滤资格及硬约束、去重后排序取最多 5 项；FIRST_FIT/RANKED 使用同一候选与约束。System.nanoTime 计时覆盖生成、资源读取、检查及选择；本轮没有生成策略评测结果。

## 其他接收依赖与边界

- AN 冯玉琪：`activity/ActivityPortAdapter.java` 的真实查询及版本接口，供独立预览查询服务；SchedulingPort 实现本身仅依赖 DS Repository，避免循环。
- CM 杨瀚霖：EventPort 实现（契约未固定类名）与真实数据库环境。main 已有 Compose、load-env/check 和配置，不再沿用旧 handoff 声称这些文件缺失；本轮没有启动或验证 PostgreSQL，不能宣称数据库已就绪。
- QA 汤雨润：已有 `acceptance/SchedulingBenchmarkIT.java` 和固定数据；等待 DS 实现上述评测签名后真实运行，当前 30 场景未执行。
- UI 杨畅：资源、日历和候选 HTTP 示例待真实接口落地，本轮不提供伪成功响应。

## 检查结果与验收对应

详见 `scheduling-check-v1.0.md`：Java 21.0.6 编译退出码 0，JUnit Jupiter 5.10.2 执行 13 用例、0 失败、0 跳过，包含固定随机种子的 200 组区间/12,000 个逐分钟对照点。仅纯逻辑单测。

`./scripts/check.ps1 -Scope scheduling` 退出码 1（缺 pom/Wrapper）；`./server/mvnw.cmd -f server/pom.xml test` 退出码 1（命令不存在）。未执行 Maven verify、SchemaIT、并发/回滚、数据库、HTTP、E2E 或策略评测。D-AT-04/05/06 的算法前置规则有单测支撑，但这些 D-AT 本身以及 N-AT 均未因此验收通过。

下一接收人 PM：交付构建及公共类型；随后 DS 继续资源/调度接入，AN/CM 接 SchedulingPort，QA 交叉检查及真实数据库验收。接收状态：尚未接收；未自动联系任何成员。
