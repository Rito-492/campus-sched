# UI 接口接入矩阵 · v0.1

负责人：杨畅（17231018）。基准：main=feb0d70；分支 work/17231018-ui。

统一字段依据 common/02 与 contracts/openapi.yaml。40项类型化调用均已落在 web/src/api/index.ts；下列“页面已接调用”只说明前端代码，不表示真实服务联调通过。所有行的真实联调状态均为**未执行**。

OpenAPI 的 AN/DS 实现状态文字仍有“待交付”，而 main 已存在对应 Controller。本表以源码和各方 handoff 区分实现与运行依赖，不擅改公共OpenAPI。CM 的 change/notification/audit 生产包在本基线不存在。

| API | 方法与路径 | 后端负责人 | 当前前端接入 | 真实服务依赖 |
|---|---|---|---|---|
| API-01 | POST /auth/login | PM | 登录表单 | PM认证/账号服务及真实数据库 |
| API-02 | GET /auth/me | PM | 路由会话恢复 | PM认证/账号服务及真实数据库 |
| API-03 | POST /auth/logout | PM | 退出 | PM认证/账号服务及真实数据库 |
| API-04 | GET /users | PM | 创建页工作人员查询；账号列表仅客户端 | PM认证/账号服务及真实数据库 |
| API-05 | POST /users | PM | 仅类型化客户端；操作界面待实现 | PM认证/账号服务及真实数据库 |
| API-06 | PATCH /users/{id}/enabled | PM | 仅类型化客户端；操作界面待实现 | PM认证/账号服务及真实数据库 |
| API-07 | GET /resources | DS | 活动创建/详情/日历资源查询 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-08 | POST /resources | DS | 仅类型化客户端；操作界面待实现 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-09 | GET /resources/calendar | DS | 日历分段及数量明细 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-10 | POST /resources/{id}/faults | DS | 仅类型化客户端；操作界面待实现 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-11 | GET /resources/{id}/faults | DS | 仅类型化客户端；操作界面待实现 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-12 | POST /activities | AN | 创建页保存草稿 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-13 | GET /activities | AN | 活动列表及review待办列表 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-14 | GET /activities/{id} | AN | 活动详情刷新 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-15 | PATCH /activities/{id} | AN | 仅类型化客户端；操作界面待实现 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-16 | POST /activities/{id}/submit | AN | 草稿提交 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-17 | POST /activities/{id}/withdraw | AN | 撤回新草稿 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-18 | POST /activities/{id}/approver-decision | AN | 批准/驳回及范围门禁 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-19 | POST /activities/{id}/resource-confirm | AN | 资源联合确认入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-20 | POST /activities/{id}/publish | AN | 发布入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-21 | POST /activities/{id}/cancel | AN | 取消确认入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-22 | PUT /activities/{id}/registration | AN | 学生报名/退出入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-23 | GET /activities/{id}/registrations | AN | 仅类型化客户端；操作界面待实现 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort |
| API-24 | POST /activities/{id}/schedule-preview | DS | 保存草稿后的资源检查 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-25 | POST /activities/{id}/alternatives | DS | 仅类型化客户端；操作界面待实现 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |
| API-26 | POST /activities/{id}/change-preview | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-27 | POST /activities/{id}/changes | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-28 | GET /activities/{id}/changes | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-29 | GET /changes/{id} | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-30 | POST /changes/{id}/confirmation | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-31 | POST /changes/{id}/approver-decision | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-32 | POST /changes/{id}/resource-decision | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-33 | POST /changes/{id}/withdraw | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-34 | GET /notifications | CM | 通知列表/未读筛选 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-35 | POST /notifications/{id}/read | CM | 标记已读 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-36 | GET /activities/{id}/audit | CM | 仅类型化客户端；操作界面待实现 | CM真实改期/通知/审计实现，当前main缺生产包 |
| API-37 | GET /health | PM | 仅类型化客户端；操作界面待实现 | PM认证/账号服务及真实数据库 |
| API-38 | GET /organizations | PM | 仅类型化客户端；操作界面待实现 | PM认证/账号服务及真实数据库 |
| API-39 | PATCH /users/{id}/authorization | PM | 仅类型化客户端；操作界面待实现 | PM认证/账号服务及真实数据库 |
| API-40 | PATCH /resources/{id}/eligibility | DS | 仅类型化客户端；操作界面待实现 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort |

## 后续接入顺序

1. PM/CM/QA提供可启动的真实服务、隔离测试数据、账号与环境；不把fixture或单元测试替身用在联调。
2. 登录/me/退出、权限细分；API-05/06/38/39账号管理页面。
3. 草稿编辑、首次审批/联合预约/发布/报名；补资格变化、409重载、满额、冻结、同key重试与刷新持久化验证。待办的改期聚合拉取尚未实现，后续并发不超过6。
4. DS日历器材同时用量/故障可用量、具体受限资源提示、候选对比；API-08/10/11/40资源管理。
5. CM改期预览/影响/创建/本人确认/复审/最终资源确认/审计，通知真实接通。
6. QA在真实后端和数据库上执行D-AT/N-AT与E2E，UI修复交互和3视口问题，更新实际截图与手册。

## QA 稳定标识

当前真实页面：`login-username`、`login-password`、`login-submit`、`activity-create`、`activity-title`、`activity-start`、`activity-end`、`activity-venue`、`activity-save`、`activity-list`、`activity-submit`、`approver-approve`、`resource-confirm`、`activity-publish`、`registration-submit`、`current-version`、`conflict-list`、`resource-calendar`、`notification-item`、`api-error`、`page-pending`。

开发组件：`alternative-0`（及后续索引）、`change-status`。这些原型标识不表示生产改期流程可用。

尚未生产实现：`change-preview`、`change-create`、`staff-approve`、`change-resource-approve`；禁止QA以开发原型替代真实改期验收。
