# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：2026-11-02T04:22:32.331507500Z
- 机器：Windows 11/amd64 / Windows 11 10.0 (amd64)
- JDK：21.0.12.1 (Eclipse Adoptium)；被测构建：未提供（构建时注入）
- 参考数据：test-data/scenarios/scheduling-reference.json（SHA-256=c7e9785b30c22e416fd5bdf3a0cde2b1803cd08267583b36cccf446b292df540）
- 场景数据：test-data/scenarios/scheduling.json（SHA-256=57b647328e15b868488d96ae0075e7f13e235ce3bc1cb01cd2e4d3053b184278；DEMO_DATE=2030-10-20，Clock=2030-10-19T00:00:00Z）
- 计时范围：候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）
- 测量：每场景每策略预热 1 次（已丢弃）、测量 10 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | 10 | true | -60 | false | 0 | 13 | 9.177 | 8.235 | 12.531 |
| SCH-01-TIME-CONFLICT | RANKED | 10 | true | -60 | false | 0 | 36 | 24.049 | 21.332 | 31.815 |
| SCH-02-VENUE-SWAP | FIRST_FIT | 10 | true | 0 | true | 0 | 3 | 2.214 | 1.679 | 2.98 |
| SCH-02-VENUE-SWAP | RANKED | 10 | true | 0 | true | 0 | 36 | 21.837 | 17.816 | 23.537 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 1.786 | 1.219 | 2.587 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | 10 | true | 0 | false | 1 | 36 | 21.802 | 16.456 | 27.507 |
| SCH-04-NO-SOLUTION | FIRST_FIT | 10 | false | null | null | null | 36 | 18.648 | 16.813 | 20.834 |
| SCH-04-NO-SOLUTION | RANKED | 10 | false | null | null | null | 36 | 17.642 | 16.782 | 19.01 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 1.11 | 0.858 | 1.842 |
| SCH-05-BOUNDARY-TOUCH | RANKED | 10 | true | 0 | false | 0 | 36 | 26.246 | 16.378 | 30.719 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 0.977 | 0.722 | 1.283 |
| SCH-06-PEAK-QUANTITY | RANKED | 10 | true | 0 | false | 0 | 36 | 17.643 | 14.956 | 22.448 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 1.566 | 1.249 | 2.039 |
| SCH-07-RESOURCE-FAULT | RANKED | 10 | true | 0 | false | 1 | 36 | 20.668 | 16.085 | 22.726 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | 10 | true | -30 | true | 0 | 7 | 4.482 | 2.807 | 5.01 |
| SCH-08-STRATEGY-DIFF | RANKED | 10 | true | 30 | false | 1 | 36 | 20.653 | 14.539 | 24.747 |

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
