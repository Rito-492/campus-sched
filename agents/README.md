# 岗位开发入口

每个人使用自己的 `agent.md`，六份文档共同开发一个系统。公共规则以 `common/` 五份文件为准，个人文档定义自己的范围、任务顺序和交接。

| 岗位 | 工作分支示例 | 开发文档 | 主要工作 |
|---|---|---|---|
| 项目经理 | work/project-manager | [agent.md](project-manager/agent.md) | 项目经理：foundation 工作包 |
| 系统分析员 | work/system-analyst | [agent.md](system-analyst/agent.md) | 系统分析员：activity 工作包 |
| 设计师 | work/designer | [agent.md](designer/agent.md) | 设计师：scheduling 工作包 |
| 测试经理 | work/test-manager | [agent.md](test-manager/agent.md) | 测试经理：qa 工作包 |
| 配置经理 | work/configuration-manager | [agent.md](configuration-manager/agent.md) | 配置经理：change 工作包 |
| 界面设计师 | work/ui-designer | [agent.md](ui-designer/agent.md) | 界面设计师：ui 工作包 |
## 使用方法

在仓库根目录启动开发助手，将下面路径换成本人的路径。`agent.md` 是需要明确指定的个人任务文件；根 `AGENTS.md` 提供共同约定。不要依赖助手自动识别小写单数文件名。

```text
请读取 AGENTS.md 和 agents/project-manager/agent.md，按这份个人文档开始实际开发。
从最新 origin/main 创建或同步工作分支，保留已有本地修改。
读取公共契约，检查已有实现，从下一项未完成任务继续；实际编码、测试并更新本人 handoff。
缺少其他成员依赖时先完成独立工作，列明具体缺口，不用生产 Mock 冒充完成。
```

每份文档末尾有本人身份、分支和任务编号的启动指令，可以直接复制。每人使用独立仓库副本或本人分支的独立工作目录，避免多个助手同时在同一目录切换分支。执行顺序和接收条件见 [协作与集成规程](common/04-协作与集成规程.md)。
