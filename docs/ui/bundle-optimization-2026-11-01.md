# 主包体积优化与复验 · 2026-11-01

杨畅（17231018），UI-01。落实[待提交版本复审](pr-rereview-2026-11-01.md)的P3建议：减少Element Plus全量加载，核验生产界面与核心流程。当前改动仅在本地，未提交、推送或更新PR；正式放行仍需针对最终提交交叉复审。

## 基准与改动

本轮fetch后`origin/main=107a301ba1ea040a0c322656789f078ff81093f2`，已在本人`work/17231018-ui`分支内。本地HEAD仍为`9fe38e9c2d9ca9f2ae402acdee74e471ad4c0a48`，加此前修复和本次未提交改动。保留既有用户文件、Word及历史证据，只修改web、docs/ui和本人artifacts。

- `main.ts`移除Element Plus全量注册和整套CSS，仅保留基础主题；`vite.config.ts`使用固定版本`unplugin-vue-components@32.1.0`及Element Plus resolver自动引入用到的组件和样式，不生成本地组件声明，不手工拆块或提高警告阈值。
- `App.vue`通过ConfigProvider保留中文配置，覆盖正式路由与开发预览；`style.css`提高根主题选择器优先级，避免异步组件样式覆盖项目主色。
- `package-lock.json`新增21个构建依赖条目，原有包版本未变化。Node22干净`npm ci`成功安装203包，最终产物体积可复现。
- `scripts/report-bundle.mjs`读取Vite manifest，按静态依赖闭包统计入口/登录页、全部JS/CSS及逐文件gzip体积；构建执行单块≤500kB、登录静态JS≤400kB检查，超限退出失败。
- 新增`playwright.production.config.ts`和生产三视口组件测试，真实认证与查询验证弹窗、下拉选择、中文按钮、主题、原型入口关闭和控制台错误。浏览器检查发现缺失favicon导致404，已补`public/favicon.svg`及HTML引用。

## 实测体积

先在修改组件加载前对当前代码实际构建，再在优化后及干净安装后构建；采用同一Node22.23.0环境。kB按1000字节换算，gzip为各文件压缩大小之和；没有把文件拆散后只报较小的入口文件。

| 指标 | 优化前 | 优化后 | 减少 |
|---|---:|---:|---:|
| 最大单个JS块 | 1,050.70kB | 201.47kB | 80.8% |
| 登录页全部静态JS | 1,053.26kB | 228.75kB | 78.3% |
| 登录页静态JS gzip | 349.52kB | 89.27kB | 74.5% |
| 登录页CSS | 372.78kB | 52.19kB | 86.0% |
| 所有产品路由JS合计 | 1,172.10kB | 532.79kB | 54.5% |

原始值见`before-bundle.json`、`after-clean-bundle.json`、`final-bundle.json`，Vite清单与构建日志同目录保留。最终构建不再出现500kB大块告警，生产fixture扫描及体积预算均通过。以上只说明产物体积，不等于实际网络加载时间降低同样比例。

## 实际检查

环境：Node22.23.0、npm11.16.0、Edge152.0.4191.66；真实Java21/PostgreSQL隔离服务保持原状态，未重新播种。凭据由忽略的本地配置传入环境变量，报告不保存密码或令牌。本机原npm入口可能使用其他Node，实际通过Node22运行`C:/Program Files/nodejs/node_modules/npm/bin/npm-cli.js`。

| 命令/入口 | 实际结果 |
|---|---|
| `npm ci --no-audit --no-fund` | 203包安装成功，`npm-ci.log` |
| `npm run types:check`、`npm run typecheck` | 通过；`types-check.log`、`typecheck-final.log`，最终build再次执行vue-tsc |
| `vitest run --maxWorkers=2 --reporter=default --reporter=json --outputFile=../artifacts/test-results/ui-bundle-2026-11-01/unit-results.json` | 21文件107项通过，0失败/跳过 |
| `npm run build` | 通过；`final-build.log`包含原型隔离和体积预算结果 |
| `npm run test:layout -- --reporter=list,json` | 13项通过；5173开发预览，3视口、组件布局及fixture/会话隔离 |
| `npm run test:production-ui -- --reporter=list,json` | 3项通过；4173实际dist，3视口、真实认证/查询、中文确认、弹窗及下拉框、主题、无控制台错误，不执行账号/资源写入 |
| `playwright test --config playwright.integration.config.ts real-service.spec.ts review-regression.spec.ts --reporter=list,json` | 指定`UI_BASE_URL=http://127.0.0.1:4173`，11项通过；生产dist配合真实后端，含登录/授权、资源、草稿、预约/报名、改期/日历/通知、冲突/拒绝/撤回/风险/同key重试，以及两项P2回归 |

各浏览器命令设置`PLAYWRIGHT_CHANNEL=msedge`，新`UI_EVIDENCE_DIR`和`PLAYWRIGHT_JSON_OUTPUT_NAME`指向本轮目录；布局复用5173时设`UI_REUSE_SERVER=true`，生产组件复用4173时设`UI_REUSE_PRODUCTION=true`。Vite preview实际继承开发配置的API代理，连接8080真实服务；README已纠正之前“preview不提供代理”的描述。此环境不是Compose/Nginx发布验收。

测试过程中保留并解决了两类诊断问题：首次Vitest有10套组件测试因Node直接加载CSS而无法启动，已将Element Plus样式入口交给Vite转换，未删测试或断言；首次生产测试错误地要求组件内部隐藏的原生checkbox可见，改为验证可见复选框外观与原生输入可用，未强制点击。随后发现并修复真实favicon 404。初始失败与定位日志保留，不覆盖为通过记录。截图等待下拉层关闭并结束动画，避免把过渡帧作为视觉证据；重复执行不重复计入测试数量。

## 证据和交接

报告目录`artifacts/test-results/ui-bundle-2026-11-01/`；截图目录`artifacts/screenshots/ui-bundle-2026-11-01/`，分开保存开发布局、生产组件及生产真实业务。已核对桌面和手机生产弹窗截图、主题主色及按钮可达性；manifest记录当前工作区文件及证据SHA-256，不能仅凭HEAD复现未提交源码。

收尾范围及凭据/令牌扫描通过，`git diff --check`通过；锁文件根依赖与package.json一致，原有包版本未变化。核对前三轮manifest列出的112项历史证据哈希一致，旧报告及失败截图没有被本轮测试覆盖。

本次未修改业务API或权限规则。此前JDK21全量verify的QA种子外键与重复EventPort问题、API-18合法空批准意见400、完整QA/Compose/D-AT/N-AT未验收继续保留；本轮未重启后端或重跑Java验证，不借前端检查宣称这些问题已完成。

交接单、工程README、界面说明及本地PR说明已同步。下一接收人QA/独立评审人核对最终提交、PM跟踪整体验收，均尚未接收，未向他人发送消息。5173开发预览已恢复，4173生产产物可本地查看。提交或更新PR前仍需用户确认。
