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

## 启动演示

从源码包根目录按[发布说明](release/README.md)启动，使用本机演示口令。打开 `http://127.0.0.1:8088`，依次以申请人、批准人、资源管理员和学生演示活动提交、审批、预约、报名与改期。申请人可以在改期前查看冲突和备选方案；遇到最终确认冲突时可以核对旧预约仍有效。

## 代码与验证

后端包含统一账号与权限、命令幂等、活动、资源调度、改期、通知和审计。40 项 [API 契约](contracts/openapi.yaml)都有生产实现。测试设计、用户手册和发布清单随源码提供。项目经理的权限边界、资源自动授权历史、命令回执和跨模块事务回归见[公共模块接入说明](docs/management/foundation-guide.md)。

本次交付候选在 Java 21、PostgreSQL 17 和 Node 22 上通过 101 项后端单元测试、172 项真实数据库集成测试、109 项前端单元测试及 34 项真实浏览器测试。角色资格的八组组合均通过 UI 与 HTTP 回归。验证范围见[本次执行证据](artifacts/test-results/project-completion-2026-11-14/check-summary.json)。
