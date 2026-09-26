# 测试数据设计 v0.1

> 归属：测试经理（55230316）。本文是 `test-data/` 目录的**数据格式唯一说明**，供配置经理的种子导入器、DbTestSupport 与策略评测共同遵守。
> 依据：`agents/common/03-验收与演示数据.md` §1/§4、`agents/common/02-数据与接口契约.md` §2/§4。字段名、枚举与校验规则以契约为准，本文不另造第二套。
> 执行状态与结果另见 `docs/testing/test-report.md`（QA-05 产出），本文只定义格式与规则。

## 1. 文件清单与消费方

| 文件 | 内容 | 消费方 |
|---|---|---|
| `test-data/demo.json` | 03 §1 统一演示数据集（组织/账号/资源/活动A、B/报名） | 配置经理种子导入器；DbTestSupport |
| `test-data/scenarios/registration-concurrency.json` | D-AT-16 并发报名数据（20 名独立学生 + 容量1活动） | DbTestSupport → RegistrationCapacityIT |
| `test-data/scenarios/scheduling.json` | D-AT-22 策略评测场景（8 个） | SchedulingBenchmarkIT、`qa/scripts/evaluate-scheduling.ts` |

## 2. ID 与尾号分配

- 所有 ID 为 UUID 字符串：`00000000-0000-0000-0000-XXXXXXXXXXXX`，尾号 12 位左补零（03 §1）。
- **禁止使用团队学号作为软件测试账号**；并发测试学生使用独立 ID 段（03 §1、QA-01）。

| 尾号段 | 用途 | 数量 |
|---|---|---|
| 1001–1002 | 组织 | 2 |
| 2001–2009 | 演示用户 | 9 |
| 2101–2120 | 并发测试学生（独立 ID） | 20 |
| 3101–3102 | 场地 | 2 |
| 3201–3202 | 器材 | 2 |
| 4001–4002 | 演示活动（A/B） | 2 |
| 4101 | 测试活动（并发报名） | 1 |
| 5001–5012 | 评测场景占用活动（含被评估活动自身旧占用） | ≤12 |
| 5101–5108 | 评测场景被评估活动（自身旧占用排除用） | 8 |

## 3. 通用约定

- `schemaVersion: 1`。
- 时间占位符 `${DEMO_DATE}`：**导入器/装载器必须替换**为演示日期 D（= 首次导入当天上海日期 + 7 天，可用环境变量 `DEMO_DATE` 覆盖，03 §1）。自动测试固定 `DEMO_DATE=2030-10-20`、Clock=`2030-10-19T00:00:00Z`，严禁测试依赖实际当天。
- 密码**不写入任何 JSON**：种子程序读取本地 `DEMO_PASSWORD` 环境变量并以 BCrypt 编码写入 `password_hash`（03 §1）。仓库禁止提交密码与真实账号资料。
- 演示/测试数据均为虚构；活动 description 统一以「（演示数据）」或「（测试数据）」结尾标注。
- 字段校验统一执行 02 §2 附加校验（title 1—100、description ≤2000、reason/comment ≤1000 等），导入器不得放宽。

## 4. demo.json 格式

顶层键**固定**为 `{schemaVersion, organizations, users, resources, activities, registrations}`，不得增减（03 §1）。

### organizations
`{id, name}`；name 全局唯一（02 §4）。

### users
`{id, username, displayName, organizationId, roles[]}`；roles 取 02 §2 `Role` 枚举值。无 password 字段（见 §3）。账号一律 enabled=true。

### resources
采用 02 §2 `ResourceView` 结构：`{id, kind, name, category, capacity, quantity, openFrom, openTo, createdAt}`。

- VENUE：capacity 为正整数、quantity=1、category 固定 `VENUE`；
- EQUIPMENT：capacity=null、quantity 为正整数、category 非空（本数据集为 `PROJECTOR`）；
- `createdAt` 使用确定性占位值 `${DEMO_DATE}T08:00:00+08:00`，便于测试断言。

### activities
`{id, ownerId, organizationId, status, versionNo, input}`；`input` 为 02 §2 `ActivityInput`（title/description/expectedAttendees/registrationCapacity/staffUserIds/plan）。plan 内日期字段使用 `${DEMO_DATE}` 占位。

### registrations
`{activityId, userId, status, versionNo}`（02 §2 `RegistrationView`）；versionNo 为该活动当前有效安排版本号。

## 5. 导入器派生规则（交配置经理，DbTestSupport 同规则）

JSON 是唯一数据来源，以下记录由导入器推导写入：

1. `app_user`：password_hash = BCrypt(`DEMO_PASSWORD`)，enabled=true；`user_role` 按 roles 数组逐值展开。
2. `activity`：version_no = versionNo；effective_version_no = versionNo（status ∈ SCHEDULED/OPEN 时，否则 NULL）；lock_version = 0。
3. `activity_version`：每活动一行，(activity_id, version_no, input_json = 完整 input, created_by = ownerId)。
4. `reservation`：status ∈ SCHEDULED/OPEN 的活动按 input.plan 派生 ACTIVE 预约（区间为 `[startAt−setupMinutes, endAt+teardownMinutes)`，01 基线 §4）：
   - 场地行：(venueId, quantity=1, 该区间)；
   - 器材行：每条 equipment 一行 (resourceId, quantity, 同区间)。
   - DRAFT/PENDING_TEACHER/APPROVED_UNRESERVED 活动不派生预约（首次确认前 0 条 ACTIVE，D-AT-03）。
5. 幂等：已存在同 id 记录则校验一致后跳过，不覆盖用户已修改数据（03 §1）。
6. 运行门禁：仅 dev profile 且 `APP_DEMO_SEED=true` 时运行（03 §1）。重新演示使用新数据库或明确授权的重置流程。

## 6. scenarios/registration-concurrency.json

`{schemaVersion, purpose, users[], activities[]}`；users/activities 结构同 §4。装载时与 `demo.json` **合并**（同 id 须校验一致）。

- 20 名并发测试学生：尾号 2101—2120，username `perf_s01`—`perf_s20`，STUDENT，组织 1001。
- 活动 4101「并发报名测试活动」：OPEN、registrationCapacity=1、registrations 为空（并发 JOIN 由测试执行，D-AT-16）。

## 7. scenarios/scheduling.json

结构（`jsonc` 仅为说明，实际文件为纯 JSON）：

```jsonc
{
  "schemaVersion": 1,
  "evaluation": {
    "strategies": ["FIRST_FIT", "RANKED"],       // 02 契约 §8
    "maxCandidates": 300,                        // 01 基线 §7 搜索预算
    "warmupRuns": 1, "measuredRuns": 10,         // 03 §4 预热1次测量10次
    "offsetsMinutes": [0, -30, 30, -60, 60, -90, 90, -120, 120]
  },
  "scenarios": [{
    "scenarioId": "SCH-01-TIME-SHIFT",
    "title": "纯时间冲突",
    "description": "……",
    "blockers": [{ "tail": 5001, "title": "……", "reservations": [
      { "resourceId": "…3101", "startAt": "${DEMO_DATE}T09:30:00+08:00",
        "endAt": "${DEMO_DATE}T11:30:00+08:00", "quantity": 1 }]}],
    "faults": [{ "resourceId": "…3101", "startAt": "…", "endAt": "…",
      "unavailableQuantity": 1, "reason": "……（测试数据）" }],
    "request": { "subjectTail": 5101, "expectedAttendees": 60,
      "plan": { "startAt": "…", "endAt": "…", "setupMinutes": 30,
        "teardownMinutes": 30, "venueId": "…3101",
        "equipment": [{ "resourceId": "…3201", "quantity": 2 }] } },
    "expected": { "feasible": true,
      "firstFitChoice": { "timeShiftMinutes": -60, "venueChanged": false,
        "replacedEquipmentLines": 0 },
      "rankedChoice": { "timeShiftMinutes": -60, "venueChanged": false,
        "replacedEquipmentLines": 0 },
      "firstFitSameAsRanked": true, "note": "……" }
  }]
}
```

装载与语义规则：

- **资源与组织来自 `demo.json`**：评测装载 = demo.json 的 organizations/users/resources + 本文件场景数据；**不装载**演示活动 A/B 与报名，避免场景互相干扰。
- `blockers` 生成「占用活动」（SCHEDULED、versionNo=1）及其 ACTIVE 预约行；input_json 由装载器合成（title=blocker.title，expectedAttendees=registrationCapacity=1，staffUserIds=[]，plan 的 venueId 取 blocker 的场地预约行、无场地行时取 request.plan.venueId，setup/teardown=0）。**实际占用以 reservations 为准**，合成 input 仅为满足不可变输入结构（02 §4）。
- `faults` 写入 `resource_fault`（02 §2 FaultInput）。场地故障 unavailableQuantity=1（01 基线 §8）。
- `request.plan` 为被评估目标计划；`subjectTail` 为被评估活动（即 evaluateStrategies 的 excludeActivityId，02 契约 §8）。blockers 中同 subjectTail 的预约表示**该活动自身旧占用**，用于覆盖「排除自身旧占用」（D-AT-13）。
- `expected` 是**断言目标**（测试设计值），**不是实测结果**。实测结果只允许写入 `artifacts/evaluation/results.csv`（字段：scenarioId、strategy、feasible、timeShiftMinutes、venueChanged、replacedEquipmentLines、evaluatedCount、elapsedMs，03 §4）；缺少原始结果时评测脚本必须失败，不得自动造数。
- 场景覆盖 03 §4 要求的七类：纯时间冲突、换场地、单器材替换、无解、边界相接、数量峰值、故障；另加 SCH-08 作为策略差异对照（FIRST_FIT 与 RANKED 选择不同，验证 01 基线 §7「不预设先进策略成功率一定更高」）。

## 8. 测试侧装载约定（DbTestSupport）

- 每测试类独立 schema `t_<UUID去掉连字符>`，正则 `^t_[0-9a-f]{32}$`，数据库固定 `campus_test`；结束只清理自己创建的 schema（03 §3）。
- 测试装载固定 `DEMO_DATE=2030-10-20`；测试用户密码哈希同由 `DEMO_PASSWORD` 生成。
- 各测试装载文件选择：
  - 一般集成测试：`demo.json`；
  - RegistrationCapacityIT：`demo.json` + `registration-concurrency.json`；
  - SchedulingBenchmarkIT：按 §7 规则装载 `scheduling.json`（各场景独立，跑前重置场景数据）。
