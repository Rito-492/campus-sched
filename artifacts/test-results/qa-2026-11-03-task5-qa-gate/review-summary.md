# 任务5 QA 结果摘要（review 可读）— qa-2026-11-03-task5

- 成员：汤雨润（tyr，55230316）｜分支 `work/55230316-qa`｜HEAD `5f081aa`｜main `16de4a5`
- 日期：2026-11-03（Asia/Shanghai）｜范围：qa/scripts、qa/tests 新文件、acceptance 新测试、本人 artifacts
- 一句话结论：**质量门禁 27 项自测 + 契约检查 + 类型检查全部真实通过；门禁对 writer 成稿最终 exit 0（24 场景/134 条，UC 20/20、验收 30/30、API 40/40）；7 项 HTTP/PG 边界回归编译通过但未执行（无安全测试库），其中 3 项预计在 AN/CM 已知缺陷修复前会真实失败（契约正确预期，未迁就实现）。**
- 父代理技术复核更正（18:20）：① @Test 实为 **7 个**（此前误记 6）；② 资格用例收紧为 **submit 必须 403**（不再放行 submit 200 继续最终确认）；③ negative 语义检查扩充并降级 warning（不逼文档虚构错误码），占位内容仍硬拒绝。
- 补充（18:25 后）：父代理已独立复跑 27/27 自测 + typecheck + test:contract 全 exit 0，证据存 `artifacts/test-results/qa-2026-11-03-task5-final/`；成稿将补安全用例至 138/139 条，最终门禁由父代理运行（本摘要数值为我本轮实测口径，不累加）。

## 1. 交付物

| 文件 | 说明 |
|---|---|
| [check-test-design.ts](../../../qa/scripts/check-test-design.ts) | 测试设计用例质量门禁：校验 test-data/test-design-cases-v1.0.json；导出 `validate`/`validateJsonText`/`formatReport`/`runCli`，CLI `--file`；缺失/0条/硬性问题 exit 1 |
| [check-test-design.test.ts](../../../qa/scripts/check-test-design.test.ts) | Node 内置 node:test 自测 27 项（正例 1 + 反例 22 + 提示不阻塞 2 + CLI 1 + JSON非法 1），不依赖产品可执行 |
| [ContractBoundaryIT.java](../../../server/src/test/java/com/campus/events/acceptance/ContractBoundaryIT.java) | 真实 HTTP+PG 边界回归 **7 项**（标题/时长/空拒绝理由/合法空审批意见/资格-提交必拦/改期所有者确认×2），预期取自公共契约 |

未改动：生产代码、旧文档、handoff、package 锁、未跟踪历史证据；未提交 Git。

## 2. 门禁检查内容（缺失或 0 条一律失败）

结构 → 必填与具体（preconditions/inputs/steps/expected 非空数组、cleanup、automation）→ 唯一 ID（场景+用例全局）→ 场景编号覆盖（id=UC-01～20 一一对应，单大场景不算）→ 需求覆盖（UC-01～20；编号仅基线族 UC/FR/BR/NFR）→ 极性配比（每场景 ≥1 positive + ≥2 negative）→ 状态（逐条「未执行」）→ 验收覆盖（D-AT-01～24 + N-AT-01～06 共 30，未知报错）→ API 设计引用（API-01～40 出现在 inputs/steps/automation）。
内容级-硬性：占位内容（TODO/待定）报错。内容级-提示（warning）：negative 预期未见负面结果信号（拒绝/限制/降级/无副作用，含不显示/不出现/不渲染/非0/仅在等）→ 人工复核，不硬失败；FR/NFR/BR 引用统计；flow 引用未知备选流N。

## 3. 真实执行结果

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `npm --prefix qa run test:contract` | 0 | 40 接口、9 枚举、15 DTO 全部通过 |
| 2 | `npm --prefix qa run typecheck` | 0 | tsc --noEmit 无错误 |
| 3 | `node --test qa/scripts/check-test-design.test.ts` | 0 | **27/27 通过，0 失败，0 跳过** |
| 4 | `./server/mvnw -f server/pom.xml test-compile` | 0 | 编译通过，含收紧后的 ContractBoundaryIT（7 个 @Test） |
| 5 | `-Dit.test=ContractBoundaryIT` verify | — | **7 项未执行**：无安全已配置 campus_test（TEST_* 未设置）；按指示不搭库，如实记未执行 |
| 6 | 门禁①（18:09 前，文件缺失） | 1 | 找不到 test-design-cases-v1.0.json——按设计失败，不假通过（历史保留） |
| 7 | 门禁②（18:12，writer 首版） | 1 | 11 项问题：4 项 requirementIds 非法编号（writer 已修）+ 7 项 negative 关键词误伤（门禁规则已扩充） |
| 8 | 门禁③（18:16，writer 修订+规则更新） | **0** | **通过**：24 场景/134 条（47 正/87 负，ID 134 全局唯一），UC 20/20、验收 30/30、API 40/40；仅统计性 warning |

环境偏差（如实记录）：Node **v26.13.0**（基线 22 系）；OpenJDK **27**（基线 21，编译目标仍 release 21）；Docker 仅探测，历史容器未复用、未改动。

## 4. 未执行项与已知缺陷（保留契约正确预期，不改断言迁就实现）

未执行：ContractBoundaryIT 7 项数据库执行；test:e2e / evaluate / 全量 verify（本轮范围外）。

预期将失败的 3 项（integration-review-2026-11-02 §4 已知缺陷，修复前运行即缺陷证据）：

1. `approveWithEmptyComment_isLegal` — AN：合法 APPROVE 空意见被用作空事件摘要导致 400（owner 冯玉琪）；
2. `changeOnlyOwnerStaff_goesStraightToApprover` — CM：仅所有者 staff 未直接复审（期望 PENDING_APPROVER，owner 杨瀚霖）；
3. `changeOwnerPlusOtherStaff_ownerHasNoSelfConfirmationRow` — 同上 CM 缺陷：所有者不应有自我确认行（BR-07/BR-23）。

资格用例（收紧后）预期：create 403 → 活动/预约快照计数零写入；create 201 → **submit 必须 403 RESOURCE_INELIGIBLE**、状态留 DRAFT、仅草稿 1 行、预约快照不变（01-基线 §2 覆盖提交；若实现推迟到最终确认才拒将如实失败）。

## 5. writer 成稿复验结论

test-data/test-design-cases-v1.0.json（writer 18:14 修订版）门禁 **exit 0**：24 场景（id=UC-01～20 全覆盖 + 4 专项）、134 用例（47 正/87 负，每场景 ≥1+2）、逐条 status=未执行、30 验收、40 API 引用、FR 25/25、BR 32/32、NFR 30/30 全引用、无占位内容。遗留人工评审项：步骤/预期因果语义、UI 长内容压力用例的正/负面极性归类（门禁仅提示）。复验命令：`node qa/scripts/check-test-design.ts --file test-data/test-design-cases-v1.0.json`。

证据：[verify.log](verify.log)（含三轮门禁运行历史与更正记录）、[result.json](result.json)（本目录）。
