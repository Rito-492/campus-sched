# 接口实现与调用登记

依据开发任务包v3.0；核查日期2026-10-19。契约声明与真实实现分开登记。接收反馈均为尚未接收。

| 编号 | 方法及路径 | 提供方 | 调用方 | 实现状态 |
|---|---|---|---|---|
| API-01 | POST /auth/login | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-02 | GET /auth/me | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-03 | POST /auth/logout | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-04 | GET /users | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-05 | POST /users | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-06 | PATCH /users/{id}/enabled | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-07 | GET /resources | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-08 | POST /resources | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-09 | GET /resources/calendar | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-10 | POST /resources/{id}/faults | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-11 | GET /resources/{id}/faults | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-12 | POST /activities | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-13 | GET /activities | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-14 | GET /activities/{id} | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-15 | PATCH /activities/{id} | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-16 | POST /activities/{id}/submit | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-17 | POST /activities/{id}/withdraw | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-18 | POST /activities/{id}/approver-decision | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-19 | POST /activities/{id}/resource-confirm | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-20 | POST /activities/{id}/publish | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-21 | POST /activities/{id}/cancel | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-22 | PUT /activities/{id}/registration | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-23 | GET /activities/{id}/registrations | AN | UI、QA | 待对应负责人交付真实Controller/Service |
| API-24 | POST /activities/{id}/schedule-preview | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-25 | POST /activities/{id}/alternatives | DS | UI、QA | 待对应负责人交付真实Controller/Service |
| API-26 | POST /activities/{id}/change-preview | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-27 | POST /activities/{id}/changes | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-28 | GET /activities/{id}/changes | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-29 | GET /changes/{id} | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-30 | POST /changes/{id}/confirmation | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-31 | POST /changes/{id}/approver-decision | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-32 | POST /changes/{id}/resource-decision | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-33 | POST /changes/{id}/withdraw | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-34 | GET /notifications | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-35 | POST /notifications/{id}/read | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-36 | GET /activities/{id}/audit | CM | UI、QA | 待对应负责人交付真实Controller/Service |
| API-37 | GET /health | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-38 | GET /organizations | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-39 | PATCH /users/{id}/authorization | PM | UI、QA、AN/DS/CM（AccountPort） | 本轮实现，验证见handoff |
| API-40 | PATCH /resources/{id}/eligibility | DS | UI、QA | 待对应负责人交付真实Controller/Service |
