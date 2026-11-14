# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：2026-10-31T05:32:43.983206220Z
- 机器：Linux/amd64 / Linux 6.18.33.1-microsoft-standard-WSL2 (amd64)
- JDK：27.0.1.0.3 (Arch Linux)；被测构建：a697d90871c6126bf6f42f26c938be9a1e9792cc
- 场景数据：test-data/scenarios/scheduling.json（DEMO_DATE=2030-10-20，Clock=2030-10-19T00:00:00Z）
- 计时范围：候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）
- 测量：每场景每策略预热 1 次（已丢弃）、测量 10 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | 10 | true | 0 | true | 0 | 9 | 38.228 | 31.843 | 43.621 |
| SCH-01-TIME-CONFLICT | RANKED | 10 | true | 0 | true | 0 | 144 | 564.294 | 485.686 | 644.374 |
| SCH-02-VENUE-SWAP | FIRST_FIT | 10 | true | 0 | true | 0 | 5 | 15.984 | 14.508 | 23.664 |
| SCH-02-VENUE-SWAP | RANKED | 10 | true | 0 | true | 0 | 144 | 457.933 | 401.8 | 550.523 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 6.835 | 5.45 | 8.259 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | 10 | true | 0 | false | 1 | 144 | 411.889 | 363.905 | 446.259 |
| SCH-04-NO-SOLUTION | FIRST_FIT | 10 | true | 0 | true | 0 | 9 | 23.932 | 20.761 | 37.187 |
| SCH-04-NO-SOLUTION | RANKED | 10 | true | 0 | true | 0 | 144 | 361.017 | 322.543 | 418.294 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 3.281 | 3.051 | 4.232 |
| SCH-05-BOUNDARY-TOUCH | RANKED | 10 | true | 0 | false | 0 | 144 | 363.068 | 339.842 | 405.879 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 3.386 | 2.838 | 4.453 |
| SCH-06-PEAK-QUANTITY | RANKED | 10 | true | 0 | false | 0 | 144 | 357.127 | 323.901 | 393.421 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 5.834 | 5.322 | 6.86 |
| SCH-07-RESOURCE-FAULT | RANKED | 10 | true | 0 | false | 1 | 144 | 375.129 | 341.937 | 404.23 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | 10 | true | 0 | true | 1 | 10 | 25.705 | 24.708 | 37.096 |
| SCH-08-STRATEGY-DIFF | RANKED | 10 | true | 0 | true | 1 | 144 | 383.143 | 341.123 | 515.639 |

## 期望对照（断言目标 vs 实测）

| scenarioId | strategy | 期望代价 | 实测代价 | 结论 |
|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | (-60,0,0) | (0,1,0) | **不一致** |
| SCH-01-TIME-CONFLICT | RANKED | (-60,0,0) | (0,1,0) | **不一致** |
| SCH-02-VENUE-SWAP | FIRST_FIT | (0,1,0) | (0,1,0) | 一致 |
| SCH-02-VENUE-SWAP | RANKED | (0,1,0) | (0,1,0) | 一致 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | (0,0,1) | (0,0,1) | 一致 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | (0,0,1) | (0,0,1) | 一致 |
| SCH-04-NO-SOLUTION | FIRST_FIT | null | (0,1,0) | **不一致** |
| SCH-04-NO-SOLUTION | RANKED | null | (0,1,0) | **不一致** |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | (0,0,0) | (0,0,0) | 一致 |
| SCH-05-BOUNDARY-TOUCH | RANKED | (0,0,0) | (0,0,0) | 一致 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | (0,0,0) | (0,0,0) | 一致 |
| SCH-06-PEAK-QUANTITY | RANKED | (0,0,0) | (0,0,0) | 一致 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | (0,0,1) | (0,0,1) | 一致 |
| SCH-07-RESOURCE-FAULT | RANKED | (0,0,1) | (0,0,1) | 一致 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | (-30,1,0) | (0,1,1) | **不一致** |
| SCH-08-STRATEGY-DIFF | RANKED | (30,0,1) | (0,1,1) | **不一致** |

## 无解记录

（本次无不可行结果）
