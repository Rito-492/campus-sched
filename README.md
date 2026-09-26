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

## 团队与开发分工

| 成员 | 学号 | 课程岗位 | 开发模块 |
|---|---|---|---|
| 冀项阳 | 55230223 | 项目经理 | 后端公共工程、账号权限、幂等、OpenAPI、集成 |
| 冯玉琪 | 22220314 | 系统分析员 | 活动、版本、审批、报名 |
| 黄方勇 | 55230315 | 设计师 | 数据库、资源、日历、联合预约、替代方案 |
| 汤雨润 | 55230316 | 测试经理 | 测试数据、跨模块验收测试、E2E、策略评测 |
| 杨瀚霖 | 55231031 | 配置经理 | 改期、通知、审计、环境与发布 |
| 杨畅 | 17231018 | 界面设计师 | Vue 前端全部页面与核心交互 |

完整分工与交接见 [`agent.md`](agent.md) §3、§5。

## 仓库结构

```text
agent.md + agents/   开发任务包 v2.0（总说明、公共契约、六份个人任务书）
docs/                文档区（course / management / requirements / design / ui / testing / configuration）
server/  web/  qa/   后端 · 前端 · E2E 测试工程
test-data/ contracts/ infra/ scripts/ release/ artifacts/ models/
```

详解见 [`docs/configuration/repo-structure.md`](docs/configuration/repo-structure.md)。

## 开始工作

- **成员/agent 启动开发**：读 [`agent.md`](agent.md)（含可直接使用的启动指令）
- **了解项目**：本 README → [`AGENTS.md`](AGENTS.md)（agent 与新成员指南）
- **查交付状态**：[`docs/management/task-assignments.md`](docs/management/task-assignments.md) §0
- **课程与分工文档**：`docs/course/`
