# 初审问题再次核验 · 2026-11-01

杨畅（17231018），UI；仅本地核验，不是交叉评审批准。本轮没有修改产品代码、提交、推送或更新 PR。

依据：[PR #17 初审 5726456316](https://github.com/Rito-492/campus-sched/pull/17#pullrequestreview-5726456316)，评审头 `a327b16`。本次重新 fetch 后 main 仍为 `107a301`，已包含在本地 HEAD `9fe38e9`；本地未提交修复保留。再次查询远端 UI 分支仍为 `a327b16`，因此本地已修复不表示远端 PR 已包含修复。

## 逐项结果

| 初审条目 | 代码核对与本次实际复测 | 结论 |
|---|---|---|
| 必须改 P2：403/404 后残留旧活动及关联记录 | `web/src/pages/ActivityDetailPage.vue:96` 对 401/403/404 清空实体；相关记录随实体卸载。单测核对旧标题、说明、资源名、人员 ID 消失；网络故障有旧快照提示并禁用办理。真实浏览器撤销跨组织审批范围后返回 403，旧实体和人员记录消失 | 本地已修复 |
| 必须改 P2：纯审核角色误查 API-04 候选人员 | `web/src/components/ActivityRecords.vue:26` 要求启用的 APPLICANT 且与活动同组织；其余展示 API-14 已授权 ID。单测覆盖纯 APPROVER、纯 RESOURCE_CONFIRM、跨组织兼任及停用账号；真实浏览器验证前三类角色均无候选查询、无多余 403 且人员 ID 可见 | 本地已修复 |
| 建议：409 重读清掉结构化冲突 | `web/src/pages/ActivityDetailPage.vue:49` 等待重读后恢复 ConflictList；单测人为延迟 GET，确认重读后仍显示原因和“需要 2 / 可用 1”，下次成功后清除 | 本地已修复 |
| 建议：生产主包过大 | 代码与上一轮完整构建的哈希一致；核对该次 build 日志，主块 1,050.70 kB、gzip 348.08 kB，告警仍在。本轮未再次构建 | 未优化；初审明确非阻塞 |

另外复跑了资源查询错误掩盖 403、旧成功响应在新 403 后迟到、角色变化后旧姓名响应迟到等回归，均通过。

## 本次运行证据

Node 22.23.0；真实 Java/PostgreSQL 服务、Edge 浏览器；凭据来自忽略的本地配置并注入环境变量。没有使用 fixture 冒充业务联调。

- `vitest run tests/activity-detail.test.ts tests/activity-records.test.ts --maxWorkers=2 --reporter=default --reporter=json --outputFile=…`：**2 文件、15 项通过**，0 失败。
- `playwright test --config playwright.integration.config.ts review-regression.spec.ts --grep '真实纯角色与跨组织工作人员展示' --reporter=list,json`：**1 项真实回归通过**，0 失败/跳过/重试。
- 复测前核对上一轮 manifest：源码及测试文件与全量自查通过时一致；本轮随后仅增加证据与交接文档。无需将此前 98 项单测、10 项浏览器检查或 build 冒记为本轮重跑。

原始日志、JSON 和本轮 manifest：`artifacts/test-results/ui-pre-review-recheck-2026-11-01/`；真实截图：`artifacts/screenshots/ui-pre-review-recheck-2026-11-01/`。前轮报告和证据原样保留。

## 放行判断

初审两项必须改在本地均已修复并再次验证，409 建议也已完成，具备提交复审的条件。大包告警仍为非阻塞建议。远端尚未包含修复，且最终放行必须由独立评审人核对更新后的提交；本报告不能代替正式批准。

后端全量 verify 错误、API-18 空意见缺陷和未完成的整体验收继续按 `self-review-2026-11-01.md` 如实披露；依据初审原文，不把未完成整项目验收直接判作本 UI PR 新增缺陷。

下一接收人：QA/独立评审人核对本轮证据。尚未接收、未对外发送；提交或更新 PR 前仍须用户确认。
