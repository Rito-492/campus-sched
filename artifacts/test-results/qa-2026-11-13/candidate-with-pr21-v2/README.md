# 候选组合验证 v2（CM #21 最新 head，尚未合入主线）

- 目的：CM #21 已获 APPROVED 但 GitHub 仍 BLOCKED（2 条未解决的评审对话，仓库要求 conversation resolution）；在合入前用其**最新** head 与 QA 最新 head 组合复跑，确认两条陈旧版本断言在修复后通过。
- 参与提交：QA PR #16 head `8ca2efc`；CM PR #21 head `196f4a8`；本地候选合并 `fd7e868`；基线 main `db01829`。
- 环境：JDK21 + PostgreSQL 17.11（127.0.0.1:55432，独立 campus_test），默认 `./server/mvnw -f server/pom.xml verify`。
- 结果：**exit 0**；Surefire **101/101 通过**；Failsafe **158 项 = 157 通过、0 失败、0 错误、1 跳过**（跳过为 ResourceSchedulingIT 既有显式项）。`PermissionsIT.tc2001And02` 两种陈旧请求均通过。
- 边界：候选组合，不是已合入主线；正式放行仍需 #21 合入后以最新 main + 本 PR 复跑默认 verify 并附 SHA。
