# 两项P2修复与复验 · 2026-11-01

杨畅（17231018），UI-04/05。针对[本地专业评审](pr-review-2026-11-01.md)的两个新增P2完成代码修复与回归；当前状态为**本地修复通过，等待独立复审**。这不是正式交叉评审的放行结论。用户确认前不提交、推送或更新PR #17，不自动合并。

## 基准和范围

本人分支 `work/17231018-ui`；本轮已获取最新 `origin/main=107a301ba1ea040a0c322656789f078ff81093f2`，已包含在本地HEAD `9fe38e9c2d9ca9f2ae402acdee74e471ad4c0a48`。修复仍未提交，不能只用HEAD复现当前代码；本轮manifest记录工作区源码、文档及证据SHA-256。保留原有本地修改，变更限web、docs/ui及本人artifacts，没有修改后端、公共契约、QA或课程/用户Word。

## 问题与修复

| 原问题 | 修复及实际回归 |
|---|---|
| P2：资源刷新后资格开关保留旧值，再保存可能覆盖服务端新设置 | `ResourceManagePanel.vue`监听选中资源快照及写入修订号；同一ID的新快照也同步资格表单。显式刷新成功替换未保存资格输入，进入加载状态不提前覆盖。单测覆盖开→关、关→开及加载中保留输入；真实API外部关闭资格后，页面刷新、保存请求体、响应及再次读取均为false/false。 |
| P2：普通工作人员因人员数组被裁剪而没有历史入口 | `ActivityRecords.vue`允许启用账号点击查询改期历史，移除`domain/change.ts`基于人员数组推断历史权限的逻辑。API-28负责授权，403保留原因和requestId且不冒充空记录；初次进入详情不自动请求历史。真实普通工作人员API-14人员数组为空，仍能查询历史并进入待确认、APPLIED、REJECTED、WITHDRAWN详情；普通查看者实际403。 |

未扩大人员隐私披露或其他记录的权限；报名名单、审计和同组织候选姓名查询维持原有权限规则。保留请求代次隔离，单测验证旧账号迟到成功不能覆盖新账号403。新稳定标识：`change-history`、`change-record-<id>`。

新增 `web/tests/resource-management.test.ts`，扩充 `activity-records.test.ts`、`change-access.test.ts`及真实 `tests/integration/review-regression.spec.ts`。组件fixture仅在单测中使用；浏览器业务检查请求真实后端，资源通过API创建和修改，没有数据库直写或伪造成功。

## 本轮实际验证

Node22.23.0/npm11.16.0，Edge152.0.4191.66，真实Java21/PostgreSQL隔离服务。后端固定演示时钟为2030-10-19，不重新播种。凭据只由本地忽略配置传入`UI_DEMO_PASSWORD`，未写入报告。Windows通过Node22直接运行`C:/Program Files/nodejs/node_modules/npm/bin/npm-cli.js`；下表npm命令为等价简写，工作目录为web。

| 实际命令 | 结果与证据 |
|---|---|
| `npm run types:check` | 通过，`types-check.log` |
| `npm run typecheck` | 通过，`typecheck.log` |
| `node node_modules/vitest/vitest.mjs run --maxWorkers=2 --reporter=default --reporter=json --outputFile=../artifacts/test-results/ui-review-fixes-2026-11-01/unit-results.json` | 21文件107项通过，0失败/跳过；`unit.log`、`unit-results.json` |
| `npm run build` | 通过，生产产物无原型入口/fixture标识；`build.log`。Element Plus主块1,050.70kB的原有告警仍在，非本轮阻塞问题。 |
| `node node_modules/@playwright/test/cli.js test --reporter=list,json` | 13项通过，0失败/跳过；1366×768、1920×1080、390×844，`layout.log`、`layout-results.json`。开发预览布局与会话隔离检查，不是业务验收。 |
| `node node_modules/@playwright/test/cli.js test --config playwright.integration.config.ts real-service.spec.ts review-regression.spec.ts --reporter=list,json` | 11项真实浏览器检查通过，0失败/跳过；原有5项及评审回归6项，`real.log`、`real-results.json`。 |

浏览器环境变量：`PLAYWRIGHT_CHANNEL=msedge`；布局另设`UI_REUSE_SERVER=true`。`PLAYWRIGHT_JSON_OUTPUT_NAME`分别指向本轮JSON报告，`UI_EVIDENCE_DIR`分别指向本轮截图的`layout/`、`real/`子目录。类型/业务代码检查完成后，仅对新增测试应用格式化，无逻辑变化。

证据根目录：`artifacts/test-results/ui-review-fixes-2026-11-01/`；截图：`artifacts/screenshots/ui-review-fixes-2026-11-01/`。已人工检查真实资格刷新保存后的手机截图及普通工作人员进入已生效改期详情的桌面截图。原专业评审失败日志、诊断脚本、截图与manifest保留在原目录，不用新通过结果覆盖失败证据。

收尾检查：`git diff --check`通过；相对main的变更与未跟踪文件均在本人目录；未修改锁文件。待交付文本的本地凭据及令牌扫描通过，旧评审manifest列出的24个历史证据文件SHA-256保持一致。

## 遗留边界与交接

本轮只修复两项前端问题，依赖/锁文件未变，未重跑npm ci、后端重启或JDK21 verify。前轮npm ci、2项实际重启检查通过；JDK21全量verify的8项QA数据外键错误、1项AN/CM重复EventPort及API-18合法空批准意见400仍按[严格自查报告](self-review-2026-11-01.md)保留。完整QA E2E、Compose、D-AT/N-AT未验收；本轮11项真实测试不等于全系统验收。

用户手册、接口矩阵、界面说明、handoff和本地PR草案已同步。下一接收人QA/独立评审人核对最终提交及证据，PM跟踪其余依赖；**尚未接收**，未对外发送。最终提交或更新PR前仍需用户确认。
