# 校园活动前端 · UI-01

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

布局测试检查登录、开发原型的3视口及缺后端时登录反馈。它不验证预约/改期持久化，不应登记为 D-AT/N-AT 验收通过。截图在 `artifacts/screenshots/ui-2026-10-31/`。

## 类型和接口

`src/types/api.ts` 由 `agents/common/02-数据与接口契约.md` 原文 TypeScript 块派生，`npm run types:sync` 更新。共享契约只读。`tests/contracts.test.ts` 检查40个客户端调用与共享OpenAPI的方法、URL和幂等头对应关系。

全部请求通过 `src/api/client.ts`。列表保留 `truncated`，错误保留 code/details/requestId。持久化命令先生成一次 key 和请求快照，失败重试复用。网络结果未知时创建表单锁定，不能以新key重复保存。生产页面仅请求真实API；未完成页面明确提示正在接入。

当前交付边界、接口矩阵和后续联调顺序见 `docs/ui/handoff.md`、`docs/ui/api-matrix.md`；并非全部UI-01～05及E2E完成。
