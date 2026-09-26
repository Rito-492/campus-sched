# 需求追踪矩阵

版本：v1.0（依据开发任务包 v2.0；2026-09-26）

状态基于当前 `work/22220314-activity` 分支实际内容。分支仅包含任务包与课程/配置文档，未发现 `server/`、`qa/`、`scripts/` 或 `artifacts/`；因此代码、测试和验收证据均标记为**未提供/未执行**，不表示测试失败或通过。

| 需求 | 用例 | API/接口 | 预期实现/验证 | D-AT | 当前证据状态 |
|---|---|---|---|---|---|
| FR-ACT-01/02，BR-VERSION-01/02 | UC-ACT-01 | API-12 创建、API-15 编辑；ActivityRepository | ActivityLifecycleIT；ActivityStateMachineTest | 03、20 | 需求/用例已编写；代码与测试未提供 |
| FR-ACT-03/04，BR-VERSION-03 | UC-ACT-02 | API-16 提交、API-18 教师决定 | ActivityLifecycleIT：版本绑定、同组织、不得自批 | 02、03、20 | 需求/用例已编写；代码与测试未提供 |
| FR-ACT-05，BR-VERSION-04 | UC-ACT-03 | API-17 撤回 | ActivityStateMachineTest、ActivityLifecycleIT：生成新版本、旧审批失效 | 03、20 | 需求/用例已编写；代码与测试未提供 |
| FR-ACT-06，BR-RESERVATION-01—04 | UC-ACT-04 | API-19 资源确认；SchedulingPort | ActivitySchedulingIT：完整事务、冲突零部分预约、资源竞争 | 03、07 | 需求/用例已编写；代码与测试未提供 |
| FR-ACT-07，BR-PLAN-05 | UC-ACT-05 | API-20 开放 | ActivitySchedulingIT：未开始、NORMAL 风险、正确状态 | 03、18、20 | 需求/用例已编写；代码与测试未提供 |
| FR-ACT-08，BR-CANCEL | UC-ACT-06 | API-21 取消；ChangePort、SchedulingPort、EventPort | ActivityCancellationIT：释放、幂等、取消与改期竞争 | 17 | 需求/用例已编写；代码与测试未提供 |
| FR-REG-01—03，BR-REGISTRATION-01—05 | UC-REG-01 | API-22 报名/退出 | RegistrationConcurrencyIT：20 并发、容量、幂等、改期冻结 | 15、16、20 | 需求/用例已编写；代码与测试未提供 |
| FR-REG-04、FR-READ-01/02，BR-AUTH | UC-ACT-07 | API-13 我的/公开/审批列表、API-14 详情、API-23 名单 | ActivityLifecycleIT：过滤、详情裁剪、名单授权 | 02、21 | 需求/用例已编写；代码与测试未提供 |
| FR-CHG-01—05，BR-CHANGE | 改期交互用例边界 | ActivityPort；ChangePort；EventPort | CM/DS 联调测试；ActivityPortAdapter 测试 | 09—15、17、20 | 契约边界已记录；实现及联调未提供 |
| FR-AUD-01，BR-EVENT | UC-ACT-02/04/05/06 | EventPort | 事件持久化及事务测试 | 03、10、14、17、19 | 需求已记录；实现及运行证据未提供 |
| BR-HTTP-01—05，BR-AUTH | 全部写用例 | CommandExecutor 与统一 API envelope | API/权限/幂等集成验证 | 02、08、20、21 | 依赖 PM 公共基础；当前分支未提供 |

## 需求到实现的当前差距

- AN-01 文档：本分支已提供 `software-requirements.md`、`use-cases.md`、`business-rules.md`、`traceability.md`。
- AN-01 代码和纯状态测试：`server/`、公共契约 Java 类型及 Maven Wrapper 均未出现在本分支，不能按统一工程真实实现或执行。
- AN-02—AN-04：控制器、服务、repository、真实 PostgreSQL 集成测试均未提供；依赖项目经理的 foundation/contract、设计师 schema、测试经理 DbTestSupport、设计师 SchedulingPort、配置经理运行环境及配置经理 ChangePort/EventPort。
- AN-05：没有实际测试命令入口或运行产物；所有相关 D-AT 当前状态均为未执行。待共享基础并入后更新本矩阵和交接单。
