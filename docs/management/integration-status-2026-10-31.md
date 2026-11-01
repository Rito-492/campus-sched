# 主线集成核查与下一交付条件

核查人：冀项阳55230223。日期：2026-10-31。工作分支`work/55230223-foundation`已保留本地修改并从`34dc4d5`安全快进至`origin/main=feb0d70`，未改写main或其他成员分支。

## 已接收的实际内容

PM公共工程、认证授权、幂等及AccountPort已在主线；DS资源/调度、StrategyEvaluator/Result和SchedulingPort已在主线；AN的API-12～23及ActivityPortAdapter已在主线；CM种子编译、资源授权导入顺序与不重放撤销授权的修复随PR #10进入主线。本轮不再将这些已交付内容写为“文件不存在”。

40项HTTP契约中29项有主线Controller，CM的API-26～36共11项未实现。已有控制器的成功路径仍可能依赖CM端口；源码存在不等于整场验收通过。web仍只有Dockerfile，无package.json、页面或锁文件；release仍无可执行发布说明与manifest。

## 本轮工程与代码

- 新建密码规则按NFR-08落实到DTO、Service、OpenAPI和公共契约，并补6项纯逻辑测试及1项真实HTTP/落库回归。
- 标准Failsafe通过`.mvn/integration-test.properties`启用test配置，并让Spring数据源及Flyway解析QA扩展发布的随机schema。使用外置属性文件保留Spring运行期占位符，不在Maven配置阶段将其提前插值为空。各模块DynamicPropertySource仍优先，不回落到开发库或public。此用法依据[Apache Failsafe系统属性说明](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/system-properties.html)。
- PublishedModulesIT显式装配真实PM/AN/DS及PostgreSQL，不装配CM、不创建端口替身；验证资源创建后AccountPort范围、活动内部快照与只读预览，以及提交/故障缺EventPort时503、状态和回执整体回滚。
- StandardMavenSchemaIT沿用QA原有DbTestSupport但不装载其有缺陷的demo fixture，实际检查Spring和Flyway使用相同随机schema及22表。不是对QA数据装载通过的宣称。
- 默认test/verify不排除任何验收类；历史pm-isolated仅保留PM独立源码范围，新增两个依赖已发布业务/QA支撑的集成类只在标准构建执行。当前推荐使用标准POM按明确测试集合复验，不把隔离结果当全量。

具体命令、计数和源码快照以`artifacts/test-results/pm-2026-10-31/`的日志和check-summary.json为准。单元、模块集合、全量默认verify分别统计，不合并重复运行结果计算通过率。

## 全量失败与责任分配

本轮默认verify已实际执行。八个QA acceptance/benchmark测试类在数据准备时因`user_resource_scope`先于`resource`写入，触发`fk_user_resource_scope_resource`，尚未进入各自场景断言。共同失败入口为`support/DbTestSupport.insertReferenceTables`，不能把八项初始化错误等同八个业务场景均已验证。该文件归QA，本轮未修改。

QA后续还需核实活动/首版本的共同事务及jsonb绑定。先后顺序是：组织/用户基础→资源→用户资源范围→活动和版本等在约定事务内装载，随后重跑原验收。任何修复均保留真实数据库约束，不能删除外键、跳过失败类或伪造数据使其变绿。

CM尚未交付ChangePort/EventPort生产实现及改期、通知、审计模块，真实M2首次流程和M3改期不能闭环。模块IT中既有的CM测试替身仅用于隔离验证，不能计算为生产接收或完整通知投递。UI工程未交付，未执行typecheck/test:unit/build、浏览器E2E与视口验收；未运行正式八场景策略评测或发布复现。

## 节点结论与接收次序

| 节点 | 本轮结论 | 继续条件 |
|---|---|---|
| PM-01～03 | 公共构建可编译并运行真实检查；本轮密码和测试schema绑定已验证 | 持续回归，交叉评审本次公共改动 |
| M0 | 后端默认test已解除旧编译阻塞；全节点仍未通过 | QA完成装载修复；UI提供可构建/可测工程 |
| M1/LCA | 已有Account/Activity/Scheduling三类实现；未通过全组架构门槛 | CM补齐两类真实端口，验证无循环及共同事务 |
| M2 | 未通过完整真实首次链 | EventPort、ChangePort、UI和数据均到位后验证创建至取消 |
| M3/IOC | 未通过 | CM改期/确认/通知/审计真实实现和成功/冲突/异常数据库证据 |
| M4/发布 | 不放行 | 30场景、默认全量检查、UI/E2E、160行策略测量、干净部署及恢复达标 |

下一接收顺序：QA数据修复、CM两类端口、UI工程并行；PM接收后重新运行默认入口和真实主线。AN/DS按各自责任接入、修复；不由PM接管其全部工作包。没有新增基线标签、发布包、课程签字、工时或接收回执。

## 同日后续状态更新

上文保留为feb0d70时点事实。最新main已为c4f3d41并含DS PR #15；QA PR #16和UI fork PR #17已交付候选，PM在独立组合中真实复验，详见[最新候选接收记录](integration-intake-2026-10-31-v1.0.md)。主线+PM默认verify为76单元通过、113 IT中8错误/1跳过；候选组合为76单元通过、142 IT中24失败/2错误/1跳过。QA装载错误在候选解除，160行专项评测通过；UI78项unit/构建及5项真实局部浏览器、1项重启检查通过。CM两类生产端口和11接口仍缺，QA/UI未合入main，完整验收与发布未放行。
