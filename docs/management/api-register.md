# 接口实现与调用登记

依据开发任务包 v3.0。2026-11-14 核查基准为主线 `750763b`。40 项接口的提供方和调用方如下。最新验收范围见[集成与评审记录](integration-review-2026-11-14-v1.0.md)。

| 编号 | 方法及路径 | 提供方 | 调用方 | 实现状态 |
|---|---|---|---|---|
| API-01 | POST /auth/login | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-02 | GET /auth/me | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-03 | POST /auth/logout | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-04 | GET /users | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-05 | POST /users | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-06 | PATCH /users/{id}/enabled | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-07 | GET /resources | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-08 | POST /resources | DS（编排）、PM（AccountPort） | UI、QA | 生产已实现；本轮补完整自动授权历史，共事务、重复与幂等重放已验证 |
| API-09 | GET /resources/calendar | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-10 | POST /resources/{id}/faults | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-11 | GET /resources/{id}/faults | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-12 | POST /activities | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-13 | GET /activities | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-14 | GET /activities/{id} | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-15 | PATCH /activities/{id} | AN | UI、QA | 交付候选已修复陈旧锁冲突详情，真实库回归通过 |
| API-16 | POST /activities/{id}/submit | AN | UI、QA | 交付候选已修复陈旧锁冲突详情，真实库回归通过 |
| API-17 | POST /activities/{id}/withdraw | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-18 | POST /activities/{id}/approver-decision | AN | UI、QA | #22 已合入，空意见或纯空白批准的后端与真实页面回归通过 |
| API-19 | POST /activities/{id}/resource-confirm | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-20 | POST /activities/{id}/publish | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-21 | POST /activities/{id}/cancel | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-22 | PUT /activities/{id}/registration | AN | 交付候选已修复陈旧有效版本详情，真实库回归通过 |
| API-23 | GET /activities/{id}/registrations | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-24 | POST /activities/{id}/schedule-preview | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-25 | POST /activities/{id}/alternatives | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-26 | POST /activities/{id}/change-preview | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-27 | POST /activities/{id}/changes | CM | UI、QA | #21 已合入，所有者自我确认和两种陈旧版本的详情已修复，最新 QA 组合通过 |
| API-28 | GET /activities/{id}/changes | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-29 | GET /changes/{id} | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-30 | POST /changes/{id}/confirmation | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-31 | POST /changes/{id}/approver-decision | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-32 | POST /changes/{id}/resource-decision | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-33 | POST /changes/{id}/withdraw | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-34 | GET /notifications | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-35 | POST /notifications/{id}/read | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-36 | GET /activities/{id}/audit | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-37 | GET /health | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-38 | GET /organizations | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-39 | PATCH /users/{id}/authorization | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-40 | PATCH /resources/{id}/eligibility | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |

2026-10-31核查：40项契约中29项有主线Controller实现，CM的API-26～36共11项未交付。AccountPort、ActivityPort、SchedulingPort已接收代码并实施模块检查；ChangePort/EventPort仅有定义，未收到生产适配器。具体通过范围、503拒绝与回滚证据见`integration-status-2026-10-31.md`，不将测试替身计为生产实现。

## 历史：2026-10-31候选调用方接收

UI PR #17来自ychangz1的fork，已有40项API客户端、11类页面、锁文件和测试；本轮独立验证类型/78项unit/构建及5项真实局部浏览器、1项重启持久化通过。QA PR #16的装载修复、类型和160行策略评测亦已限定接收。两项均未合入main，不能将API26–36客户端存在等同CM接口实现。具体访问与字段边界见[协调方案](access-coordination-v1.0.md)。

## 2026-11-02 接收与审核

AN #13、PM #14、DS #15、CM #18、UI #17均已合并，40项Controller/5类生产端口已接收；旧表述中的CM/UI缺失不再适用。QA #16原head已独立复验并正式Request Changes（两项基线既有断言需修订），未合入。PM全生产端口限定链、Node22 UI及真实浏览器检查见[2026-11-02集成与正式审核](integration-review-2026-11-02-v1.0.md)，不能据API实现登记推断全产品通过。

## 2026-11-07 公共接入与调用方核对

本轮不改变40项API、共享DTO、枚举、端口或迁移。统一JSON绑定现在拒绝整数小数截断及数字枚举，正常具名输入兼容。AccountPort自动新增资源范围保存完整授权前后历史，调用方仍需先锁创建者并保持同一事务。PM指定96单元＋43 IT通过，覆盖真实创建至改期／通知／取消；默认全量另有初始化错误。

最新QA #16 `6596a7d` 已正确校验API-27授权冲突的当前状态详情，CM实现仍空；AN API-18合法空意见仍400。UI客户端、后端正常输入的真实局部5项与重启1项通过；QA全浏览器25／29通过，另两项AN前置和两项定位不匹配明确交接。API登记表示实现者与调用者，完整结果及接收门槛见[本轮报告](integration-review-2026-11-07-v1.0.md)。
