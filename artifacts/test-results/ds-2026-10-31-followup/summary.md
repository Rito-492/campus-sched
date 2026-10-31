# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：2026-10-31T07:56:57.612226500Z
- 机器：Windows 11/amd64 / Windows 11 10.0 (amd64)
- JDK：21.0.6 (Oracle Corporation)；被测构建：4681e4e+DS-bee9805
- 参考数据：test-data/scenarios/scheduling-reference.json（SHA-256=c7e9785b30c22e416fd5bdf3a0cde2b1803cd08267583b36cccf446b292df540）
- 场景数据：test-data/scenarios/scheduling.json（SHA-256=57b647328e15b868488d96ae0075e7f13e235ce3bc1cb01cd2e4d3053b184278；DEMO_DATE=2030-10-20，Clock=2030-10-19T00:00:00Z）
- 计时范围：候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）
- 测量：每场景每策略预热 1 次（已丢弃）、测量 10 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01-TIME-CONFLICT | FIRST_FIT | 10 | true | -60 | false | 0 | 13 | 46.837 | 29.524 | 66.246 |
| SCH-01-TIME-CONFLICT | RANKED | 10 | true | -60 | false | 0 | 36 | 134.473 | 95.481 | 156.432 |
| SCH-02-VENUE-SWAP | FIRST_FIT | 10 | true | 0 | true | 0 | 3 | 9.361 | 3.997 | 13.68 |
| SCH-02-VENUE-SWAP | RANKED | 10 | true | 0 | true | 0 | 36 | 76.847 | 60.942 | 122.118 |
| SCH-03-EQUIPMENT-REPLACE | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 6.073 | 4.142 | 8.339 |
| SCH-03-EQUIPMENT-REPLACE | RANKED | 10 | true | 0 | false | 1 | 36 | 89.69 | 44.676 | 106.713 |
| SCH-04-NO-SOLUTION | FIRST_FIT | 10 | false | null | null | null | 36 | 83.925 | 70.818 | 104.179 |
| SCH-04-NO-SOLUTION | RANKED | 10 | false | null | null | null | 36 | 94.578 | 80.814 | 107.251 |
| SCH-05-BOUNDARY-TOUCH | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 3.144 | 1.725 | 3.816 |
| SCH-05-BOUNDARY-TOUCH | RANKED | 10 | true | 0 | false | 0 | 36 | 83.121 | 43.772 | 108.691 |
| SCH-06-PEAK-QUANTITY | FIRST_FIT | 10 | true | 0 | false | 0 | 1 | 2.285 | 1.887 | 3.577 |
| SCH-06-PEAK-QUANTITY | RANKED | 10 | true | 0 | false | 0 | 36 | 71.076 | 44.846 | 81.083 |
| SCH-07-RESOURCE-FAULT | FIRST_FIT | 10 | true | 0 | false | 1 | 2 | 5.721 | 1.515 | 7.21 |
| SCH-07-RESOURCE-FAULT | RANKED | 10 | true | 0 | false | 1 | 36 | 79.63 | 67.999 | 94.088 |
| SCH-08-STRATEGY-DIFF | FIRST_FIT | 10 | true | -30 | true | 0 | 7 | 18.88 | 14.486 | 21.485 |
| SCH-08-STRATEGY-DIFF | RANKED | 10 | true | 30 | false | 1 | 36 | 94.319 | 73.863 | 105.094 |

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
