# 30 场景覆盖矩阵 v3.0

> 归属：测试经理（55230316）。依据 `agents/common/03-验收与演示数据.md` §2（D-AT-01～24 + N-AT-01～06）。
> 用途：QA-02/03 的测试改造对照表与 QA-05 覆盖判定的执行台账（判定规则见 `test-plan.md` §4.1）。
> **执行状态初始全部「未执行」**；每轮执行后更新状态列并附证据路径。主责列指模块自测负责人，QA 列为跨模块验收落点。

| 编号 | 场景摘要 | 需求关联 | 主责 | QA 执行落点 | 状态（2026-10-31真实PG/Failsafe） |
|---|---|---|---|---|---|
| D-AT-01 | 登录/错密码/停用后旧令牌 401；响应无 passwordHash | FR-01 | PM | PermissionsIT-01 + login.spec | 🟨 部分：PermissionsIT三项通过；login.spec未运行 |
| D-AT-02 | approver_other/account_admin/applicant_b 越权均 403 无变化 | FR-02 | PM+AN | PermissionsIT（TC-02组） | ⛔ 阻塞：三项在读取活动时ChangePort 503，越权断言未到 |
| D-AT-03 | 新建→编辑→提交→批准→资源确认→发布；首确前 0 ACTIVE | FR-03 | AN | AN自测 + `booking.spec.ts` | 🟨 部分：ActivityRegistrationIT全生命周期通过，但CM端口为测试替身；E2E未运行 |
| D-AT-04 | 相邻占用可共存；布场 15 分钟产生边界冲突 | FR-04 | DS | DS自测 + Bench(SCH-05) | ✅ 通过（ResourceSchedulingIT真实PG模块断言） |
| D-AT-05 | 错峰占用峰值恰 2，不得误算 3 | FR-05 | DS | DS自测 + Bench(SCH-06) | ✅ 通过（ResourceSchedulingIT真实PG模块断言） |
| D-AT-06 | 缺件冲突指出 [11,12) 缺 1，无部分占用 | FR-06 | DS | DS自测 | ✅ 通过（ResourceSchedulingIT真实PG模块断言） |
| D-AT-07 | 两批准活动抢同资源，恰 1 成功 1 冲突，无部分预约 | FR-07 | DS+AN | BookingConcurrencyIT | 🟨 部分：DS底层竞态IT通过；HTTP BookingConcurrencyIT被EventPort 503阻断 |
| D-AT-08 | 幂等：同 key 同 body 重放 / 同 key 不同 body 409 | FR-08 | PM | PermissionsIT-08 | 🟨 部分：AN模块重放测试通过；PermissionsIT前置被EventPort 503阻断 |
| D-AT-09 | 改期预览两类冲突 + 首选 3102+3202×2 代价 (0,1,1) | FR-09 | DS+UI | DS自测 + conflict.spec | ⬜ 未执行：本轮没有对应首选方案/UI断言证据 |
| D-AT-10 | 改期确认链 staff→approver→resource，version2 原子切换 | FR-10 | CM+AN | `change-happy-path.spec.ts` + ChangeRollbackIT 前置 | ⛔ 阻塞：无生产ChangePort/改期闭环，E2E未运行 |
| D-AT-11 | 工作人员/批准人拒绝 → REJECTED 原安排完整 | FR-11 | CM | ChangeRollbackIT-11 | ⛔ 阻塞：两项在GET活动阶段ChangePort 503，未到拒绝断言 |
| D-AT-12 | 预览后被抢占 → CONFLICTED，原安排保留 | FR-12 | CM+DS | ChangeRollbackIT-12 + conflict.spec | ⛔ 阻塞：ChangePort缺失，抢占后确认断言未到 |
| D-AT-13 | 自身旧占用排除，不自竞争 | FR-13 | DS+CM | DS自测 + Bench(SCH-01) | 🟨 部分：DS ResourceSchedulingIT自占用排除通过；改期HTTP测试被ChangePort阻塞 |
| D-AT-14 | 预约替换中注入异常整体回滚 | FR-14 | CM | ChangeRollbackIT-14 | 🟨 部分：DS/AN模块回滚子项通过；改期+outbox整体回滚未到断言 |
| D-AT-15 | 改期期间报名冻结 CHANGE_PENDING | FR-15 | AN+CM | RegistrationCapacityIT-15 | 🟨 部分：AN模块冻结测试通过；真实改期创建返回404 |
| D-AT-16 | 容量 1 对 20 并发 JOIN 恰 1 ACTIVE | FR-16 | AN | RegistrationCapacityIT-16 | ⛔ 阻塞：commit 37a1ac1诊断复验20/20均503 INTERNAL_ERROR；ChangePort缺失在hasPending前置阻断，AN服务级并发子项通过 |
| D-AT-17 | 取消两次不多释放；取消与改期竞争一致终态 | FR-17 | AN+CM | CancellationRaceIT | 🟨 部分：AN取消状态子项通过；真实取消/改期竞态因创建改期404阻塞 |
| D-AT-18 | 故障 → AT_RISK，页面不谎报原安排可用 | FR-18 | DS+UI | DS自测 + conflict.spec | 🟨 部分：DS真实PG风险/故障断言通过；UI提示未运行 |
| D-AT-19 | 通知消费失败恢复 + 重复消费去重 | FR-19 | CM | NotificationConsistencyIT | ⛔ 阻塞：创建改期404，通知消费/去重断言未运行 |
| D-AT-20 | 旧 baseVersion/lockVersion → STALE_VERSION | FR-20 | AN+CM | PermissionsIT-20 | 🟨 部分：AN模块陈旧编辑子项通过；PermissionsIT提交前置EventPort 503 |
| D-AT-21 | 日历脱敏/名单与他人通知 403 | FR-21 | 全员 | PermissionsIT-21 + permissions.spec | 🟨 部分/混合：名单403等通过；带+08:00日历请求400；改期详情被ChangePort阻塞；浏览器未运行 |
| D-AT-22 | first-fit vs RANKED 300 候选对比 | NFR-评测 | DS+测试 | SchedulingBenchmarkIT + evaluate | ❌ 失败：8×2×10原始测量已产出；评测期望在SCH-01/04/08不一致，交DS/QA核查 |
| D-AT-23 | 刷新/重启后状态来自真实持久化 | FR-23 | UI+配置 | `change-happy-path.spec.ts`（reload 断言） | ⬜ 未执行：本轮无前端/浏览器刷新与重启验收 |
| D-AT-24 | 干净环境按发布说明启动换角色答辩 | FR-24 | 全员 | 发布演练（check.ps1 + 人工） | ⛔ 阻塞/未执行：无发布演练；默认verify整体失败 |
| N-AT-01 | 身份与角色分离：教师申请人/学生申请人/仅账号管理员调用审批复核 | 身份规则 | PM+AN | IdentityEligibilityIT.nat01 | 🟨 部分：Foundation/AN身份子项通过；集成测试提交遇EventPort 503 |
| N-AT-02 | 两类身份×四种资源资格（3111-3114/3211-3214），UI+HTTP 双通道 | 资格规则 | DS+UI | IdentityEligibilityIT.nat02 + conflict.spec | 🟨 部分：DS真实PG资格矩阵通过；HTTP身份矩阵/界面未完成 |
| N-AT-03 | 候选过滤受限资源；强制提交仍拒绝；按所有者身份校验 | 资格规则 | DS | IdentityEligibilityIT.nat03 + Bench | 🟨 部分：DS资格检查子项通过；应用候选读取因ChangePort 503阻塞 |
| N-AT-04 | 提交后改身份/资格：未确认用最新设置；旧预约保留；新目标不合资格拒绝 | 资格规则 | AN+CM | IdentityEligibilityIT.nat04 | 🟨 部分：AN当前资格/身份重查子项通过；真实pending-change链未完成 |
| N-AT-05 | 所有者兼任 APPROVER/RESOURCE_CONFIRM 对本人申请 4 种审核全拒 | 回避规则 | PM+AN | IdentityEligibilityIT.nat05 | 🟨 部分：AN真实PG自审拒绝子项通过；集成测试提交遇EventPort 503 |
| N-AT-06 | 审批范围/管理范围不全覆盖拒绝；停用工作人员拒绝不代签 | 范围规则 | PM+CM | IdentityEligibilityIT.nat06 | 🟨 部分：Foundation/DS/AN范围子项通过；完整停用工作人员流程遇EventPort 503 |

## 2026-10-31 执行证据与统计

- QA测试代码commit：`a697d90871c6126bf6f42f26c938be9a1e9792cc`；main基线：`feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`。
- 默认命令：`./server/mvnw -f server/pom.xml verify`；PostgreSQL 17隔离容器，`campus_test`，测试schema按类生成；DB密码和`DEMO_PASSWORD`运行时随机注入且未入代码/日志。容器已按名称、标签、镜像校验后停止移除。
- 结果：Surefire 70/70通过；Failsafe 120项，93通过、25失败、2错误、0跳过，`verify`退出1。SeederIT 3项、QA新增SeederRollbackIT 1项、DbTestSupportIT 1项、DS ResourceSchedulingIT 20项、SchemaIT 8项、FoundationIT 21项、AN ActivityRegistrationIT 33项通过；ActivityRegistrationIT 的CM端口用测试替身，只算部分覆盖。
- D-AT-22：SchedulingBenchmarkIT跑完8场景×2策略×10次并生成160行原始结果，单项benchmark Failsafe通过；`npm --prefix qa run evaluate`退出1，SCH-01、SCH-04、SCH-08实测代价与预期不一致，已列缺陷交DS/QA核查；没有修改期望值迎合实测。
- 统计：RfT=30；达到至少一个目标断言的场景Tx=21（3通过、17部分、1失败），部分覆盖/失败计入已执行；7项阻塞于缺失生产端口或交付，2项未执行（D-AT-09、D-AT-23）；Ts=3。执行覆盖率Tx/RfT=70%，完全成功率Ts/RfT=10%。阻塞不计Tx，模块级通过不推导M0-M4完成。
- 完整日志/原始报告：`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/`；评测对照：`artifacts/evaluation/summary.md`、`artifacts/evaluation/results.csv`；最终原始JSON为`artifacts/test-results/qa-2026-10-31/final-verify-with-seeder-rollback/scheduling-evaluation.json`。

## 需求追溯说明（QA-05 交付给 AN 的主表输入）

- 本矩阵覆盖 `docs/requirements/` 规约中的 FR-01～25 与 NFR-01～30 中**可测试项**；纯文案类 FR 依任务书 §3「文案修改不强制镜像测试」标注 N/A。
- FR/NFR 全量对照以 `docs/requirements/traceability.md` 为主表（AN 维护），本矩阵提供测试侧执行状态回填。
- v2.0 旧映射（test-design.md §0 表）中 teacher/sys_admin 等旧名已废弃；改造完成后并入本矩阵，test-design.md 保留用例细节作为断言依据。

## 统计口径（03 契约 + 课程课件 6.2）

- 已执行覆盖 = Tx / RfT（Tx=已执行用例数，RfT=用例总数）；成功覆盖 = Ts / RfT（Ts=完全成功无缺陷数）。
- 每个测试需求至少一正一负两个用例（课件 5.1）；正负面配对情况在各 IT 的 @DisplayName 标注。
