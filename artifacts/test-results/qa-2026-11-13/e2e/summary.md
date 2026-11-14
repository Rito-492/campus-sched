# 2026-11-13 浏览器全量复跑（PR16 复审清理项）

- 代码：HEAD 含 TC-12-1 抢占活动清理（所有者 API-21 取消并核对 CANCELLED），`change-conflict` 与 `change-happy-path` 在全新库同序执行 6/6 通过。
- 默认命令 `npm --prefix qa run test:e2e`（`workers: 1`）在全新 PostgreSQL + 真实后端/前端：**29/29 通过（42.5s）**。
- 说明：本机 8080–8089 端口被环境占用，临时将 `web/vite.config.ts` 开发代理指向 18080，仅用于本次运行，运行后已还原、未提交；产品与测试断言未改。
- 并发对照：默认多 worker 跨文件并发会共享 demo 活动 A，导致 3 个用例因状态竞争失败（26/29）；`workers: 1` 已写入 `qa/playwright.config.ts` 防复发。
