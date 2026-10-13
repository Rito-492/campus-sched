# 系统设计文档（v0.2 草稿——DS-01 范围，v3.0 契约）

> 维护：黄方勇 55230315（设计师）。本文档随交付逐步充实，当前覆盖 DS-01：统一数据库结构与设计模型。v3.0 采用三角色（APPLICANT/APPROVER/ADMIN）、独立身份（TEACHER/STUDENT）与资源资格（allowTeacher/allowStudent，四组合均合法）。
> 全文体系为 Mermaid 图源（可直接编辑），不提供外部链接占位。

## 1. 系统模块图

单一 Spring Boot 后端按业务包拆分，包间仅通过 `contract/` 中定义的端口调用，禁止跨包直改他人表。

```mermaid
flowchart LR
    subgraph web["web — Vue 3 / TS（杨畅）"]
        FE["页面与 API 客户端"]
    end
    subgraph server["server — com.campus.events"]
        F["foundation/auth/contract<br/>PM：DTO、CommandExecutor、认证、AccountPort"]
        A["activity/registration\nAN：活动与报名"]
        R["resource\nDS：资源/故障/日历"]
        S["scheduling\nDS：约束检查/预约/方案"]
        C["change/notification/audit\nCM：改期/通知/审计"]
    end
    DB[("PostgreSQL 17")]
    FE -->|"HTTP /api/v1"| F
    F --> A
    F --> R
    F --> C
    A -->|"ActivityPort"| S
    A -->|"ActivityPortAdapter ← ChangePort/SchedulingPort.risk"| C
    C -->|"ChangePort、EventPort"| A
    C -->|"SchedulingPort.lockAndCheck/replace/release"| S
    R -->|"EventPort.append"| C
    A & R & S & C --> DB
```

## 2. 调度模块（scheduling）内部结构

SchedulingPort 实现只依赖 resource/scheduling 自己的 Repository，不依赖 ActivityService（防循环依赖）；预览 Controller 若需 ActivityPort，放在独立查询 Service，不注入 SchedulingPort 实现。

```mermaid
flowchart TB
    SC["ScheduleController<br/>活动预览/方案入口 API-24/25"]
    AS["ScheduleQueryService<br/>读活动 + 权限裁剪"]
    CE["ConstraintEvaluator（纯函数）<br/>evaluate(Plan, attendees, resourceFacts, reservations, faults)"]
    SW["IntervalSweep<br/>结束优先的事件扫描"]
    CG["CandidateGenerator<br/>偏移×场地×器材组合，≤300"]
    ALT["AlternativeSearch<br/>检查→去重→元组排序→前5"]
    SE["StrategyEvaluator<br/>FIRST_FIT vs RANKED（仅内部调用）"]
    SS["SchedulingService implements SchedulingPort<br/>lockAndCheck / replaceReservations / release / risk"]
    RR["ReservationRepository"]
    RS["ResourceRepository<br/>resource 表（行锁，UUID 升序）"]
    SC --> AS --> CE
    AS --> CG --> CE
    AS --> ALT
    ALT --> CG
    AS --> SE
    CE --> SW
    SS --> CE
    SS --> RR
    SS --> RS
```

## 3. 活动状态图（AN 主流程中与调度相关的边界）

```mermaid
stateDiagram-v2
    [*] --> DRAFT: APPLICANT 创建（版本1）
    DRAFT --> DRAFT: 编辑 → 新版本 n+1
    DRAFT --> PENDING_APPROVER: 提交（不占资源）
    PENDING_APPROVER --> APPROVED_UNRESERVED: APPROVER 同意
    PENDING_APPROVER --> DRAFT: 驳回（意见入档）
    PENDING_APPROVER --> DRAFT: 撤回 → 克隆 n+1
    APPROVED_UNRESERVED --> DRAFT: 撤回
    APPROVED_UNRESERVED --> SCHEDULED: 资源确认（SchedulingPort 整组预约，冲突则 409 保持原状态）
    SCHEDULED --> OPEN: 发布报名（无资源风险才允许）
    SCHEDULED --> CANCELLED: 取消（释放全部 ACTIVE 预约）
    OPEN --> CANCELLED: 取消
    CANCELLED --> [*]
```

## 4. 改期状态图（CM 驱动，调度端口在两处接入）

```mermaid
stateDiagram-v2
    [*] --> AWAITING_CONFIRMATIONS: 创建（无待确认人者直达 PENDING_APPROVER）
    AWAITING_CONFIRMATIONS --> REJECTED: 任一工作人员拒绝
    AWAITING_CONFIRMATIONS --> PENDING_APPROVER: 全部同意
    PENDING_APPROVER --> PENDING_RESOURCE: APPROVER 同意（绑定 changeId+targetPlan）
    PENDING_APPROVER --> REJECTED: APPROVER 驳回
    PENDING_RESOURCE --> APPLIED: 资源确认\n= lockAndCheck + activatePlan + replaceReservations 同一事务
    PENDING_RESOURCE --> CONFLICTED: lockAndCheck 不可行（409，原安排保留）
    [*] --> WITHDRAWN: 组织者撤销
    [*] --> CANCELLED_BY_ACTIVITY: 活动取消
```

> v3.0 资格规则：所有场地和设备按**活动所有者最新身份**校验（覆盖提交、候选、改期创建和最终确认）；SchedulingPort 各方法签名包含 `Identity applicantIdentity`。已生效预约保留，未确认申请及改期目标用最新资格（01 §2）。

## 5. 首次资源确认时序（成功与失败路径）

```mermaid
sequenceDiagram
    participant Admin as RESOURCE_ADMIN
    participant Cmd as CommandExecutor（幂等）
    participant Act as ActivityPort（AN）
    participant Sched as SchedulingPort（DS）
    participant DB as PostgreSQL

    Admin->>Cmd: resource-confirm(expectedLockVersion)
    Cmd->>DB: 占位 receipt（ON CONFLICT DO NOTHING）→ 锁行验证 hash
    Cmd->>Act: lock(activityId)
    Act->>DB: 锁活动行 + 校验状态/版本
    Act->>Sched: lockAndCheck(activityId, plan, attendees)
    Sched->>DB: 按 UUID 升序 FOR UPDATE 锁 resource 行
    Sched->>DB: 重读 ACTIVE 预约与故障（排除本活动旧预约）
    Alt 可行
        Sched-->>Act: CheckResult(feasible=true)
        Act->>Act: activatePlan → SCHEDULED + effectiveVersionNo
        Sched->>DB: 插入整组新 ACTIVE 预约
    Else 不可行
        Sched--xAct: CheckResult(conflicts[])
        Note over Cmd: 事务提交：状态保持 APPROVED_UNRESERVED，无任何占用
        Cmd-->>Admin: 409 {conflicts[]}
    End
    Cmd->>DB: 填 receipt 状态与响应 → COMMIT
```

## 6. 数据库设计

**由 designers 单独成篇**：见 [database-design.md](database-design.md)（17 表模型、约束与索引取舍）与 [migration-register.md](migration-register.md)（迁移版本登记）。本文档不再重复表定义。

## 7. 尚未覆盖的内容（后续 DS-02~05 补充）

- 资源/故障/日历（DS-02）的服务细节与 API-07—11 权限矩阵——待实现完成补写。
- 纯约束检查的类型与缺口报告格式（DS-03）——待 ConstraintEvaluator 定型补写。
- 替代方案搜索边界与策略比较结论（DS-04）——评测数据产生后补写实测结果。
- 设计取舍的"为什么"记录随 DS-05 交接一并列出。
