# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：2026-10-31T05:54:00.583446600Z
- 机器：Windows 11/amd64 / Windows 11 10.0 (amd64)
- JDK：21.0.6 (Oracle Corporation)；被测构建：未提供（构建时注入）
- 场景数据：test-data/scenarios/scheduling.json（DEMO_DATE=2030-10-20，Clock=2030-10-19T00:00:00Z）
- 计时范围：候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）
- 测量：每场景每策略预热 1 次（已丢弃）、测量 10 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | 10 | true | 0 | true | 0 | 9 | 19.487 | 6.961 | 35.85 |
| SCH-01-TIME-CONFLICT | RANKED | 10 | true | 0 | true | 0 | 144 | 296.026 | 129.77 | 479.822 |
| SCH-02-VENUE-SWAP | FIRST_FIT | 10 | true | 0 | true | 0 | 5 | 11.581 | 6.43 | 14.536 |
| SCH-02-VENUE-SWAP | RANKED | 10 | true | 0 | true | 0 | 144 | 281.62 | 202.503 | 358.817 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 3.446 | 1.194 | 5.337 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | 10 | true | 0 | false | 1 | 144 | 157.99 | 80.136 | 241.286 |
| SCH-04-NO-SOLUTION | FIRST_FIT | 10 | true | 0 | true | 0 | 9 | 18.565 | 9.41 | 23.09 |
| SCH-04-NO-SOLUTION | RANKED | 10 | true | 0 | true | 0 | 144 | 264.467 | 167.225 | 315.368 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 0.894 | 0.765 | 2.122 |
| SCH-05-BOUNDARY-TOUCH | RANKED | 10 | true | 0 | false | 0 | 144 | 88.259 | 64.54 | 212.416 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 1.493 | 0.851 | 3.428 |
| SCH-06-PEAK-QUANTITY | RANKED | 10 | true | 0 | false | 0 | 144 | 235.969 | 88.842 | 306.407 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 2.577 | 1.149 | 5.265 |
| SCH-07-RESOURCE-FAULT | RANKED | 10 | true | 0 | false | 1 | 144 | 264.928 | 85.735 | 297.949 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | 10 | true | 0 | true | 1 | 10 | 13.844 | 5.699 | 23.304 |
| SCH-08-STRATEGY-DIFF | RANKED | 10 | true | 0 | true | 1 | 144 | 205.833 | 74.298 | 287.5 |

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
