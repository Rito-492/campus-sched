# PR #16 评审修正（2026-11-02）

评审人：PM 冀项阳55230223 / GitHub jixiiangyang；评审对象19ae94b，2026-11-02 CHANGES_REQUESTED。QA汤雨润55230316逐项修正，不变更生产实现或公共契约，不自行批准/合并。

## 必须改1：停用工作人员的待办快照

[IdentityEligibilityIT](../../server/src/test/java/com/campus/events/acceptance/IdentityEligibilityIT.java) 在创建改期后先核对staff_a存在1条待确认行，decision/decided_at均NULL。停用后旧令牌确认仍要求401/403；随后要求该人员行数不变、decision/decided_at仍NULL、改期仍AWAITING_CONFIRMATIONS。保留恢复启用、撤回重建检查。不再把“存在待确认行”误判为“已代签”，不要求删除快照。

## 必须改2：陈旧版本错误详情

[PermissionsIT](../../server/src/test/java/com/campus/events/acceptance/PermissionsIT.java) 的旧baseVersion与旧lockVersion两请求均保留409/STALE_VERSION、requestId/message/details对象错误信封。移除details必须出现versionNo或lockVersion的限定；分别重新GET确认内容/锁版本不变，继续核对无新activity_version。

契约说明：common/02 §1的错误信封允许details对象；§3行196实际还写有其余409含版本或status（适用时）。本轮遵从评审做QA最小修正，不把某一种details字段写成强制、不改公共契约；原文与评审口径若需进一步澄清交PM协调，不由QA重写。

## 验证边界

- main仍b8be7e4；正式分支为19ae94b加上述两个QA文件补丁，AN生产/模块源码完全不变。
- 首次复验因停止后的Docker动态映射端口改变而连接拒绝，保留原始失败；重新读取容器实际端口后正式基线：77单测通过，两目标IT均在首次合法空意见审批400“事件字段不完整”处失败，尚未到本轮目标断言。见 [原基线复验](../../artifacts/test-results/qa-2026-11-02/pr16-review-fixes/original-targeted-rerun/)。不能称原基线全绿。
- 另建19ae94b detached隔离实验副本，复制两个QA修正；仅实验中给AN空意见事件提供非空摘要、将AN模块测试EventPort和ChangePort两替身标Primary。实验补丁单独归档，绝不提交生产改动或将实验结果当原基线结果。
- 实验完整默认verify **exit0：77/77单测；146 IT=145通过/0失败/0错误/1显式跳过**，其中PermissionsIT13/13、IdentityEligibilityIT6/6（含两评审目标）全部通过。该结果仅属于解阻实验，不是正式基线全绿。统计见 [实验结果](../../artifacts/test-results/qa-2026-11-02/pr16-review-fixes/unblocked-experiment/result-summary.json)；两项目标实际断言已到达。使用JDK21与PostgreSQL17独立campus_test、schema隔离，无Mock冒充产品验收。
- QA契约40 API/9枚举/15 DTO与typecheck实际通过；源码diff空白检查通过。

## 交付

本轮只提交两处QA断言、此修正说明、handoff及精选真实证据。旧覆盖矩阵不按隔离实验自动提升，原AN缺陷08/09仍待模块作者修复。推送PR16后回复原评审请求复审，GitHub批准与合并继续由交叉评审执行。
