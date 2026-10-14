# data-testid 依赖清单（QA → UI）v0.1

> 归属：测试经理（55230316）维护；**界面设计师（yc 17231018）实现时逐项提供**。05 界面规范 §4 的 12 个公共 testid 是契约级要求，本文档补充 E2E 实现所需的扩展项。
> 用途：`qa/tests/` 的 7 个 spec（29 tests）已用这些标识写好断言；缺标识时暂以语义角色兜底（代码内已注释标记），补齐后改为 getByTestId 稳定定位。

## 一、05 界面规范 §4 公共清单（契约级，必须逐字提供）

| testid | 所在元素 | 依赖它的 spec |
|---|---|---|
| `login-submit` | 登录按钮 | login、ui-layout、helpers |
| `activity-submit` | 新建活动提交按钮 | booking、helpers |
| `resource-confirm` | 资源确认按钮 | booking、change-conflict |
| `change-preview` | 改期预览按钮 | change-happy-path、change-conflict |
| `alternative-0` | 候选方案列表第 1 项 | change-happy-path、change-conflict |
| `change-create` | 创建改期按钮 | change-happy-path |
| `staff-approve` | 工作人员确认按钮 | change-happy-path |
| `approver-approve` | 批准人审批按钮（v3.0，原 teacher-approve） | change-happy-path、booking |
| `change-resource-approve` | 改期最终资源确认按钮 | change-happy-path、change-conflict |
| `change-status` | 改期状态展示区 | change-happy-path、change-conflict |
| `current-version` | 当前版本号展示 | change-happy-path、change-conflict、ui-layout |
| `conflict-list` | 冲突列表区 | change-happy-path、change-conflict |

## 二、扩展项（E2E 已用语义兜底，待补 testid）

| 建议 testid | 所在元素 | 依赖它的 spec | 备注 |
|---|---|---|---|
| `login-username` / `login-password` | 登录表单两输入框 | login、全部（经 helpers.login） | 现用 getByLabel 兜底 |
| `login-error` | 登录错误提示区（role=alert） | login | 断言错误文案 |
| `change-entry` | 活动详情「发起改期」入口 | change-happy-path、change-conflict | |
| `change-start` / `change-end` | 改期表单开始/结束时间字段 | change-happy-path、conflict、eligibility | |
| `change-reject` / `reject-reason` | 拒绝按钮与理由输入（dialog） | change-conflict | |
| `calendar-nav` | 日历日期导航 | permissions | |
| `registration-list` | 报名名单区 | permissions | 裁剪可见性断言 |
| `risk-banner` | 资源风险提示文本 | change-conflict（N-AT 故障段） | AT_RISK 展示 |
| `eligibility-error` | 资格受限错误区（含受限资源名） | eligibility | **N-AT-02 要求 UI 说明具体受限资源** |
| `alert-generic` | 通用错误横幅（role=alert） | ui-layout | 溢出/错误检查 |

## 三、约定

- 命名小写-短横线；同页同名唯一；测试不依赖元素位置/顺序（QA-03 红线）。
- UI 落实后通知测试经理，spec 中语义兜底统一切换为 getByTestId 并删除兜底注释。
- 验收方式：`npm --prefix qa ci && npm --prefix qa run test:e2e`（真实服务），缺 testid 导致的失败会以「找不到元素」形式暴露。
