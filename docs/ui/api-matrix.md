# UI 接口接入矩阵 · v0.1

负责人：杨畅（17231018）。原始联调基准：main=feb0d70；提交前已同步main=c4f3d41；分支 work/17231018-ui。最新复验证据见handoff顶部，以下服务缺口证据保留原始请求基准。

统一字段依据 common/02 与 contracts/openapi.yaml。40项类型化调用均已落在 web/src/api/index.ts；下列“页面已接调用”只说明前端代码，不表示真实服务联调通过。本轮已运行真实Java21/PostgreSQL17局部联调；下表最后一列是实际结果，未覆盖的场景保持未验收。证据与复现入口见[真实联调记录](real-integration-2026-10-31.md)。

OpenAPI 的 AN/DS 实现状态文字仍有“待交付”，而 main 已存在对应 Controller。本表以源码和各方 handoff 区分实现与运行依赖，不擅改公共OpenAPI。CM 的 change/notification/audit 生产包在本基线不存在。

| API | 方法与路径 | 后端负责人 | 当前前端接入 | 真实服务依赖 | 本轮真实结果 |
|---|---|---|---|---|---|
| API-01 | POST /auth/login | PM | 登录表单 | PM认证/账号服务及真实数据库 | 真实登录通过 |
| API-02 | GET /auth/me | PM | 路由会话恢复 | PM认证/账号服务及真实数据库 | 刷新会话通过 |
| API-03 | POST /auth/logout | PM | 退出 | PM认证/账号服务及真实数据库 | 真实退出通过 |
| API-04 | GET /users | PM | 工作人员查询；账号列表与筛选 | PM认证/账号服务及真实数据库 | 账号/工作人员查询通过；普通申请人查完整列表403 |
| API-05 | POST /users | PM | 账号创建表单与完整授权 | PM认证/账号服务及真实数据库 | 真实创建通过 |
| API-06 | PATCH /users/{id}/enabled | PM | 启用/停用二次确认 | PM认证/账号服务及真实数据库 | 停用/刷新/重启通过；旧会话撤销与重新启用未覆盖 |
| API-07 | GET /resources | DS | 活动创建/详情/日历资源查询 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort | 真实资源查询通过 |
| API-08 | POST /resources | DS | 新增资源表单；成功后重新读取管理范围 | DS资源/调度；API-10需CM EventPort | 真实创建通过 |
| API-09 | GET /resources/calendar | DS | 30分钟刻度、设备同时用量/故障扣减；改期生效后刷新 | DS资源/调度 | 真实日历查询及3视口截图通过；故障闭环未覆盖 |
| API-10 | POST /resources/{id}/faults | DS | 范围内故障登记，原因及数量校验 | DS资源/调度与CM EventPort | 真实请求503：资源故障事件服务尚未就绪 |
| API-11 | GET /resources/{id}/faults | DS | 按资源/日期查询故障及可见受影响活动 | DS资源/调度 | 真实空列表查询通过；有故障场景未覆盖 |
| API-12 | POST /activities | AN | 创建页保存草稿 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 真实分步草稿创建通过 |
| API-13 | GET /activities | AN | 活动列表及review待办列表 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 公开列表真实503：改期端口尚未就绪 |
| API-14 | GET /activities/{id} | AN | 活动详情刷新 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 草稿详情/刷新/重启通过；已预约详情未通过 |
| API-15 | PATCH /activities/{id} | AN | 详情内编辑草稿；409保留输入、等待最新lockVersion | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 真实编辑与lockVersion恢复通过 |
| API-16 | POST /activities/{id}/submit | AN | 草稿提交 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 真实503：活动事件端口尚未就绪 |
| API-17 | POST /activities/{id}/withdraw | AN | 撤回新草稿 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-18 | POST /activities/{id}/approver-decision | AN | 批准/驳回及范围门禁 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-19 | POST /activities/{id}/resource-confirm | AN | 资源联合确认入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-20 | POST /activities/{id}/publish | AN | 发布入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-21 | POST /activities/{id}/cancel | AN | 取消确认入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-22 | PUT /activities/{id}/registration | AN | 学生报名/退出入口 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-23 | GET /activities/{id}/registrations | AN | 按权限显示报名名单页签；不猜姓名字段 | AN活动服务；事件与已预约视图依赖CM EventPort/ChangePort | 未执行完整真实流程；等待业务依赖 |
| API-24 | POST /activities/{id}/schedule-preview | DS | 保存草稿后的资源检查 | DS资源/调度；API-10需CM EventPort，24/25需AN ActivityPort | 草稿预览可行及资格403提示通过 |
| API-25 | POST /activities/{id}/alternatives | DS | 草稿冲突后的候选比较；填入编辑器，需保存再预览 | DS资源/调度及AN ActivityPort | 未执行完整真实流程；等待业务依赖 |
| API-26 | POST /activities/{id}/change-preview | CM | 改期工作台；选择候选后对最终目标重新预览 | CM真实改期/通知/审计实现，当前main缺生产包 | 真实404 NOT_FOUND |
| API-27 | POST /activities/{id}/changes | CM | 核对影响与原因后创建，携带baseVersionNo/expectedLockVersion | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-28 | GET /activities/{id}/changes | CM | 活动记录页签；review待办聚合最多6并发，独立提示失败/截断 | CM真实改期/通知/审计实现，当前main缺生产包 | 真实404 NOT_FOUND |
| API-29 | GET /changes/{id} | CM | 改期详情、时间线与状态刷新 | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-30 | POST /changes/{id}/confirmation | CM | 指定工作人员本人确认/拒绝 | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-31 | POST /changes/{id}/approver-decision | CM | 按目标资源类别、组织范围复审，所有者回避 | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-32 | POST /changes/{id}/resource-decision | CM | 目标全部资源范围复核，409保留原安排和冲突明细 | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-33 | POST /changes/{id}/withdraw | CM | 所有者填写原因、二次确认后撤回 | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-34 | GET /notifications | CM | 通知列表/未读筛选 | CM真实改期/通知/审计实现，当前main缺生产包 | 真实404 NOT_FOUND |
| API-35 | POST /notifications/{id}/read | CM | 标记已读 | CM真实改期/通知/审计实现，当前main缺生产包 | 未执行完整真实流程；等待业务依赖 |
| API-36 | GET /activities/{id}/audit | CM | 按权限显示历史记录；操作者/版本/时间/摘要 | CM真实改期/通知/审计实现，当前main缺生产包 | 真实404 NOT_FOUND |
| API-37 | GET /health | PM | 类型化诊断调用；不属于业务操作页面 | PM认证/账号服务及真实数据库 | 真实health通过 |
| API-38 | GET /organizations | PM | 组织选择及审批范围 | PM认证/账号服务及真实数据库 | 真实组织查询通过 |
| API-39 | PATCH /users/{id}/authorization | PM | 完整授权替换、本人权限刷新 | PM认证/账号服务及真实数据库 | 完整授权体保存身份修改/刷新/重启通过；其他组合待验收 |
| API-40 | PATCH /resources/{id}/eligibility | DS | 两项独立开关及确认；四组合均允许，解释已生效预约保留 | DS资源/调度 | 真实开关修改及重启通过；生效预约资格保留未覆盖 |

## 后续接入顺序

1. 本机真实服务与隔离库已启动；CM仍需交Compose发布环境和缺失生产端口，QA需修复数据装载。
2. UI-02登录、创建、身份授权修改、停用已真实通过；旧会话失效和全部授权组合仍待QA完整验证。
3. 已实现活动编辑、名单、首次预约/报名及改期待办页面；待真实验证资格变化、409、满额、冻结、同key重试和刷新持久化。
4. 已实现日历同时用量、故障扣减、候选对比及API-08/10/11/40管理页面；待DS真实日历与资格验证。
5. 已实现CM改期预览/影响/创建/本人确认/复审/最终资源确认/审计/通知调用；待CM交付可运行的生产服务。
6. QA在真实后端和数据库上执行D-AT/N-AT与E2E，UI修复交互和3视口问题，更新实际截图与手册。

## QA 稳定标识

当前真实页面：`login-username`、`login-password`、`login-submit`、`activity-create`、`activity-title`、`activity-start`、`activity-end`、`activity-venue`、`activity-save`、`activity-list`、`activity-submit`、`approver-approve`、`resource-confirm`、`activity-publish`、`registration-submit`、`current-version`、`conflict-list`、`resource-calendar`、`notification-item`、`api-error`、`page-pending`。

UI-03～05新增正式标识：`draft-edit`、`draft-editor`、`draft-title`、`draft-save`、`activity-records`、`review-change`、`resource-management`、`resource-new`、`resource-name`、`resource-create`、`resource-select-{id}`、`eligibility-teacher`、`eligibility-student`、`eligibility-save`、`fault-create`、`fault-item`、`equipment-usage`、`capacity-details`、`alternative-0`（至4）、`change-preview`、`change-impact`、`change-reason`、`change-create`、`change-status`、`change-refresh`、`change-actions`、`staff-approve`、`change-resource-approve`、`change-withdraw`、`notification-read`、`mutation-retry`。

生产页面已使用上述标识。开发预览复用部分组件，但禁止以预览截图或单元测试替身替代真实改期验收。`approver-approve`同时用于首次审批与改期复审，QA应按所在路由定位。

UI-02新增标识：`users-list`、`users-search`、`user-create-open`、`user-authorize-{id}`、`user-toggle-{id}`、`user-editor`、`user-username`、`user-display-name`、`user-password`、`user-organization`、`authorization-identity`、`role-{ROLE}`、`permission-{PERMISSION}`、`scope-add`、`managed-resources`、`authorization-confirm`、`authorization-summary`、`user-save`、`user-save-result`。

本轮新增扩展标识：`login-error`、`change-entry`、`change-start`、`change-end`、`change-reject`、`reject-reason`、`reject-confirm`、`calendar-nav`、`registration-list`、`risk-banner`、`eligibility-error`、`alert-generic`。拒绝使用弹窗填写原因；资格错误解析服务端resourceIds，以已加载资源名解释，不扩展DTO。QA分步创建/Element Plus选择器/报名名单权限等脚本适配说明见真实联调记录，未改QA工程。
