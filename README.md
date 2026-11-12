# campus-sched

**面向校园活动的多资源协同调度与变更影响分析系统** — 《软件工程案例分析与实践》课程团队项目。

以校园讲座、竞赛、社团展演等活动为主线，将**活动审批、场地与器材预约、人员协作和变更处理**关联起来：

- 🗓️ **多资源联合调度** — 联合检查场地容量、开放时段、器材数量与布场/撤场时间
- ⚠️ **冲突解释与替代方案** — 指出冲突资源/时段/数量，提供按明确偏好排序的换时间、换场地、换器材方案
- 🔄 **变更影响分析** — 改期前展示新旧差异与受影响的资源、审批、人员、报名；变更失败不破坏原预约

## 技术栈

| 层 | 方案 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.5.16 · Spring JDBC + Flyway · Spring Security |
| 数据库 | PostgreSQL 17 |
| 前端 | Vue 3 · TypeScript · Vite · Vue Router · Pinia · Element Plus |
| 测试 | JUnit 5 + Spring Boot Test（真实数据库集成测试）· Vitest · Playwright |
| 部署 | Docker Compose：db + server + web（Nginx） |

技术约束与依赖锁定见 [`agents/common/01-开发基线.md`](agents/common/01-开发基线.md) §2。

## 团队

| 成员 | 学号 | 课程岗位 |
|---|---|---|
| jxy | 55230223 | 项目经理 |
| fyq | 22220314 | 系统分析员 |
| hfy | 55230315 | 设计师 |
| tyr | 55230316 | 测试经理 |
| yhl | 55231031 | 配置经理 |
| yc | 17231018 | 界面设计师 |

本轮基于main的六人开发安排见 [详细开发计划](docs/management/detailed-project-plan.md)。三角色、独立身份及资源申请资格统一采用新版v3.0；六人承担的开发模块与交接关系见 [`taskbook.md`](taskbook.md) §3、§5。

## 仓库结构

```text
taskbook.md + agents/   新版开发任务包 v3.0（总说明、公共契约、六份个人任务书）
docs/                文档区（course / management / requirements / design / ui / testing / configuration）
server/  web/  qa/   后端 · 前端 · E2E 测试工程
test-data/ contracts/ infra/ scripts/ release/ artifacts/ models/
```

详解见 [`docs/configuration/repo-structure.md`](docs/configuration/repo-structure.md)。

## 开始工作

- **成员/agent 启动开发**：从 [六人开发入口](agents/README.md) 选择本人的 `agent.md`，复制文档末尾指令给开发助手；总分工见 [`taskbook.md`](taskbook.md)
- **了解项目**：本 README → [`AGENTS.md`](AGENTS.md)（agent 与新成员指南）
- **课程与分工文档**：`docs/course/`

## 项目经理工作包（2026-11-07）

main=`16de4a5` 已包含公共工程、账号认证/权限、幂等事务、活动、资源调度、改期/通知/审计和 UI。40 项 [API 契约](contracts/openapi.yaml)均有生产 Controller，五类端口已有真实实现。本轮 PM 修复小数版本/数量被截断、数字枚举被接受及资源自动授权缺少历史的问题，并补齐真库回归与管理交接。

需 Java 21、Node 22 和真实 PostgreSQL；默认后端入口为 `./server/mvnw.cmd -f server/pom.xml test` 及 `verify`。本轮 96 项单元和 43 项指定 IT 通过；默认全量仍有 9 个初始化错误、1 项跳过。最新 QA #16 的版本详情断言已符合契约，组合验收仍受 AN/CM 缺陷影响。前端 107 项单元、类型和构建通过，真实局部浏览器 5 项及关闭播种后的重启检查 1 项通过；QA 全部浏览器 29 项中 25 通过、4 失败。依赖审计仍为 8 项。具体修复、各次计数、责任与发布门槛见 [本轮集成记录](docs/management/integration-review-2026-11-07-v1.0.md)及 [交接单](docs/management/handoff.md)，完整验收与发布尚未放行。
