# 校园活动前端 · UI-01～05

杨畅（17231018）负责。当前基准 `main=750763b`（2026-11-14，含PR #17、#21与#22），任务包 v3.0。当前结果与依赖见[本轮报告](../docs/ui/ui-validation-2026-11-14.md)，[11月10日记录](../docs/ui/ui-validation-2026-11-10.md)保留原验证范围与日期。

## 启动与验证

使用 Node 22（至少22.18，推荐 `.node-version` 指定版本）。在本目录执行：

```powershell
npm ci
npm run audit:dependencies
npm run types:check
npm run typecheck
npm run test:unit
npm run build
npm run dev
```

开发地址 `http://127.0.0.1:5173`，`/api` 代理到 `http://127.0.0.1:8080`。正式构建输出 `dist/`，沿用配置经理的 `web/Dockerfile` 与 Nginx `/api` 代理；没有改动这些文件。`npm run preview -- --port 4173`用于本机核对生产产物；当前Vite版本继承同一`/api`代理，须另行启动真实后端，不替代正式部署。

Element Plus组件及样式通过`unplugin-vue-components`按需引入，不做全量全局注册。`App.vue`的ConfigProvider提供中文配置；项目主题变量使用`html:root`，避免延迟加载的组件样式覆盖主色。新增组件按现有`el-*`模板写法使用，无需维护第二份注册列表。

`npm run build`同时生成Vite manifest、检查生产fixture隔离与体积预算：单JS块不超过500kB、登录页入口及静态依赖合计不超过400kB。`npm run report:bundle -- --output <报告路径>`可导出原始/gzip字节统计；父目录需已存在。统计包括入口、登录路由及递归静态依赖，不把拆分文件当作首屏体积消失。

登录使用真实服务账号。令牌仅写入 sessionStorage；页面刷新/受保护路由切换会重新调用 `/auth/me`；401清除会话，403保留失败原因。没有默认账号、默认密码、自动成功登录或生产 Mock。

## 开发原型与生产隔离

仅 `npm run dev` 可打开 `/__prototype`。页面固定显示“演示数据 / 未连接后端”，仅展示四个组件交互，不调用 API 或写入认证会话。路由使用 `import.meta.env.DEV` 条件导入，生产构建剔除。build 后脚本扫描产物，发现原型路由或 `UI_FIXTURE_ONLY` 标志即失败。单元测试替身只在 `tests/` 内。

可选布局验证（独立于QA真实业务E2E）：

```powershell
npx playwright install chromium --only-shell
npm run test:layout
```

布局测试检查登录、工作台、账号与授权、资源管理、日历、方案/影响、改期状态、活动详情及通知的3视口，并检查开发预览不发送API或创建会话。它不验证预约/改期持久化，不应登记为D-AT/N-AT验收通过。

本机可使用已安装的Edge：设置 `PLAYWRIGHT_CHANNEL=msedge`；复用已有开发服务器时显式设置 `UI_REUSE_SERVER=true`。新证据可指定 `UI_EVIDENCE_DIR=../artifacts/screenshots/<新目录>`，避免覆盖旧版截图。2026-10-31本次证据在 `artifacts/screenshots/ui-completion-2026-10-31/`。

`/__prototype/workspace`复用正式组件，包含资源管理、改期工作台与进度外观。预览状态选择器只是切换固定样例，业务写入禁用。真实页面无法连接服务时显示错误，不回退到预览数据。

## 类型和接口

`src/types/api.ts` 由 `agents/common/02-数据与接口契约.md` 原文 TypeScript 块派生，`npm run types:sync` 更新。共享契约只读。`tests/contracts.test.ts` 检查40个客户端调用与共享OpenAPI的方法、URL和幂等头对应关系。

全部请求通过 `src/api/client.ts`。列表保留 `truncated`，错误保留code/details/requestId。持久化命令先生成一次key和请求快照，结果未知时锁定其他提交并复用原请求。`useMutation`区分写入失败与写入成功后的查询失败，后者只能重新查询。草稿冲突保留输入、等待新lockVersion；改期选择候选后重新预览最终目标，输入或版本变化即清除旧预览。生产业务页面全部请求真实API。

已实现11个约定产品路由及权限布局；草稿编辑在详情内展开，无需新增契约路由。单测覆盖权限回避、容量区间、未知结果重试、预览失效、冲突后保留输入以及待办6并发上限。

当前交付边界、接口矩阵和后续联调顺序见[交接单](../docs/ui/handoff.md)、[接口矩阵](../docs/ui/api-matrix.md)。PR #17已合并；本轮为新增维护候选。CM PR #21已合并，所有者不自确认及版本冲突恢复的3项真实页面用例已通过。实际重启证据仍为11月10日结果；完整QA E2E、N-AT-02全部UI资格组合及发布签收仍需相应负责人完成。

## 测试依赖维护

Vitest固定为4.1.12，`@vue/test-utils`固定为2.5.1；只在后者的依赖树内覆盖`glob=14.0.2`、`minimatch=11.0.1`，关闭本轮审计发现的测试工具链问题。未使用全局强制升级；生产依赖版本未变。`tooling-compatibility.test.ts`实际调用Vue测试包装器HTML格式化、js-beautify文件匹配及EditorConfig规则，检验覆盖版本的兼容性。上游依赖链修复后可移除局部覆盖，但需重新干净安装、审计并通过这些回归。审计门禁覆盖完整依赖树，不仅检查生产依赖；具体版本依据见本轮报告。

## 真实局部联调

先启动真实后端和隔离测试数据库，使用仓库demo.json对应账号。通过环境变量`UI_DEMO_PASSWORD`传入隔离环境密码，默认前端5173；可指定`UI_BASE_URL`及`PLAYWRIGHT_CHANNEL=msedge`。然后运行`npm run test:integration`（5项，写入少量UI专用账号/资源/草稿，无API mock）。缺少密码或服务直接失败。

在操作者重启自己的后端并关闭重新播种后，运行`npm run test:persistence`，复查上一轮实际创建的数据。该脚本自身不重启服务，未重启不能宣称重启验收。运行状态及本地独立数据库放在已忽略的`.integration-runtime/`；请勿提交其中的密码/运行文件。

`npm run test:review`（11项，新增3项CM回归见本轮报告）：真实纯角色/跨组织工作人员展示、撤销范围后的403清除；空字符串/纯空白意见首次批准、预约及审计；首次预约/报名/改期本人确认/生效/通知；拒绝/撤回解冻及通知已读不代签；真实资源竞争409保留原预约及故障风险；报名真实响应丢失后的同key/body重试；资源资格被外部关闭后的刷新与保存。主线及拒绝/撤回用例还验证普通工作人员在人员数组被裁剪时的历史入口，普通查看者403不得冒充空列表。响应丢失测试只丢弃实际成功响应一次，重试继续请求真实服务，没有合成成功。每项通过真实API建立独立数据；空意见测试核对真实请求体未补造意见、审批状态、一次审计记录及唯一日历占用。原始历史失败证据保留，PR #22合入后本轮已复验通过。

完成该检查并实际重启后端（关闭播种）后，单独运行 `npm run test:review-persistence`（3项）验证空/纯空白批准后的预约与审计，以及已生效改期安排、报名、日历和通知持久化。配合`test:persistence`共4项。各测试在隔离库保留唯一命名的数据用于复验，不执行清库。

新增`npm run test:production-ui`：先build并设置真实隔离服务的`UI_DEMO_PASSWORD`，自动启动4173的dist预览，检查3视口下的中文确认、弹窗/下拉框、主题、生产原型关闭和控制台错误，不提交业务写入。如果已运行该预览，显式设置`UI_REUSE_PRODUCTION=true`。截图仅在提供`UI_EVIDENCE_DIR`时保存。要在同一生产产物上运行原有业务用例，另行保持4173预览运行，设置`UI_BASE_URL=http://127.0.0.1:4173`，再执行`test:integration`和`test:review`。

这些检查不能登记为完整D-AT-23/24或Compose E2E。历史记录（2026-11-10，基准ca162bf）：npm ci/types:check/typecheck/build/体积预算、审计0问题、109项unit、13项开发布局、3项生产组件、13项生产产物真实业务及4项实际后端重启检查通过。最大JS块201.47kB；登录静态JS合计228.75kB（gzip89.27kB）。该轮未重跑全量Java verify或Compose；详见[11月10日历史报告](../docs/ui/ui-validation-2026-11-10.md)。最新证据见[11月14日主线复验报告](../docs/ui/ui-validation-2026-11-14.md)，旧重启结果不计为新主线重跑。
