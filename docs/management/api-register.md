# 接口实现与调用登记

依据开发任务包v3.0；核查日期2026-11-02；主线b8be7e4。契约声明、候选交付与生产接收分开登记；PM限定范围独立复验见[正式审核记录](integration-review-2026-11-02-v1.0.md)，不代替其他成员确认或全链路验收。

| 编号 | 方法及路径 | 提供方 | 调用方 | 实现状态 |
|---|---|---|---|---|
| API-01 | POST /auth/login | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-02 | GET /auth/me | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-03 | POST /auth/logout | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-04 | GET /users | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-05 | POST /users | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-06 | PATCH /users/{id}/enabled | PM | UI、QA、AN/DS/CM（AccountPort） | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-07 | GET /resources | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-08 | POST /resources | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-09 | GET /resources/calendar | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-10 | POST /resources/{id}/faults | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-11 | GET /resources/{id}/faults | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-12 | POST /activities | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-13 | GET /activities | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-14 | GET /activities/{id} | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-15 | PATCH /activities/{id} | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-16 | POST /activities/{id}/submit | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-17 | POST /activities/{id}/withdraw | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-18 | POST /activities/{id}/approver-decision | AN | UI、QA | 已有生产实现；合法空APPROVE意见400待AN修复 |
| API-19 | POST /activities/{id}/resource-confirm | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-20 | POST /activities/{id}/publish | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-21 | POST /activities/{id}/cancel | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-22 | PUT /activities/{id}/registration | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-23 | GET /activities/{id}/registrations | AN | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-24 | POST /activities/{id}/schedule-preview | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-25 | POST /activities/{id}/alternatives | DS | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-26 | POST /activities/{id}/change-preview | CM | UI、QA | 已有生产实现；限定验证与遗留验收见2026-11-02审核 |
| API-27 | POST /activities/{id}/changes | CM | UI、QA | 已有生产实现；所有者staff自我确认缺陷待CM修复 |
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
