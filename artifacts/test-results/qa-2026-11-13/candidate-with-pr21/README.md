# 候选组合真实验证（CM #21 未合入主线）

- 目的：复审要求“保留两条断言、待 CM #21 合入后复跑”；#21 仍未合并（PM 已 APPROVE，但仍有一条早前 CHANGES_REQUESTED 使 mergeStateStatus=BLOCKED），先在本地候选组合验证两条断言在修复后确实通过。
- 参与提交：QA PR #16 head `8f30a49`；CM PR #21 head `ade8c2b`；本地候选合并提交 `ff78b2a`；基线 main `db01829`。
- 命令与环境：JDK21 + PostgreSQL 17.11（127.0.0.1:55432，独立 campus_test），`./server/mvnw -f server/pom.xml verify`。
- 结果：**exit 0**；Surefire **101/101 通过**；Failsafe **158 项 = 157 通过、0 失败、0 错误、1 跳过**（跳过为 ResourceSchedulingIT 既有显式项）；`PermissionsIT.tc2001And02` 两条陈旧版本断言均通过。
- 边界：这是**候选组合**，不是已合入主线；正式放行仍需 #21 合入后以最新 main + 本 PR 再跑默认 verify 并附 SHA。本目录仅存档该候选运行，不修改产品与契约。
