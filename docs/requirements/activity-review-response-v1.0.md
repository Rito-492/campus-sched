# PR #11 交叉评审修复说明 v1.0

日期：2026-10-29。作者：冯玉琪（22220314）/Damer-F。对应评审：Rito-492代发的“必须改”，被审提交9b76b2e、0886c8b。本文为作者处理记录，不是作者批准自己的PR；仍需非作者复核。

| 必须改 | 修复与证据 |
|---|---|
| 1 API-13 mine并集及APPLICANT | SQL使用所有者/当前版本staffUserIds/任意报名历史三个OR分支，集合天然去重且状态过滤先于LIMIT；Service检查当前APPLICANT。HTTP真库回归包含重叠分支、WITHDRAWN历史、无关活动排除、状态筛选和ADMIN无APPLICANT时403 |
| 2 API-14报名历史豁免 | 没有当前工作人员关系的ACTIVE/WITHDRAWN/CANCELLED报名历史均可作为详情读取依据；保留权限裁剪和API-23名单拒绝。真库HTTP测试报名→取消→本人列表/详情200、myRegistrationStatus=CANCELLED、名单及无关DRAFT仍403 |
| 3 发布门禁证据不足 | 新增Service层已开始、错误状态、AT_RISK三条负向测试，断言409且不改状态/不写事件；真库HTTP另验证已开始/资源故障拒绝、信封及持久状态不变。追踪表补充实际覆盖范围 |

## 本轮同时处理的建议

- A：移除发布时CHANGE_PENDING附加阻断，增加待改期仍可发布/报名仍冻结的HTTP回归。保留实时风险检查：现有docs/requirements/use-cases.md UC-09明确前置“实时风险NORMAL”，个人任务书AN-03验收也明确“未开始无风险才发布”，不是本轮新增契约规则。若PM需要变更该产品规则，应统一更新这两处权威要求。
- E（部分）：HTTP视图改用count(*)获取真实ACTIVE人数，不再调用activeIds装载全量名单；内部ActivitySnapshot仍保留完整名单。资源类别批量化和全部N+1优化仍未完成。
- F：edit/submit在锁定调用者提供的工作人员前检查活动归属；非所有者稳定403，新增单测确认不锁任何人员。
- G：接口失败码表明确工作人员账号不存在来自AccountPort的404，人员不合资格/同组织校验为400；没有承诺不存在账号也返回400。
- H：补注释说明审批绑定来自活动锁、lockVersion和PENDING期间内容不可变，不接受客户端单独指定另一个审批版本。
- J：归档修复前/默认/修复后真实日志、3份JUnit XML/TXT、隔离POM及源码哈希到artifacts/test-results/an-2026-10-29/。
- K：新增资源确认冲突的HTTP回归，核对409、RESOURCE_CONFLICT、非空message/requestId、无data、details.conflicts中的代码/资源ID/时段，并核对没有部分预约。
- L（部分）：补public/review HTTP直接用例、过去安排发布负向与待改期发布回归。其他路由幂等重放、容量非法值与竞争回归次数扩展未在本轮新增。
- M（部分）：两份新增交接/验证文档按规范补v1.0后缀并更新本人范围内链接；原状态机生产代码未改。

## 保留的建议与集成依赖

B的review集合收紧、C的缺端口HTTP状态码配对、D的审核者DRAFT/CANCELLED读取范围、I的资源读取/加锁端口归属，以及工作人员裁剪/API-23授权口径，仍交PM统一确认。本轮不私改公共契约，也不将这些建议标记为已解决。E/L/M未完成部分可后续消化。

默认编译仍被CM DemoDataSeeder.java:118阻断；CM ChangePort/EventPort仍为测试上下文替身，不能推断M2/M3/E2E或重启恢复通过。最终本模块隔离结果：25单测+22真实PostgreSQL IT，0失败/错误/跳过，见[验证记录](activity-module-validation-v1.0.md)。接收/复核确认：尚未接收。
