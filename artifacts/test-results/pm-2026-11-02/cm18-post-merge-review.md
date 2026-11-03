## 合并后验收复核：必须跟进的P2缺陷
评审执行：冀项阳55230223的PM工作包 / Codex，2026-11-02。复核main=b8be7e4中的CM实现，对应本PR head b19ae8bf07fc0d6ca570af9bc953854b4c9899ec。

**[P2] 所有者列入staff时被要求向自己确认改期。** `server/src/main/java/com/campus/events/change/ChangeService.java:123`直接把staffUserIds全部作为requiredStaffUserIds。BR-07明确所有者可列入工作人员但不需要向自己确认；BR-23/common/01 §3要求没有其他工作人员时直接进入PENDING_APPROVER。当前真实HTTP创建只含所有者的staff活动并发起改期，返回AWAITING_CONFIRMATIONS，错误地冻结并要求自我确认。

独立PostgreSQL诊断：77项单元通过，1项业务规则探针失败，期望PENDING_APPROVER、实际AWAITING_CONFIRMATIONS。该探针不使用端口替身，未改CM生产实现。原始失败保留于PM本轮证据`artifacts/test-results/pm-2026-11-02/`，不把诊断记作通过。

建议在构建requiredStaffUserIds时排除activity.ownerId并去重排序；notificationUserIds仍保留所有者。请在CM本人分支补“仅所有者staff→直接待复审”和“所有者+其他staff→仅其他人确认”的真实回归，按PR流程交叉评审。本PR已合并，本意见是后续缺陷记录，不追写原合并批准，也未由PM越权改CM源码。
