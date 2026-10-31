# 集成日志

## 2026-10-31：标准工程复验与真实模块接收

从`34dc4d5`安全快进至最新`origin/main=feb0d70`，保留本地计划v3.2～v3.5、所有Word及已有handoff追加。AN活动、DS调度及CM种子修复均已进入主线；Account/Activity/Scheduling三类实现已有实际接收代码。Change/Event及CM的11个HTTP操作未交付，web工程未交付。

本轮补齐NFR-08新建密码最少8字符规则、OpenAPI的503缺依赖信封及标准Failsafe随机schema绑定；新增PM/AN/DS真实模块联测，无CM替身。密码回归在旧代码上实际出现400期望/201实际的失败，修复后通过；schema绑定使用标准入口实测同一随机schema及22表。

指定模块标准verify执行76项单元＋30项真库IT通过。默认全量verify另行实际运行，QA acceptance/benchmark八类在fixture装载阶段出现资源外键错误；不关闭原场景或用新测试代替它们。原始命令、全部计数、通过/失败摘要及源码清单见`artifacts/test-results/pm-2026-10-31/`。责任与节点结论见[集成状态](integration-status-2026-10-31.md)，合同影响及AN待决建议见[协调记录](contract-coordination-2026-10-31.md)。

前端typecheck/test:unit/build、E2E、正式八场景策略评测、安装恢复和30场景整体未通过；不创建发布标签或批准合并。源码及本轮管理材料可交叉评审，其他成员仍负责本人生产实现和缺陷修复。

## 2026-10-19：PM-01～05本地工作包

成员：冀项阳55230223。公共基线v3.0，实际`origin/main`为`2a9e94143296f5972e91bc2b7848b711762e01f4`。先fetch核实，再将没有额外提交的本人分支同步主线，切换`work/55230223-foundation`；保留三份用户Word修改及三份未跟踪历史Word。没有提交、推送、PR、合并或发布。

### 实际接收与实现

DS的V1迁移、活动纯状态机、调度区间/峰值/排序纯逻辑、CM环境/种子框架及QA测试源码已在主线，可检查文件存在。接收仅表示文件可读取，不代表全部编译或业务验收通过。PM复用V1完成真实库迁移和本模块测试。Activity/Scheduling/Change/Event生产端口适配器仍缺；AccountPort已在本轮实现。40API归属与调用方见[接口登记](api-register.md)，用法见[公共模块接入](foundation-guide.md)。

### 已运行检查

| 命令/范围 | 实际结果 | 证据 |
|---|---|---|
| 默认Maven test/verify | 构建失败，种子导入编译错误，未开始业务测试 | artifacts/test-results/pm-2026-10-19/full-test.log、full-verify.log |
| Maven -Ppm-isolated clean verify | 22单元+19真实PostgreSQL IT通过，0失败/0跳过 | 同目录pm-verify.log及五份测试摘要 |
| node qa/scripts/check-contract.ts | 40API、9组枚举、15DTO全部通过 | 同目录contract-check.log |
| diff/新增文本结构与本地链接检查 | 结果见交接与check-summary，非产品验收 | 同目录check-summary.json |

环境：Java21.0.12.1、Maven3.9.11、Spring Boot3.5.16、Node24.19.0、PostgreSQL17.6；测试库为本轮临时实例的campus_test，随机schema经Flyway迁移，测试结束删除。未使用Docker替身、H2或生产业务Mock。当前机器没有Docker命令，web工程未交付，因此前端三项检查、浏览器E2E、策略评测及干净环境部署未执行。

### 明确阻塞与修复建议

1. **CM / DemoDataSeeder.java第118行**：seed调用`seedActivity`，后者声明`throws Exception`而调用链未处理。默认构建实际报“未报告的异常java.lang.Exception”。建议收窄为JSON序列化异常，在seedActivity中转换为IllegalStateException；保持业务异常从TransactionTemplate回调抛出，使导入整体回滚。PM未改动CM文件。
2. **CM及QA / 资源授权导入顺序**：两份导入器都在resource创建之前写入user_resource_scope，而V1该外键即时检查。建议组织→用户（暂不资源范围）→资源→user_resource_scope→活动/版本/预约/报名，整批共事务。此项来自源码和DDL静态核查，原导入器未实际运行，不记为已复现测试失败。
3. **QA / DbTestSupport.insertActivities**：活动和首版本目前由无显式事务的JdbcTemplate分别写入；延迟外键仍在各自提交时检查。input_json直接绑定字符串也需使用jsonb转换或PGobject。建议数据装载统一事务后实际执行SchemaIT/数据装载。此项是静态风险，尚未在原测试中实证。
4. **DS / StrategyEvaluator.java及StrategyResult.java**：源码不存在，SchedulingBenchmarkIT有直接import。即使修复种子代码，全量testCompile仍需这些真实类型；不得以空策略或排除该测试代替交付。本次默认构建先在主源码阶段停止，未把后续推断误记为编译日志。
5. **AN/DS/CM/UI**：业务Controller与四类生产适配器、web工程待交付；M2首次预约、M3改期、30场景和UI验收均未完成。具体下一动作见详细计划§7。
6. **CM / docs/configuration/repo-structure.md第93行**：接口数量仍为38，需同步40。本轮对需求/UI扫描`PENDING_TEACHER`、`teacher-decision`、`teacherDecision`及旧角色常量，未发现所查标识，不据此宣称所有语义已逐项验收。

### 验收与接收状态

PM检查覆盖D-AT-01账号部分、D-AT-08幂等基础及N-AT-01/05/06的公共策略部分。尚未接入各业务审核入口，不能登记这些跨模块场景整体通过；其余D-AT/N-AT均未新增通过结论。接收方AN/DS/CM/UI/QA均尚未接收，尚无交叉评审记录。
