# UI维护与联调验证 · 2026-11-10

收尾更新于2026-11-11：再次核对远端main仍为ca162bf，PR #21仍OPEN；本报告测试日期和结果保持2026-11-10实际运行记录，未把收尾核对记为重跑。服务关闭记录见`runtime-cleanup.json`，远端最终核对见`final-check-2026-11-11.json`。

杨畅（17231018），界面设计师；任务包v3.0，UI-01～05。分支`work/17231018-ui`，本轮从干净工作区快进同步到`origin/main=ca162bf975b2ebae0bd0dd59d7c63b23bc505b35`。该基线包含已合并的[UI PR #17](https://github.com/Rito-492/campus-sched/pull/17)和[空批准意见修复 PR #22](https://github.com/Rito-492/campus-sched/pull/22)。本轮改动尚未commit/push；验证绑定该基线加本轮工作区，文件哈希见证据manifest。

## 优先级与完成标准

| 优先级 | 工作 | 完成标准 | 结果 |
|---|---|---|---|
| P0 | 前端依赖审计 | 干净安装可复现；全量审计无moderate及以上问题；构建与原有回归不退化 | 已完成，8→0，109项单测通过 |
| P1 | 空批准意见闭环 | 空串/纯空白从真实页面提交，不补造意见；批准、刷新、资源预约、审计、日历和实际重启一致 | 已完成，新增2项真实业务及2项重启用例 |
| P1 | 当前UI回归 | 真实核心流程、关键失败反馈、三视口布局和生产组件通过 | 已完成本文所列范围，非全项目验收 |
| P1 | CM改期修复联调 | 所有者不自确认；旧版本409显示最新版本/状态，刷新后保留输入并可重试 | 等待PR #21进入可验收基线；未宣称已完成 |
| P2 | 同步交付材料 | 手册、界面说明、矩阵、交接和原始证据可对应到同一版本 | 已完成本地候选，等待独立评审及接收 |

## 依赖修补

首次审计8项：6 moderate、2 critical；最终完整依赖树审计0，`npm run audit:dependencies`退出码0。该命令使用`npm audit --audit-level=moderate`，不通过忽略开发依赖规避问题。

| 依赖 | 本轮前锁定值 | 本轮后锁定值 | 范围与理由 |
|---|---|---|---|
| vitest | 3.2.7 | 4.1.12 | 修补测试运行器/传递依赖链；直接依赖固定版本 |
| @vue/test-utils | 2.5.1（声明为^2.4.6） | 2.5.1（精确固定） | 固定局部覆盖的上游版本，未改变运行时产品依赖 |
| glob | 13.0.6 | 14.0.2 | 仅覆盖@vue/test-utils依赖树内的格式化工具链 |
| minimatch | 10.2.6 | 11.0.1 | 同上，修补文件模式匹配依赖 |

版本依据：[Vitest 4迁移说明](https://v4.vitest.dev/guide/migration.html)、[glob安全公告](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4)、[minimatch安全公告](https://github.com/advisories/GHSA-g3hv-75wg-m249)。本轮使用仓库规定的Node22；没有使用`npm audit fix --force`或全局覆盖。锁文件比较显示59个生产依赖包的版本均未改变，见`lockfile-comparison.json`。

新增`web/tests/tooling-compatibility.test.ts`两项回归：实际执行Vue测试包装器的HTML格式化；通过js-beautify CLI同时验证文件glob展开、EditorConfig匹配和非匹配文件不被改写。与原107项测试一起通过。后续上游移除受影响依赖后，可删除局部overrides，重新干净安装、审计并运行上述兼容性回归。

`audit-before.json`为修补前结果；`audit-after-initial.json`为过程中的未完成修补结果，最终以`audit-after.json`及`audit-gate.log`为准。安装过程日志一并保留；最终`npm-ci.log`来自Node22干净安装。审计结果只代表本次检查时点。

## 环境与执行结果

Windows；Node22.23.0、npm11.16.0、Java21.0.9、PostgreSQL17.6、Edge157.0.4341.42。npm入口显式使用Node22运行npm CLI，避免系统Node24被`npm.cmd`优先采用。参考`web/.node-version`。

本轮新建独立PostgreSQL集群，监听127.0.0.1:55433；后端使用当前main打包产物、`dev,e2e`，真实HTTP端口8080。时钟固定为2030-10-19，演示日期2030-10-20；测试活动日期2030-10-22，因此页面活动时间不同于本轮执行日期。初次启动播种，浏览器通过真实API生成独立资源及活动。未复用旧库作为本轮完成证据。

| 检查 | 本轮结果 | 原始证据（相对证据目录） |
|---|---|---|
| npm ci | 通过 | npm-ci.log |
| npm run audit:dependencies / audit --json | 0漏洞，通过 | audit-gate.log、audit-after.json |
| npm run types:check | 通过，共享类型未漂移 | types-check.log |
| npm run typecheck | 通过 | typecheck.log |
| npm run test:unit | 22文件、109项通过，无跳过 | unit.log、unit-results.json |
| npm run build | 通过；生产fixture剔除及体积门禁通过 | build.log、bundle-report.json |
| Playwright布局 | 13/13通过 | layout.log、layout-results.json |
| Playwright生产组件 | 3/3通过 | production-ui.log、production-ui-results.json |
| Playwright真实业务 | 13/13通过（原5项+评审回归8项） | real.log、real-results.json |
| 实际后端重启后Playwright | 4/4通过（原2项+空意见2项） | restart.log、restart-results.json、backend-restart.json |
| Java21 -DskipTests package | 通过，仅为启动服务 | backend-package.log |

体积：最大JS块201,474字节；登录页静态JS合计228,753字节、gzip89,265字节；登录CSS52,191字节。未用分块数量代替首屏依赖总量。生产组件和业务测试均访问实际`dist`预览4173；布局测试使用开发服务器5173，报告分别记录，不混算真实业务。

真实业务覆盖：登录/me/退出、无隐式教师审批、账号创建/完整授权/停用、资源创建/资格、草稿保存编辑/预览；纯角色/跨组织工作人员与撤权清除；空/纯空白意见首次批准、联合预约；发布报名、工作人员本人确认改期/复审/生效/日历通知；拒绝/撤回解冻、通知已读不代签；目标资源竞争409保留旧安排、故障AT_RISK；真实报名响应丢失一次后的同key/body重试；资格被外部关闭后刷新保存。测试使用真实后端，只有响应丢失场景主动丢弃一次真实成功响应，不合成成功数据。

新增空意见用例逐项核对：提交后PENDING_APPROVER；POST请求的comment分别为`""`与`"   "`；批准后APPROVED_UNRESERVED、有效版本为空、lockVersion恰好递增一次；刷新显示待资源确认；一次ACTIVITY_APPROVED审计；资源确认后SCHEDULED、有效版本正确；刷新后无API错误且唯一EVENT占用数量1。重启回归再次检查版本、完整安排、审计与唯一占用。

实际停止后端PID21512，再以`--app.demo-seed=false`启动PID5676，保持同一数据库。2026-11-10北京时间22:28:44新进程启动，22:29:16开始执行4项持久化检查，约12.3秒后全部通过；22:29:53补核对新进程启动时间、关闭播种参数及`/api/v1/health=UP`。进程及时间证据见`backend-restart.json`和`restart-results.json`。测试脚本自身不会重启服务，不应在未重启时把单独重跑脚本称为重启验证。本轮验证后关闭本轮启动的后台服务，独立运行目录保留在Git忽略范围。

## 复现入口

在Node22环境及`web/`目录执行：

```powershell
npm ci
npm run audit:dependencies
npm run types:check
npm run typecheck
npm run test:unit
npm run build
$env:PLAYWRIGHT_CHANNEL = 'msedge'
$env:UI_EVIDENCE_DIR = '../artifacts/screenshots/<本轮新目录>/layout'
npm run test:layout
```

另行启动真实后端和隔离数据库，将隔离环境密码通过`UI_DEMO_PASSWORD`注入，不提交密码。保持`npm run preview -- --port 4173`运行，然后：

```powershell
$env:UI_BASE_URL = 'http://127.0.0.1:4173'
$env:UI_REUSE_PRODUCTION = 'true'
$env:UI_EVIDENCE_DIR = '../artifacts/screenshots/<本轮新目录>/production'
npm run test:production-ui
$env:UI_EVIDENCE_DIR = '../artifacts/screenshots/<本轮新目录>/real'
npm run test:integration
npm run test:review
# 实际停止/重启自己的后端，保持同一数据库，设置app.demo-seed=false。
$env:UI_EVIDENCE_DIR = '../artifacts/screenshots/<本轮新目录>/restart'
npm run test:persistence
npm run test:review-persistence
```

本轮为收集JSON，真实业务使用Playwright CLI合并运行`real-service.spec.ts review-regression.spec.ts --reporter=list,json`，重启同理运行`persistence.spec.ts review-restart.spec.ts`；对应相同测试文件。完整脚本入口在`web/package.json`。端口、密码和运行状态置于被忽略的`.integration-runtime/`，不随提交交付。

## 截图与视觉检查

截图根目录：[ui-2026-11-10](../../artifacts/screenshots/ui-2026-11-10/)。`layout/`为开发预览；`production/`为真实认证的生产组件；`real/`为生产产物真实业务；`restart/`为实际重启后的状态。每组按1366×768、1920×1080、390×844保存；fullPage截图高度可超过视口。报告不把开发预览截图当作业务完成证明。

本轮人工查看以下实际截图，关键文字与状态可读，桌面卡片和手机堆叠、弹窗内滚动/底部操作未见新增遮挡；自动检查同时覆盖横向溢出、中文控件、主题及浏览器错误：

- [桌面空意见预约结果](../../artifacts/screenshots/ui-2026-11-10/real/real-approval-empty-reserved-1366x768.png)
- [手机纯空白意见预约结果](../../artifacts/screenshots/ui-2026-11-10/real/real-approval-whitespace-reserved-390x844.png)
- [手机改期生效全页](../../artifacts/screenshots/ui-2026-11-10/real/real-change-applied-390x844.png)
- [桌面冲突后原安排及资源风险](../../artifacts/screenshots/ui-2026-11-10/real/real-conflict-risk-1366x768.png)
- [手机生产账号弹窗](../../artifacts/screenshots/ui-2026-11-10/production/production-dialog-390x844.png)

以上为本地视觉抽查，独立评审尚未签收。

## 未完成依赖与交接

截至2026-11-10本轮核对，CM [PR #21](https://github.com/Rito-492/campus-sched/pull/21)仍OPEN，head=`ade8c2b7979e6639dbc018d3022fbe6dcbd4356e`。未把其未合并实现当成main能力。该修复进入可验收基线后，UI的完成检查为：

1. 所有者为唯一工作人员：影响预览无需要本人确认项，创建后直接待复审，无本人确认按钮；刷新保持。
2. 所有者加其他工作人员：仅其他工作人员待确认；本人不能代签，批准人/资源复核仍按契约回避所有者。
3. 旧baseVersionNo/expectedLockVersion触发409：真实响应包含当前versionNo/lockVersion或status；页面显示原因、刷新最新状态并保留可重试输入；成功重试不重复预约。

QA负责完整权限/并发矩阵、D-AT/N-AT、正式E2E和独立交叉评审；CM/QA负责Compose干净部署；PM确定发布基线和最终手册签收。本轮未运行全量Java verify，未运行Docker/Compose（本机无Docker），不沿用旧基线的verify失败作为当前结果，不宣称项目级放行。

下一接收人QA/独立评审人、CM、PM，**尚未接收**。本轮只修改web、docs/ui和本人证据；未改共享契约、后端、QA工程或课程Office原件，未自动联系成员。新交付说明见[PR草案](pr-draft-2026-11-10.md)。

## 证据索引

原始结果：[artifacts/test-results/ui-2026-11-10](../../artifacts/test-results/ui-2026-11-10/)。`summary.json`记录各检查结果；`remote-status.json`记录本轮远端PR和main核对；`manifest.json`记录本轮源文件、文档、测试结果、截图及生产产物SHA-256。manifest本身不自引用，旧轮证据不覆盖。密码/令牌/数据库文件不纳入证据；认证trace和video关闭。
