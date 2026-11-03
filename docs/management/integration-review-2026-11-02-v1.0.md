# 主线集成与正式审核记录 v1.0

日期：2026-11-02；责任人：冀项阳55230223（PM-01～05）。以本轮实际源码与命令为准，历史记录不覆盖。本轮没有接管其他成员源码，也没有合并或批准自己的PR。

## 1 已同步与已接收

PM分支从2055c63安全快进至main=b8be7e48c227627c2c6e24aee534cf727200faf6，已包含AN #13、PM #14、DS #15、CM #18和UI #17。40项契约均已有生产Controller，五类端口均有真实实现，应用已实际启动且没有循环依赖。CM改期/通知/审计和UI不再是“源码缺失”。

QA #16的head=19ae94ba8d0e461336ba037e315bbc077d529196在独立工作区原样审核，没有合入PM或main。审核涵盖测试支撑、外键/事务/JSONB、回滚注入、通知真实探针、浏览器兼容补丁、数据与证据；333项变更的范围、空文件、新增禁用测试及认证字段模式扫描未发现问题。扫描SHA来自复验工作区，两个evaluation工件为本次重新生成的输出，源代码未改写。

## 2 PM实际新增的集成回归

ProductionWorkflowIT直接启动生产应用，使用真实Account/Activity/Scheduling/Change/Event实现、真实HTTP和PostgreSQL，无CM或通知端口替身：

1. 从账号/资源建档、活动提交、带合法意见的首次审批，到资源确认、发布、学生报名、工作人员改期确认、复审、资源原子切换、通知持久化及取消。核对有效版本、旧新预约、报名版本、改期终态、通知收件快照与最终释放。
2. 待资源复核时撤销目标资源管理范围，同一旧会话403；保留原版本/预约/待处理改期且不落回执/审计。恢复范围后用原key重试成功，重放完整首次响应。

有意见的首次审批是明确的有效路径，不替代原QA对合法空意见的测试。默认构建仍编译并执行所有原测试；pm-isolated只追加排除需要其他模块的ProductionWorkflowIT，未改变默认验收范围。

## 3 分范围真实结果

Java21.0.12.1、Maven3.9.11、PostgreSQL17.6、Node22.23.0；本机campus_test随机schema；真实浏览器使用Edge无界面模式。以下结果不能累加为唯一覆盖率。

| 范围 | 真实结果 | 结论 |
|---|---|---|
| 原main=b8be7e4默认verify | 77单元通过；85 IT=75通过/9初始化错误/1跳过，exit1 | 8个QA装载错误及AN测试Bean冲突仍在 |
| PM新增后的指定集合 | 77单元+38 IT全通过，0跳过，exit0 | PM模块及限定生产集成回归通过 |
| PM新增后的默认verify | 77单元通过；87 IT=77通过/9初始化错误/1跳过，exit1 | 不冒称全量通过 |
| PM独立兼容配置 | 28单元+22 IT全通过，exit0 | 不替代生产集成 |
| QA #16原head默认verify | 77单元通过；112 IT=101通过/9失败/1错误/1跳过，exit1 | 复现QA公开报告，未改断言或生产 |
| 独立AN归因实验 | 只对空审批事件摘要与两个测试端口Primary提出最小补丁；77单元通过，146 IT=143通过/2失败/1跳过，exit1 | 余下两项均为QA断言与当前契约不符；不是原head结果 |
| 独立QA建议检查 | 在上述实验中修正两处断言，77单元+2项针对性IT通过 | 只是建议验证，未合入或宣称整个实验全量通过 |
| CM所有者自我确认诊断 | 77单元通过，1项规则探针失败；期望PENDING_APPROVER、实际AWAITING_CONFIRMATIONS | 新发现的CM缺陷，不记作通过 |
| UI当前main | Node22下types:check/typecheck、107项unit、production build通过 | 类型、单元和构建限定通过 |
| QA静态与评测 | 40 API/9枚举/15 DTO、QA类型及160行evaluate通过，16组expected一致 | 评测仅四资源benchmark，不推导全资源最优 |
| QA原浏览器测试的生产预览复验 | 选定19项通过、0跳过/失败 | 含登录、三视口自动检查、教师专用资格组、真实改期；不是全套E2E |
| 改期重启核对 | 另外重跑同一个改期用例建立独立数据，关seed重启，API和新浏览器reload保持version2/3102/15:00 | 重复用例不另算唯一覆盖；D-AT-23的限定路径通过 |
| UI依赖审计 | exit1；6 moderate、2 critical，共8项 | critical为vitest/tinypool开发测试链，风险仍未关闭 |

首次dev服务器运行19项中有1项登录跳转未完成，保留qa-e2e-dev-first.log；改为实际production build预览后19项全通过。重启辅助脚本初次误用了4101固定ID，校正为数据集4001后完成上述独立复验，未修改QA测试来迁就运行器错误。

## 4 正式审核结论及责任

### QA #16：必须改，已正式Request Changes

已通过jixiiangyang账号提交[正式审核](https://github.com/Rito-492/campus-sched/pull/16#pullrequestreview-5732183680)，绑定19ae94b，非作者交叉评审，未Approve或合并。

- QA汤雨润修IdentityEligibilityIT:517～519：预建的待确认行不是代签；停用请求拒绝后应保留待办、decision/decided_at均NULL、状态不推进，不能要求整行不存在。
- QA汤雨润修PermissionsIT:363～365：现契约允许details={}，UI在409后重新GET；不能单方面把versionNo/lockVersion设为必填。保留409/STALE_VERSION和无覆盖断言，用重新GET校验版本/锁不变。

两处均是基线已有的QA断言，本轮解阻后新定位，不描述为本PR引入回归。它们作为此次验收包接收前的修正项，建议补丁和针对性通过证据已提供。不得通过删外键、跳过失败用例或改变生产返回以迁就错误断言。

### AN：生产衔接及模块测试仍需修复

冯玉琪负责合法APPROVE空意见被直接用作空事件摘要导致400的问题；仅补非空事件摘要后备，不应把合法空意见改成非法。模块测试的EventPort和ChangePort替身均需明确Primary，避免与新生产Bean冲突。原QA错误及独立归因实验均有记录；建议补丁未应用到PM/main。

### CM #18：合并后复核发现P2

已提交[合并后复核意见](https://github.com/Rito-492/campus-sched/pull/18#pullrequestreview-5732184450)。ChangeService:123将所有staff都纳入requiredStaffUserIds，违反BR-07/BR-23“所有者不需要向自己确认”。杨瀚霖应在本人分支过滤所有者、保留通知收件人并补“仅所有者/所有者+他人”回归，再走新PR。PM只提交诊断，不改CM源码。

### 其他接收条件

UI杨畅评估并修复依赖审计；DS/QA核对默认集成中的1项skip，不能因退出码为0记作已执行。资源读取边界及私有历史访问提案继续按access-coordination-v1.0协调，未擅自扩权。V2迁移已真实运行；迁移目录按共同规程归DS统一维护，CM/DS仍需补归属接收记录，技术运行通过不等同签字确认。

## 5 阶段与发布结论

M0/M1已有可构建UI、可迁移数据库和真实五端口，但QA候选尚未合入、全量错误未清；不能记全组通过。M2/M3已验证部分合法真实链，合法空审批、所有者staff等边界仍失败，不能全面放行。QA原报告的30场景18通过/10部分/2阻塞保持其报告口径，本轮不自行提高覆盖率。

M4/发布不放行：本机未找到Docker CLI，未执行Compose干净发布；release仅.gitkeep，实际发布说明/manifest、完整30场景与剩余资格/UI分支、依赖审计整改及发布恢复仍缺。CM负责发布、QA验证、UI手册、PM核对实际commit；实际人时、课程平台回执及签字不补造。

证据见[结果与源码范围](../../artifacts/test-results/pm-2026-11-02/check-summary.json)及同目录日志、计数摘要、原始160行测量、建议补丁和诊断源码。未提交JUnit XML中的环境属性、运行凭据或构建缓存。浏览器和后端、本轮随机UI schema及本次启动的PostgreSQL均已停止清理。用户Word的SHA-256与开工时一致，原有handoff追加及本地计划草案保留。
