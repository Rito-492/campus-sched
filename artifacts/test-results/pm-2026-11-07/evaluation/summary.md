# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：2026-11-07T05:26:35.609161200Z
- 机器：Windows 11/amd64 / Windows 11 10.0 (amd64)
- JDK：21.0.12.1 (Eclipse Adoptium)；被测构建：未提供（构建时注入）
- 参考数据：test-data/scenarios/scheduling-reference.json（SHA-256=c7e9785b30c22e416fd5bdf3a0cde2b1803cd08267583b36cccf446b292df540）
- 场景数据：test-data/scenarios/scheduling.json（SHA-256=57b647328e15b868488d96ae0075e7f13e235ce3bc1cb01cd2e4d3053b184278；DEMO_DATE=2030-10-20，Clock=2030-10-19T00:00:00Z）
- 计时范围：候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）
- 测量：每场景每策略预热 1 次（已丢弃）、测量 10 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | 10 | true | -60 | false | 0 | 13 | 6.47 | 5.08 | 8.359 |
| SCH-01-TIME-CONFLICT | RANKED | 10 | true | -60 | false | 0 | 36 | 16.76 | 15.807 | 21.355 |
| SCH-02-VENUE-SWAP | FIRST_FIT | 10 | true | 0 | true | 0 | 3 | 1.348 | 0.988 | 1.931 |
| SCH-02-VENUE-SWAP | RANKED | 10 | true | 0 | true | 0 | 36 | 12.706 | 10.602 | 16.367 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 1.165 | 0.964 | 1.324 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | 10 | true | 0 | false | 1 | 36 | 14.366 | 12.747 | 26.028 |
| SCH-04-NO-SOLUTION | FIRST_FIT | 10 | false | null | null | null | 36 | 11.054 | 9.64 | 12.361 |
| SCH-04-NO-SOLUTION | RANKED | 10 | false | null | null | null | 36 | 10.732 | 9.368 | 13.193 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 0.542 | 0.421 | 1.024 |
| SCH-05-BOUNDARY-TOUCH | RANKED | 10 | true | 0 | false | 0 | 36 | 12.153 | 10.075 | 14.113 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 0.589 | 0.462 | 0.895 |
| SCH-06-PEAK-QUANTITY | RANKED | 10 | true | 0 | false | 0 | 36 | 11.722 | 10.246 | 14.1 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 0.925 | 0.712 | 1.546 |
| SCH-07-RESOURCE-FAULT | RANKED | 10 | true | 0 | false | 1 | 36 | 11.6 | 10.852 | 14.002 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | 10 | true | -30 | true | 0 | 7 | 2.086 | 1.88 | 2.926 |
| SCH-08-STRATEGY-DIFF | RANKED | 10 | true | 30 | false | 1 | 36 | 10.407 | 9.892 | 11.139 |

## 期望对照（断言目标 vs 实测）

| scenarioId | strategy | 期望代价 | 实测代价 | 结论 |
|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | (-60,0,0) | (-60,0,0) | 一致 |
| SCH-01-TIME-CONFLICT | RANKED | (-60,0,0) | (-60,0,0) | 一致 |
| SCH-02-VENUE-SWAP | FIRST_FIT | (0,1,0) | (0,1,0) | 一致 |
| SCH-02-VENUE-SWAP | RANKED | (0,1,0) | (0,1,0) | 一致 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | (0,0,1) | (0,0,1) | 一致 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | (0,0,1) | (0,0,1) | 一致 |
| SCH-04-NO-SOLUTION | FIRST_FIT | null | null | 一致 |
| SCH-04-NO-SOLUTION | RANKED | null | null | 一致 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | (0,0,0) | (0,0,0) | 一致 |
| SCH-05-BOUNDARY-TOUCH | RANKED | (0,0,0) | (0,0,0) | 一致 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | (0,0,0) | (0,0,0) | 一致 |
| SCH-06-PEAK-QUANTITY | RANKED | (0,0,0) | (0,0,0) | 一致 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | (0,0,1) | (0,0,1) | 一致 |
| SCH-07-RESOURCE-FAULT | RANKED | (0,0,1) | (0,0,1) | 一致 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | (-30,1,0) | (-30,1,0) | 一致 |
| SCH-08-STRATEGY-DIFF | RANKED | (30,0,1) | (30,0,1) | 一致 |

## 无解记录

- SCH-04-NO-SOLUTION/FIRST_FIT：0 个结果只表示约定搜索范围未找到方案（01 基线 §7）
- SCH-04-NO-SOLUTION/RANKED：0 个结果只表示约定搜索范围未找到方案（01 基线 §7）
