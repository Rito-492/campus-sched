# 修改后待提交版本复审 · 2026-11-01

评审对象：**main=107a301到当前工作区的完整UI候选变更，包含尚未提交的修复及新增测试文件**。本报告不评审远端旧head，也不把旧PR的结论直接套到本地新版本。

- 分支：`work/17231018-ui`；本地HEAD `9fe38e9c2d9ca9f2ae402acdee74e471ad4c0a48`，外加当前工作区改动。
- 本轮fetch后的main：`107a301ba1ea040a0c322656789f078ff81093f2`，未变化，已在本地分支中。
- 远端PR #17仍为OPEN/Draft、head `a327b16b171ab40da1ef336b8f1c0ab8d21bd834`，仅用于核对初审意见。本轮未提交、推送、发布评审或更新PR。
- 依据：AGENTS.md、taskbook.md、common五份文件、本人agent.md及`docs/testing/pr-review-checklist.md`。
- 署名口径：同一开发会话中的AI辅助本地复审；未冒充其他成员的独立交叉评审，也未执行GitHub Approve。

## 评审结论：建议改（非阻塞）

**当前候选版本未发现新增的必须改问题；之前两项P2已验证修复。可以交付最终提交供交叉复审。** 原有主包体积建议保留，不作为阻塞项。此结论绑定本报告manifest中的工作区文件内容，不等于远端旧版本已经通过，也不等于全项目验收完成。

### 必须改（阻塞合并）

本轮未发现可复现的新增阻塞缺陷。复查没有发现两项P2修复引入新的权限放宽、旧数据恢复或保存值回退。

### 建议改（不阻塞，可延后）

- [P3] `web/src/main.ts:3`、`:13`全量引入并注册Element Plus；已验证的生产主块为1,050.70kB（gzip348.08kB），仍有Vite大块告警。后续可按需引入组件并测量首屏体积，不通过提高告警阈值掩盖问题。沿用原评审非阻塞口径，未将其重新列为必须改。

## 旧意见在当前候选版本的闭环

| 问题 | 当前代码与核验结果 |
|---|---|
| 资源手动刷新后资格仍为旧值 | `ResourceManagePanel.vue:86`监听选中资源快照及revision，同ID的新响应也替换两项资格。真实外部PATCH关闭后，页面刷新显示关闭；后续保存的请求体、响应及重新读取均为false/false。反向开关及加载时保留输入由单测覆盖。通过。 |
| 普通工作人员因人员数组被裁剪而不能进入改期历史 | `ActivityRecords.vue:25`、`:66`不再用裁剪后的名单推断关系；点击页签才查询API-28，由后端授权。真实工作人员在人员数组为空时可进入待确认、已生效、已拒绝和已撤回详情；普通查看者实际403，未显示假空列表。通过。 |
| 原初审：活动失权刷新仍展示旧详情 | `ActivityDetailPage.vue`先读实体，401/403/404清空实体和关联内容；网络失败标注旧快照并禁用办理；代次检查拒绝迟到响应。相关代码与前轮通过的8项活动详情单测、真实撤权场景源码哈希一致，本轮静态复核无回退。 |
| 原初审：纯审核角色、跨组织账号误调人员候选接口 | `ActivityRecords.vue`限制为同组织启用申请人查询，其他账号展示服务端已授权的ID。本轮该组件13项测试全部通过，包含纯APPROVER、纯RESOURCE_CONFIRM和跨组织兼任账号。 |
| 原建议：409结构化冲突被刷新清空 | 活动详情冲突回调在重读后恢复ConflictList；读取资格丢失时不恢复关联内容。当前代码与前轮通过用例一致。 |

同时检查统一请求包装、幂等key/body冻结与未知结果重试、账号授权编辑、自审回避、资源管理与日历、活动/改期加载代次、记录页签、路由与生产fixture隔离；本轮未发现新的可确认缺陷。没有以增大人员数据披露范围来修复历史入口。

## 实际检查与证据

本轮在当前工作区运行Node22.23.0、Edge152.0.4191.66；真实浏览器连接现有Java21/PostgreSQL隔离测试服务。测试创建独立命名的数据，不重播种、不直接写业务表、不合成服务端成功。

| 本轮实际执行 | 结果 |
|---|---|
| `vitest run tests/resource-management.test.ts tests/activity-records.test.ts tests/change-access.test.ts --maxWorkers=2 --reporter=default --reporter=json --outputFile=../artifacts/test-results/ui-final-review-2026-11-01/targeted-unit-results.json` | 3文件22项通过，0失败/跳过 |
| `playwright test --config playwright.integration.config.ts review-regression.spec.ts --grep '真实首次预约报名\|真实通知已读不代签\|真实资源资格被外部关闭' --reporter=list,json` | 3项真实浏览器检查通过，0失败/跳过；包括工作人员各终态历史、无权403和资格刷新保存 |
| 前轮修复manifest逐文件SHA-256比对 | 201个源码/契约/文档/证据文件全部一致；本轮评审未修改产品或测试代码 |
| 范围、凭据/令牌扫描、`git diff --check` | 通过；候选改动限web、docs/ui及UI证据；没有后端/共享契约/QA/课程或Word改动 |

实际使用Node22执行本地`node_modules/vitest/vitest.mjs`及`node_modules/@playwright/test/cli.js`。浏览器设置`PLAYWRIGHT_CHANNEL=msedge`，凭据经`UI_DEMO_PASSWORD`传入，截图和JSON报告均指定本轮新目录，未覆盖旧证据。

**复用且核对哈希的前轮结果**：types:check、typecheck、21文件107项unit、build和生产fixture排除、13项布局、11项真实浏览器全部通过。本轮没有把这些记成又执行一次；只针对修复风险重跑上述22项/3项。完整原始结果仍在`ui-review-fixes-2026-11-01/`。

本轮报告和工作区文件哈希：`artifacts/test-results/ui-final-review-2026-11-01/`；12张真实截图：`artifacts/screenshots/ui-final-review-2026-11-01/real/`。`snapshot-check.json`记录评审开始时候选范围及201项哈希比对，`manifest.json`记录最终评审文件清单。

## 已核对无问题的维度

- 范围归属：本人UI目录；用户文件及原失败证据保留。
- 契约一致：API-04候选范围、API-14裁剪、API-28/29历史授权、API-40资格写入保持；未自创能力字段或接口。
- 真实性：测试结果由本轮实际执行或哈希一致的前轮输出支撑；开发布局与真实服务证据分开。
- 测试质量：资格断言包含页面、请求体、服务响应和再次读取；历史断言包含真实200/403、记录链接、详情状态；晚到响应和账号变化另有组件测试。
- 敏感信息：本地配置被忽略，待交付文本未发现凭据/令牌字面值，浏览器未开启携带认证流量的trace。

## 遗留风险与交付判断

本轮评审不把以下已披露项目依赖归为UI新增缺陷：API-18合法空批准意见400；前轮JDK21全量verify的8项QA外键装载错误和1项AN/CM重复EventPort；完整QA E2E、Compose、D-AT/N-AT尚未验收。本轮未重启后端或重跑Java verify，不宣称它们恢复通过。

**本地候选版本可以提交给评审人，正式合并放行仍要针对实际推送后的新head进行交叉复审。** 用户尚未授权本轮提交/推送；继续保持本地状态。接收人QA/其他岗位独立评审人、PM，尚未接收，未对外发消息。
