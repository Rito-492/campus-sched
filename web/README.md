# 校园活动前端 · UI-01～05

杨畅（17231018）负责。基于 `main=feb0d70`，任务包 v3.0。

## 启动与验证

使用 Node 22（至少22.18，推荐 `.node-version` 指定版本）。在本目录执行：

```powershell
npm ci
npm run types:check
npm run typecheck
npm run test:unit
npm run build
npm run dev
```

开发地址 `http://127.0.0.1:5173`，`/api` 代理到 `http://127.0.0.1:8080`。正式构建输出 `dist/`，沿用配置经理的 `web/Dockerfile` 与 Nginx `/api` 代理；没有改动这些文件。`npm run preview` 仅预览静态产物，不提供后端代理。

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

当前交付边界、接口矩阵和后续联调顺序见 `docs/ui/handoff.md`、`docs/ui/api-matrix.md`。真实认证、账号、资源和草稿已局部联调，刷新/后端重启检查已通过；完整预约/报名/改期/通知、QA E2E及PR交付仍未完成。

## 真实局部联调

先启动真实后端和隔离测试数据库，使用仓库demo.json对应账号。通过环境变量`UI_DEMO_PASSWORD`传入隔离环境密码，默认前端5173；可指定`UI_BASE_URL`及`PLAYWRIGHT_CHANNEL=msedge`。然后运行`npm run test:integration`（5项，写入少量UI专用账号/资源/草稿，无API mock）。缺少密码或服务直接失败。

在操作者重启自己的后端并关闭重新播种后，运行`npm run test:persistence`，复查上一轮实际创建的数据。该脚本自身不重启服务，未重启不能宣称重启验收。运行状态及本地独立数据库放在已忽略的`.integration-runtime/`；请勿提交其中的密码/运行文件。

真实局部检查不能登记为完整D-AT-23/24。当前main提交审批和故障登记503，改期/通知/审计404；`node scripts/probe-integration.mjs`可在上述隔离环境留存失败请求与requestId，不计作通过的业务E2E。复现环境和实际结果见`docs/ui/real-integration-2026-10-31.md`。
