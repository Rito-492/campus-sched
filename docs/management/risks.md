# 风险与待解决事项

核查日期：2026-10-19；主线基准：`2a9e941`；状态均根据本地检查。未建立外部Issue，不虚构编号或负责人接收。

| 编号 | 问题与影响 | 责任人 | 处理及关闭条件 | 状态 |
|---|---|---|---|---|
| PM-R01 | DemoDataSeeder.seed调用声明throws Exception的seedActivity却未处理，默认后端构建失败 | CM 杨瀚霖 | 在事务回调内转换为未检查异常，保持失败回滚；默认test/verify重新执行 | 已实测失败，待修复 |
| PM-R02 | StrategyEvaluator、StrategyResult源码不存在，但SchedulingBenchmarkIT直接引用；全量测试编译仍有依赖缺口 | DS 黄方勇 | 按02 §8交付真实策略实现及类型，不能加空实现或排除该测试 | 静态确认，待交付 |
| PM-R03 | CM种子和QA支撑均在资源之前写入user_resource_scope，违反即时资源外键 | CM、QA 汤雨润 | 在同一事务中先组织/用户/资源，再写资源授权；干净schema实测导入 | 静态确认，尚未运行原装载器 |
| PM-R04 | QA支撑活动与首版本分开自动提交、input_json插入缺jsonb转换，可能在数据准备阶段失败 | QA 汤雨润 | 数据装载统一事务；JSON按PostgreSQL jsonb类型绑定；执行SchemaIT及数据装载 | 静态风险，尚待原用例实证 |
| PM-R05 | Activity/Scheduling/Change/EventPort仅有定义，真实适配器和业务Controller缺失，M2/M3无法贯通 | AN 冯玉琪、DS、CM | 按端口依赖顺序交付真实模块，在统一事务中联调，无循环依赖 | 未交付 |
| PM-R06 | web只有Dockerfile，无package.json/页面工程，无法执行前端规定检查和E2E | UI 杨畅 | 提供构建、类型、测试、页面及锁文件，再按11类页面验收 | 未交付 |
| PM-R07 | 课程实际起止、截止日期与成员真实工时尚未提供，不能计算可靠进度偏差 | PM组织全员核实 | 取得真实日期/工时后更新计划和工作记录；仍保留五周计划假设 | 待核实 |
| PM-R08 | repo-structure.md第93行仍将OpenAPI数量写为38，与v3.0的40项不一致 | CM，PM协调 | 将配置索引说明同步为40；本轮扫描未在需求/UI中发现所查旧审批标识 | 静态扫描确认 |

优先顺序为R01→R02/R03/R04→R05/R06；R07并行核实。PM本模块41项通过不关闭上述集成问题，也不构成30个验收场景全部通过。
