# PR提交说明（草稿评审，不自动合并）

建议标题：`feat: 交付UI前端工程及活动、资源和改期页面`

main=feb0d70中的web目录只有容器入口，缺少可构建的产品前端。本变更提供Vue3/TypeScript/Router/Pinia/Element Plus/Vitest工程、锁文件、11个产品路由、统一API客户端与公共DTO消费。

已真实验证登录与Actor权限导航、账号创建/授权/停用、资源建档/资格、日历、活动草稿创建/编辑/资源预览及部分状态刷新/后端重启恢复。审批、首次预约、发布/报名、故障、候选比较、改期、确认与通知页面已实现请求接入，仍待生产服务闭环。未知结果保留同一幂等请求，版本冲突保留表单并重新读取状态；改期目标改变或选择候选后必须重新预览。

开发预览始终标注演示数据，业务写入禁用，不创建会话；生产构建剔除所有预览入口与fixture。改动限定web、docs/ui及UI截图/测试证据，后端、公共契约、QA工程、课程原件和Word未改。

验证：types:check/typecheck/build通过，78项unit、13项布局、5项局部真实联调与1项后端重启检查通过。Java21全量verify已运行，70项unit通过，93项IT中8项因QA数据装载外键错误失败。CM ChangePort/EventPort与改期/通知/审计生产实现仍是依赖：真实提交/故障503、改期/通知404；完整预约/报名/改期、D-AT-23/24、QA E2E及Compose发布未通过。命令、请求与截图见`docs/ui/real-integration-2026-10-31.md`。

2026-10-31杨畅已授权提交PR；已安全同步最新main=c4f3d41，保留原始feb0d70联调证据及全部本地修改。通过已有个人fork的`ychangz1:work/17231018-ui`向`Rito-492/campus-sched:main`发起草稿PR，等待其他成员评审，不自审或自动合并。最新main前端与局部真实联调复验见`artifacts/test-results/ui-pr-2026-10-31/`；JDK21全量verify失败记录仍明确对应原始feb0d70基线。
