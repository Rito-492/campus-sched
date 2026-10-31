# DS 标准构建与跨模块复验（2026-10-31）

黄方勇（55230315）；开发任务包 v3.0；基准 main `feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`，分支 `work/55230315-scheduling`。

同步前将全部本地修改与未跟踪文件保存到 stash，快进 main 后 apply，无冲突；stash 保留作备份。PR #9 调度实现、PR #12 设计文档已合入。历史 2026-10-26 隔离证据仅作历史记录。

本轮使用 Java 21.0.6、标准 Maven 3.9.11、仓库原始 server/pom.xml、真实 PostgreSQL 17.6（本机 5440/campus_test）。Windows Wrapper 存在既有路径判断问题，因此直接运行已缓存的标准 Maven，不使用隔离 pom、不排除生产源码。凭据仅经 scripts/load-env.ps1 读取。测试只创建并清理自己的 schema。

生产修复：SchedulingPortAdapter 保存成功检查凭据时复制 equipment 列表，防止调用方修改共享 Plan 的可变列表后绕过已锁定资源集合。ResourceSchedulingIT 补充对应回归及真实 ActivityPort 依赖探针：ChangePort 未到时断言 503、版本和预约不变；到位后才执行跨端口切换及回滚断言。本轮仅执行了依赖拒绝分支，未验证后续成功分支。未修改共享契约、迁移或其他岗位生产代码。

## 可复现入口

先加载环境并指定 Java 21、TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5440/campus_test。

```powershell
mvn -B -ntp -f server/pom.xml '-Dit.test=SchemaIT,ResourceSchedulingIT,DsStrategyBenchmarkIT,SchedulingBenchmarkIT' verify
mvn -B -ntp -f server/pom.xml '-Dit.test=ResourceSchedulingIT#realActivityPortAndSchedulingPortSwitchAndRollbackTogether' verify
```

实际命令额外指定本机 Maven 缓存，不改变测试选择；完整日志见 artifacts/test-results/ds-2026-10-31。

## QA 与 CM 依赖边界

标准 verify：70 项单测通过；SchemaIT 8 项、ResourceSchedulingIT 28 项、DsStrategyBenchmarkIT 2 项通过；SchedulingBenchmarkIT 因 main 的参考数据外键装载错误失败，整体退出 1。独立评测两种范围各输出 160 行，仍不能替代正式 D-AT-22。新增 AN/DS 依赖探针首次按成功链路执行失败，实际原因是 ActivityPort.lock 返回 503“改期端口尚未就绪”；保留 an-ds-port-verify.log。补充明确的依赖拒绝断言后，标准 verify 的 70 单测和该 1 项探针通过（an-ds-dependency-verify.log）。成功分支待 CM 到位后复跑。

QA 分支 `0bb9aacba5dbf41cd38e44b9cc6b819141800d4a` 修复 DbTestSupport 的外键插入顺序、TIME/JSONB 类型及事务。临时 detached 工作区以同一 main 为基准，仅载入这份 QA 源文件和本轮 DS 生产修复，使用原始 pom 运行 SchedulingBenchmarkIT、ActivityRegistrationIT；不将 QA 文件混入 DS 提交。

接入修复后的第一次运行：AN 33 项模块 IT 通过；评测上下文缺少数据源配置失败。显式经环境配置 Spring 数据源（URL 的 currentSchema 引用 DbTestSupport 设置的 test.schema.name）后，评测进入场景装载，复现 SchedulingBenchmarkIT:208 的 fk_activity_current_version。该类自己的 loadScenario 不在事务内，活动插入先提交，版本尚不存在；后续 input_json 还须显式 ::jsonb。建议补丁 qa-benchmark-proposed.patch 仅应用在临时工作区，供 QA 接收，不修改正式 QA 源文件。

DS 独立评测：full-reference 的160行有6组预期差异，scenario-core 的160行有0组差异；前者包含新增资格资源，SCH-04 实际可行。四资源对照仅作诊断，不能减少正式搜索范围或冒充正式评测环境。

临时工作区应用建议补丁后，以标准 Maven verify 复跑：70 单测和 SchedulingBenchmarkIT 1 项通过，产出160行（qa-proposed-scheduling-evaluation.json）。运行该工作区原始 QA 汇总脚本后退出1：期望对照存在不一致。results.csv、summary.md 等真实输出保留在本轮证据目录，均属于“QA修复建议实验”，尚未合入的补丁不代表正式入口已修复，D-AT-22 不记通过。QA 应先接收事务/JSONB补丁和上下文数据源配置，再核对新增资格参考资源对 SCH-01/04/08 预期的影响；不改生产候选逻辑迎合旧预期。

main 尚无 change/notification/audit 实现，CM 分支最新提交仍为配置文档。EventPort、ChangePort 的成功调用链、改期状态/outbox 整体事务尚不能联调；资源故障 API 的 503 与无部分写入测试仅证明缺依赖保护。AN 原有 ActivityRegistrationIT 含 CM 测试替身，其结果只计模块验证。新增 DS 跨端口测试直接使用真实 ActivityPort 和 SchedulingPort，不代表 CM 改期编排验收。

## 课程状态

任务4正式文件为 software-design-task4.md/.docx/.pdf，PR #12 已合入；本轮不改写已评审 Office/PDF。尚无学习通上传回执，课程平台状态记为“尚未确认提交”。截止时间沿用既有记录：2026-10-31 23:59（Asia/Shanghai），不伪造上传、接收或评审记录。

接收人：AN 冯玉琪核对活动版本/预约联合事务；CM 杨瀚霖交付真实 EventPort/ChangePort 及改期编排；QA 汤雨润接收正式评测阻塞证据；PM 冀项阳组织交叉评审。均尚未取得接收确认。
`日志入库仅规范UTF-8和行尾空白，不改变测试结果。`
