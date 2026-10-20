# 项目经理评审修复记录

日期：2026-10-20；成员：冀项阳55230223；PR：[7](https://github.com/Rito-492/campus-sched/pull/7)；被审查提交：`a5e0724f45e3f5e49ac14a68cc2cc463c8eb6cc2`。用户明确以项目经理模块与评审回归通过作为本轮重提条件，全项目集成状态仍单独记录。

## 1 拒绝问题与修复

依据[Kimcra1的评审](https://github.com/Rito-492/campus-sched/pull/7#pullrequestreview-5569691410)：

| 问题 | 根因及修改 | 回归证据 |
|---|---|---|
| R1：同账号不同幂等键死锁 | 两笔receipt插入均通过即时外键持有app_user KEY SHARE，随后FOR UPDATE锁升级互相等待。AccountRepository和SessionService登录统一改为FOR NO KEY UPDATE，不改DDL或契约 | 不同键双连接、action入口屏障、实际AccountPort.lock、5轮两笔业务均提交；登录与在途回执并行成功 |
| R2：Unix Wrapper不可执行 | Git模式100644改为100755，脚本文本不变 | 暂存模式检查及Git Bash直接执行`./server/mvnw -v`成功，Java21/Maven3.9.11 |

本版本用户更新仅修改身份、启停及关联授权，不修改用户id/username等唯一键。NO KEY UPDATE仍与其他非键更新、删除和相同行锁互斥，同时兼容外键KEY SHARE。[PostgreSQL17官方锁文档](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS)支持这一兼容性选择。所有PM生产用户显式加锁路径均已核查，没有遗留FOR UPDATE；receipt自身仍按原契约FOR UPDATE，锁顺序和五类端口签名不变。

没有通过先锁用户再插回执来绕开问题，也没有捕获死锁后伪造成功或降低隔离级别。未来新增唯一键修改或用户删除功能需重新评估锁策略，不在此次修复范围。

## 2 真实运行结果

环境：Windows、Java21.0.12.1、Maven3.9.11、PostgreSQL17.6、Node24.19.0。使用本地独立campus_test和随机schema，原始V1迁移；每类检查结束清理自己的schema。未使用数据库替身。

| 检查 | 实际结果 | 日志 |
|---|---|---|
| 增加两个回归测试，尚未修改生产锁 | 22项单元通过；两个新IT均失败，其中一个为deadlock detected，另一个为登录锁兼容性断言失败 | before-fix.log（测试口令/令牌已脱敏） |
| `mvnw.cmd -f server/pom.xml -Ppm-isolated clean verify` | 22项单元＋21项真实数据库IT，0失败/0错误/0跳过，退出0 | pm-verify.log、五份测试摘要 |
| `./server/mvnw -v` | Git Bash真实执行成功，退出0；Git文件模式100755 | unix-wrapper.log、check-summary.json |
| `node qa/scripts/check-contract.ts` | 40API、9枚举、15关键DTO通过，退出0 | contract-check.log |
| 当前分支默认`test` | 仍在既有DemoDataSeeder.java:118编译失败，退出1 | default-test.log |
| 临时集成CM已有修复后的默认`test` | 主源码编译通过；测试编译被未交付的StrategyEvaluator/StrategyResult阻塞，退出1 | cm-integration-test.log |
| 临时PM＋CM独立检查 | 22单元＋24 IT通过（PM21＋CM3），退出0 | pm-cm-isolated-verify.log |

日志目录为`artifacts/test-results/pm-2026-10-20/`。入库日志仅统一LF、去除行尾空格及对失败日志中的测试口令/令牌脱敏；错误、计数、时间和堆栈保持真实。MockMvc自动请求/响应打印已关闭，后续失败不自动输出认证材料。

## 3 集成范围与未完成事项

CM修复来源为`origin/fix/demodataseeder-118`的`ee76c2a1641f47269f9ff8cdcaab3cf5b4b7ad90`，仅在仓库外临时副本接入其DemoDataSeeder和DemoDataSeederIT。该分支还包含`1724219`异常处理、`17e5212`导入顺序、`52f3736`不重放已撤销授权的实际提交；本PR不复制或重写CM源码。

临时PM＋CM独立验证仅在副本的pm-isolated配置中增加dev生产/测试包与support测试包，以执行CM已有的三项真实库测试；该临时POM不进入生产分支，不当作默认全量通过。默认编译日志在使用未修改的默认POM时单独产生，明确记录缺失的DS类，没有排除SchedulingBenchmarkIT。

同步远端后，main仍为`2a9e941`，CM修复尚未合入；DS策略类仍未交付。默认全量verify、业务/E2E及发布验收不能宣称通过。本轮只在用户确认的PM及评审回归范围重提，并请原评审人复核，不自动合并或关闭拒绝意见。

## 4 接收与下一步

R1/R2请求原评审人Kimcra1、Rito-492重新检查；评审结果以GitHub真实记录为准，当前无放行或业务接收结论。CM完成其修复评审，DS交付策略和其余生产端口后，PM再组织默认入口与业务主线集成。课程原件、用户Word及其他成员未提交修改均保留。
