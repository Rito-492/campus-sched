# UI 接口接入矩阵 · v0.1

负责人：杨畅（17231018）。当前基准 main=107a301，分支 work/17231018-ui；2026-11-01已完成本地安全同步与评审修复，尚未推送更新PR。历史feb0d70/c4f3d41证据保留。

统一字段依据 common/02 与 contracts/openapi.yaml。40项类型化调用均已落在 web/src/api/index.ts；“页面已接调用”不等于真实验收通过。最新Node22/Java21/PostgreSQL17复验证据见[两项P2修复与复验](review-fixes-2026-11-01.md)；前轮记录保留。

PR #18 已合并，CM 生产服务已纳入最新基线，以下新增验证替代相同接口的历史缺口结论，未验证部分继续标为待验收。前端不修改公共OpenAPI或后端。

## 最新评审修复增量（main=107a301，2026-11-01）

本轮11项真实浏览器检查通过（原有5项、评审回归6项），未重新执行重启或JDK21 verify。两项新增修复如下，未扩大后端权限或修改共享DTO：

| API | 本轮新增真实验证 |
|---|---|
| 07、40 | 独立资源从允许教师/学生改为均不允许后，页面手动刷新同步两个开关；继续保存的真实PATCH请求体、响应和再次读取均为false/false，不回写旧值 |
| 14、28、29 | 普通工作人员的API-14人员数组为空，仍能点击改期记录，API-28返回200并进入API-29详情；覆盖待确认、APPLIED、REJECTED、WITHDRAWN；无权查看者得到真实403且不展示成空列表 |

稳定标识新增 `change-history` 与 `change-record-<id>`。历史页签首次点击才查询；未将被裁剪的人员数组解释为缺少历史读取权限。资源显式刷新成功会用服务端最新资格替换表单输入。

## 前轮严格自查增量（main=107a301，2026-11-01，历史记录）

10项真实浏览器检查和2项实际重启检查通过，以下仅表示用例覆盖的具体场景；全量QA E2E、完整D-AT/N-AT和Compose仍未验收。JDK21全量verify已执行但因8项QA数据外键错误、1项EventPort重复Bean失败，详见新报告。

| API | 本轮新增真实验证 |
|---|---|
| 22、29—33 | 工作人员拒绝及所有者撤回后解除报名冻结；原版本/安排/真实日历占用不变 |
| 19、32 | 另一真实预约抢占改期目标后，最终资源确认409、状态CONFLICTED，原预约保持 |
| 09、10、14 | 真实故障登记产生AT_RISK；原安排与日历数量保留 |
| 34、35 | 通知标为已读后工作人员仍待确认，通知不代签 |
| 22 | 已落库的真实报名响应被测试丢弃一次后，原key/body重试成功且人数仍为1 |

活动/改期详情及工作台的401/403/404清除、迟到响应隔离和旧快照说明另有单测；错误注入不冒充真实端点验收。新增稳定标识：`change-workspace-stale`、`change-detail-stale`。API-18空意见缺陷仍未修复；全权限组合、完整取消/候选/审计等矩阵仍待验收。

## 前轮复验增量（main=107a301，2026-11-01，历史记录）

| API | 当前真实结果 / 页面行为 |
|---|---|
| 01—08、12、15、24、37—40 | 原有5项真实检查在最新main复跑通过；仅覆盖测试列出的认证/账号/资源/草稿/资格输入，不代表全部权限组合 |
| 04 | 纯APPROVER、仅RESOURCE_CONFIRM、跨组织兼任账号查看人员时不请求候选接口，展示已授权ID；同组织申请人仍补充姓名 |
| 14、39 | 撤销跨组织批准范围后，详情刷新403并清除旧实体及关联记录；成功改期后的版本/计划已验证刷新与重启 |
| 16、18—20、22 | 正式页面完成提交、填写意见批准、联合预约、发布、报名/退出/再报名；API-18空批准意见仍400“事件字段不完整”，已交接AN/CM |
| 23 | 重启后真实读取报名列表，1条ACTIVE且versionNo对应改期生效版本；并不表示全部名单权限已验收 |
| 26、27、29—32 | 正式改期预览、创建、本人确认、纯批准人复审、最小资源范围确认和APPLIED通过；中间状态报名退出被冻结 |
| 09、34 | 生效后日历/通知刷新及重启读取通过；日历包含实际目标时段，通知关联活动与版本 |
| 10、11、13、17、21、25、28、33、35、36 | 本轮未做这些接口的完整真实验收；服务实现已合入，不继续沿用旧503/404结论 |

当时未验的冲突保护、拒绝/撤回解冻、故障风险、同key未知结果重试已按顶部具体用例补验；全权限矩阵和完整QA验收仍待完成。前轮稳定标识：`activity-refresh`、`activity-stale`、`activity-staff-{id}`、`staff-name-unavailable`。

## 历史完整接口矩阵（2026-10-31，非当前阻塞结论）

下表保留首轮feb0d70请求实录和当时依赖，当前状态以以上增量和新报告为准。表内“当前main/本轮”均指历史运行时点。

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

## 后续验证顺序（2026-11-01更新）

1. AN/CM接收并修复合法空批准意见的事件校验问题，UI补跑空意见成功路径。
2. QA在最新main接收本轮角色/权限撤销/成功主线/重启证据，补全真实异常分支及完整D-AT/N-AT。
3. UI配合冲突候选、改期失败保护、拒绝/撤回、故障风险与未知结果重试的真实交互复验。
4. CM/QA补Compose干净环境及最新Java21 verify，本轮不把旧数据装载问题当作最新失败。
5. 用户确认后更新PR #17；交叉复审决定是否放行，不自审或自动合并。

## QA 稳定标识

当前真实页面：`login-username`、`login-password`、`login-submit`、`activity-create`、`activity-title`、`activity-start`、`activity-end`、`activity-venue`、`activity-save`、`activity-list`、`activity-submit`、`approver-approve`、`resource-confirm`、`activity-publish`、`registration-submit`、`current-version`、`conflict-list`、`resource-calendar`、`notification-item`、`api-error`、`page-pending`。

UI-03～05新增正式标识：`draft-edit`、`draft-editor`、`draft-title`、`draft-save`、`activity-records`、`review-change`、`resource-management`、`resource-new`、`resource-name`、`resource-create`、`resource-select-{id}`、`eligibility-teacher`、`eligibility-student`、`eligibility-save`、`fault-create`、`fault-item`、`equipment-usage`、`capacity-details`、`alternative-0`（至4）、`change-preview`、`change-impact`、`change-reason`、`change-create`、`change-status`、`change-refresh`、`change-actions`、`staff-approve`、`change-resource-approve`、`change-withdraw`、`notification-read`、`mutation-retry`。

生产页面已使用上述标识。开发预览复用部分组件，但禁止以预览截图或单元测试替身替代真实改期验收。`approver-approve`同时用于首次审批与改期复审，QA应按所在路由定位。

UI-02新增标识：`users-list`、`users-search`、`user-create-open`、`user-authorize-{id}`、`user-toggle-{id}`、`user-editor`、`user-username`、`user-display-name`、`user-password`、`user-organization`、`authorization-identity`、`role-{ROLE}`、`permission-{PERMISSION}`、`scope-add`、`managed-resources`、`authorization-confirm`、`authorization-summary`、`user-save`、`user-save-result`。

本轮新增扩展标识：`login-error`、`change-entry`、`change-start`、`change-end`、`change-reject`、`reject-reason`、`reject-confirm`、`calendar-nav`、`registration-list`、`risk-banner`、`eligibility-error`、`alert-generic`。拒绝使用弹窗填写原因；资格错误解析服务端resourceIds，以已加载资源名解释，不扩展DTO。QA分步创建/Element Plus选择器/报名名单权限等脚本适配说明见真实联调记录，未改QA工程。
