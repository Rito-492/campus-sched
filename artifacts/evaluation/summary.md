# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：2026-11-10T06:01:28.161950189Z
- 机器：Linux/amd64 / Linux 6.18.33.1-microsoft-standard-WSL2 (amd64)
- JDK：21.0.13 (Eclipse Adoptium)；被测构建：未提供（构建时注入）
- 参考数据：test-data/scenarios/scheduling-reference.json（SHA-256=c7e9785b30c22e416fd5bdf3a0cde2b1803cd08267583b36cccf446b292df540）
- 场景数据：test-data/scenarios/scheduling.json（SHA-256=57b647328e15b868488d96ae0075e7f13e235ce3bc1cb01cd2e4d3053b184278；DEMO_DATE=2030-10-20，Clock=2030-10-19T00:00:00Z）
- 计时范围：候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）
- 测量：每场景每策略预热 1 次（已丢弃）、测量 10 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | 10 | true | -60 | false | 0 | 13 | 34.19 | 29.17 | 36.772 |
| SCH-01-TIME-CONFLICT | RANKED | 10 | true | -60 | false | 0 | 36 | 91.663 | 83.44 | 100.736 |
| SCH-02-VENUE-SWAP | FIRST_FIT | 10 | true | 0 | true | 0 | 3 | 8.163 | 6.207 | 13.325 |
| SCH-02-VENUE-SWAP | RANKED | 10 | true | 0 | true | 0 | 36 | 94.165 | 78.894 | 104.958 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 5.782 | 5.283 | 6.458 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | 10 | true | 0 | false | 1 | 36 | 90.142 | 83.837 | 101.459 |
| SCH-04-NO-SOLUTION | FIRST_FIT | 10 | false | null | null | null | 36 | 89.04 | 82.498 | 107.608 |
| SCH-04-NO-SOLUTION | RANKED | 10 | false | null | null | null | 36 | 87.633 | 85.069 | 103.022 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 3.201 | 2.504 | 4.006 |
| SCH-05-BOUNDARY-TOUCH | RANKED | 10 | true | 0 | false | 0 | 36 | 101.394 | 80.281 | 120.864 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 3.412 | 3.093 | 4.643 |
| SCH-06-PEAK-QUANTITY | RANKED | 10 | true | 0 | false | 0 | 36 | 88.308 | 85.207 | 109.619 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 5.4 | 4.939 | 5.894 |
| SCH-07-RESOURCE-FAULT | RANKED | 10 | true | 0 | false | 1 | 36 | 89.613 | 82.889 | 93.542 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | 10 | true | -30 | true | 0 | 7 | 16.482 | 15.917 | 19.363 |
| SCH-08-STRATEGY-DIFF | RANKED | 10 | true | 30 | false | 1 | 36 | 84.516 | 79.585 | 93.704 |

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
