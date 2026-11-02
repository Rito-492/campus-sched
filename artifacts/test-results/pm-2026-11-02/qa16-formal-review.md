## 评审结论：必须改（QA验收包接收）
评审执行：冀项阳55230223的PM工作包 / Codex；日期2026-11-02。被审head：19ae94ba8d0e461336ba037e315bbc077d529196；base：b8be7e4。没有修改或批准自己的PR，也没有合并main。

### 必须改
1. **[P2] 停用工作人员的断言混淆“待确认行”和“已作决定”。** `server/src/test/java/com/campus/events/acceptance/IdentityEligibilityIT.java:517-519`统计该人员的全部change_confirmation行并要求0。但CM在创建改期时就预建decision/decided_at均为NULL的待办（ChangeRepository.createConfirmations），common/02 §4明确decision可空。401/403拒绝后的正确结果是待办保留且没有决定，删除待办反而破坏快照。请核对行数不变、decision/decided_at仍NULL、改期仍AWAITING_CONFIRMATIONS，保留停用请求401/403及恢复账号的检查。
2. **[P2] STALE_VERSION断言增加了未约定的必填字段。** `server/src/test/java/com/campus/events/acceptance/PermissionsIT.java:363-365`要求details含versionNo或lockVersion；现行common/02 §1允许details={}，并要求UI在409后重新GET，OpenAPI也未将这两个details字段设为必填。请保留409/STALE_VERSION、完整错误信封及无新版本写入，改为重新GET确认versionNo/lockVersion未改变；若确需扩展必填契约，应先由PM协调AN/CM/UI统一修改。

以上均是当前基线中已有的QA断言，本轮在解阻实验中定位，不称作本PR新引入回归；但作为此次QA验收包的接收门槛，请补上最小修正，避免后续合规实现仍必然被判错。

### 真实复验与归因
- 原head默认verify：77单元通过；112 IT=101通过/9失败/1错误/1跳过，exit1，与PR报告一致。
- 独立实验仅给AN空APPROVE意见提供非空事件摘要，并将AN模块测试的EventPort与ChangePort替身都标为Primary，未改QA断言：77单元通过；146 IT=143通过/2失败/0错误/1跳过。两个残余失败恰为上述两项。
- 在实验中仅修正上述两处QA检查后，77单元+2个针对性IT通过；这是建议验证，不是原head全量通过，也不是已合入修复。
- Node22.23.0下，QA契约/类型/160行策略evaluate通过；前端107项unit、类型和production build通过。真实生产预览下原QA选定19项浏览器检查通过；改期单项再执行后关闭seed重启，API与新浏览器reload保留version2/3102/15:00。
- 333项PR变更的范围、空文件、禁用测试及认证字段模式扫描未发现问题；审阅了装载、事务/JSONB、通知探针、回滚与最终浏览器兼容补丁。

### 其他问题与限制
AN首次合法空审批意见400，以及AN模块测试与生产端口Bean冲突仍由AN修复；两个Primary均需处理。PM另复现CM所有者列入staff后被要求自我确认，单独交CM跟进。UI依赖审计仍有6 moderate/2 critical开发依赖项；发布目录仍缺实际交付说明/manifest。上述不冒称本QA PR引入。

原始、归因、建议验证及诊断证据分别保存，PM本轮记录在`artifacts/test-results/pm-2026-11-02/`。必须改未清零，本次不Approve、不合并；测试资产可接收与整机发布放行分开判断。
