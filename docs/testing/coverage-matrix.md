# 30 场景覆盖矩阵 v3.0

> 归属：测试经理（55230316）。依据 `agents/common/03-验收与演示数据.md` §2（D-AT-01～24 + N-AT-01～06）。
> 用途：QA-02/03 的测试改造对照表与 QA-05 覆盖判定的执行台账（判定规则见 `test-plan.md` §4.1）。
> **执行状态初始全部「未执行」**；每轮执行后更新状态列并附证据路径。主责列指模块自测负责人，QA 列为跨模块验收落点。

| 编号 | 场景摘要 | 需求关联 | 主责 | QA 执行落点 | 状态 |
|---|---|---|---|---|---|
| D-AT-01 | 登录/错密码/停用后旧令牌 401；响应无 passwordHash | FR-01 | PM | PermissionsIT-01 + login.spec | ⬜ 未执行 |
| D-AT-02 | approver_other/account_admin/applicant_b 越权均 403 无变化 | FR-02 | PM+AN | PermissionsIT（TC-02组） | ⬜ 未执行 |
| D-AT-03 | 新建→编辑→提交→批准→资源确认→发布；首确前 0 ACTIVE | FR-03 | AN | AN自测 + happy.spec 复核 | ⬜ 未执行 |
| D-AT-04 | 相邻占用可共存；布场 15 分钟产生边界冲突 | FR-04 | DS | DS自测 + Bench(SCH-05) | ⬜ 未执行 |
| D-AT-05 | 错峰占用峰值恰 2，不得误算 3 | FR-05 | DS | DS自测 + Bench(SCH-06) | ⬜ 未执行 |
| D-AT-06 | 缺件冲突指出 [11,12) 缺 1，无部分占用 | FR-06 | DS | DS自测 | ⬜ 未执行 |
| D-AT-07 | 两批准活动抢同资源，恰 1 成功 1 冲突，无部分预约 | FR-07 | DS+AN | BookingConcurrencyIT | ⬜ 未执行 |
| D-AT-08 | 幂等：同 key 同 body 重放 / 同 key 不同 body 409 | FR-08 | PM | PermissionsIT-08 | ⬜ 未执行 |
| D-AT-09 | 改期预览两类冲突 + 首选 3102+3202×2 代价 (0,1,1) | FR-09 | DS+UI | DS自测 + conflict.spec | ⬜ 未执行 |
| D-AT-10 | 改期确认链 staff→approver→resource，version2 原子切换 | FR-10 | CM+AN | happy.spec + ChangeRollbackIT 前置 | ⬜ 未执行 |
| D-AT-11 | 工作人员/批准人拒绝 → REJECTED 原安排完整 | FR-11 | CM | ChangeRollbackIT-11 | ⬜ 未执行 |
| D-AT-12 | 预览后被抢占 → CONFLICTED，原安排保留 | FR-12 | CM+DS | ChangeRollbackIT-12 + conflict.spec | ⬜ 未执行 |
| D-AT-13 | 自身旧占用排除，不自竞争 | FR-13 | DS+CM | DS自测 + Bench(SCH-01) | ⬜ 未执行 |
| D-AT-14 | 预约替换中注入异常整体回滚 | FR-14 | CM | ChangeRollbackIT-14 | ⬜ 未执行 |
| D-AT-15 | 改期期间报名冻结 CHANGE_PENDING | FR-15 | AN+CM | RegCapacityIT-15 | ⬜ 未执行 |
| D-AT-16 | 容量 1 对 20 并发 JOIN 恰 1 ACTIVE | FR-16 | AN | RegCapacityIT-16 | ⬜ 未执行 |
| D-AT-17 | 取消两次不多释放；取消与改期竞争一致终态 | FR-17 | AN+CM | CancellationRaceIT | ⬜ 未执行 |
| D-AT-18 | 故障 → AT_RISK，页面不谎报原安排可用 | FR-18 | DS+UI | DS自测 + conflict.spec | ⬜ 未执行 |
| D-AT-19 | 通知消费失败恢复 + 重复消费去重 | FR-19 | CM | NotifyConsistIT | ⬜ 未执行 |
| D-AT-20 | 旧 baseVersion/lockVersion → STALE_VERSION | FR-20 | AN+CM | PermissionsIT-20 | ⬜ 未执行 |
| D-AT-21 | 日历脱敏/名单与他人通知 403 | FR-21 | 全员 | PermissionsIT-21 + permissions.spec | ⬜ 未执行 |
| D-AT-22 | first-fit vs RANKED 300 候选对比 | NFR-评测 | DS+测试 | SchedulingBenchmarkIT + evaluate | ⬜ 未执行 |
| D-AT-23 | 刷新/重启后状态来自真实持久化 | FR-23 | UI+配置 | happy.spec（reload 断言） | ⬜ 未执行 |
| D-AT-24 | 干净环境按发布说明启动换角色答辩 | FR-24 | 全员 | 发布演练（check.ps1 + 人工） | ⬜ 未执行 |
| N-AT-01 | 身份与角色分离：教师申请人/学生申请人/仅账号管理员调用审批复核 | 身份规则 | PM+AN | IdentityEligibilityIT.nat01 | ⬜ 未执行 |
| N-AT-02 | 两类身份×四种资源资格（3111-3114/3211-3214），UI+HTTP 双通道 | 资格规则 | DS+UI | IdentityEligibilityIT.nat02 + conflict.spec | ⬜ 未执行 |
| N-AT-03 | 候选过滤受限资源；强制提交仍拒绝；按所有者身份校验 | 资格规则 | DS | IdentityEligibilityIT.nat03 + Bench | ⬜ 未执行 |
| N-AT-04 | 提交后改身份/资格：未确认用最新设置；旧预约保留；新目标不合资格拒绝 | 资格规则 | AN+CM | IdentityEligibilityIT.nat04 | ⬜ 未执行 |
| N-AT-05 | 所有者兼任 APPROVER/RESOURCE_CONFIRM 对本人申请 4 种审核全拒 | 回避规则 | PM+AN | IdentityEligibilityIT.nat05 | ⬜ 未执行 |
| N-AT-06 | 审批范围/管理范围不全覆盖拒绝；停用工作人员拒绝不代签 | 范围规则 | PM+CM | IdentityEligibilityIT.nat06 | ⬜ 未执行 |

## 需求追溯说明（QA-05 交付给 AN 的主表输入）

- 本矩阵覆盖 `docs/requirements/` 规约中的 FR-01～25 与 NFR-01～30 中**可测试项**；纯文案类 FR 依任务书 §3「文案修改不强制镜像测试」标注 N/A。
- FR/NFR 全量对照以 `docs/requirements/traceability.md` 为主表（AN 维护），本矩阵提供测试侧执行状态回填。
- v2.0 旧映射（test-design.md §0 表）中 teacher/sys_admin 等旧名已废弃；改造完成后并入本矩阵，test-design.md 保留用例细节作为断言依据。

## 统计口径（03 契约 + 课程课件 6.2）

- 已执行覆盖 = Tx / RfT（Tx=已执行用例数，RfT=用例总数）；成功覆盖 = Ts / RfT（Ts=完全成功无缺陷数）。
- 每个测试需求至少一正一负两个用例（课件 5.1）；正负面配对情况在各 IT 的 @DisplayName 标注。
