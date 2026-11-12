# 2026-11-07 PM 真实验证证据

本目录记录本轮实际执行，不代替非作者评审、成员接收或发布验收。说明与责任见 [PM 集成报告](../../../docs/management/integration-review-2026-11-07-v1.0.md)。

提交／推送字段是验证时的历史快照；用户随后授权推送，实际同步状态以分支 Git 历史为准。空白检查针对 PM 源码、测试及管理文档；原始日志、浏览器错误上下文及补丁格式保留实际输出。

## 来源与入口

- `source-context.json`：main／PM／QA 完整 SHA、独立合并树、五份本地 PM 代码摘要、工具版本及浏览器专用 schema；口令不保存。
- `source-manifest.json`：交付源码、配置与文档摘要。它用于绑定版本，不表示历史二进制／证据逐行重审。
- `preserved-office.json`：23 份已有 Office／PDF 文件逐字节与开工 HEAD 比对，全部未改。
- `check-summary.json`：各次真实计数、命令范围及未完成结论；`executions.json` 为执行入口与退出码。
- `runtime-cleanup.json`：本轮专用服务已停止，测试端口无监听；`delivery-validation.json` 为修改范围、文档链接和脱敏核查，`artifact-manifest.json` 为最终证据校验清单。
- `reports/`：从实际 JUnit、Vitest、Playwright 与审计输出提取的摘要。JUnit 不包含环境属性；`collect-reports.py` 是实际提取器，零报告直接失败。
- `logs/`：实际输出，仅转为 UTF-8，并隐藏测试口令或认证令牌；不改计数、错误或结论。部分 Java 控制台中文存在原始编码问题，结构化 JUnit 摘要保留可读方法与错误。
- `evaluation/`：最新 QA 原始 160 行测量、原样生成的 CSV 与摘要。原始 `baseCommit` 未注入的事实保留，来源由本目录合并树与执行上下文补充绑定，未改测量数据。
- `screenshots/`：本轮 UI 三视口及 QA 实际生成的页面图；`browser-errors/` 是原 4 项失败的页面上下文。
- `proposals/`：仅供相关所有者接收的诊断／建议材料，没有进入交付产品目录对应模块。

## 结果对照

| 范围 | 通过／总数 | 失败／错误／跳过 |
|---|---:|---|
| 修复前 JSON 负向对照 | 2／19 | 17／0／0 |
| 修复前针对性 HTTP／授权历史 | 0／6 | 6／0／0 |
| 修复后全体单元 | 96／96 | 0／0／0 |
| 修复后 PM 指定 IT | 43／43 | 0／0／0 |
| 交付目录默认 IT 报告 | 82／92 | 0／9／1 |
| 最新 QA 组合默认 IT 报告 | 108／119 | 9／1／1 |
| 同一副本临时解阻 AN，默认 IT | 151／153 | 1／0／1 |
| CM 原样两探针 | 0／2 | 2／0／0 |
| 临时补 CM 详情，QA 权限＋PM 探针 | 14／14 | 0／0／0 |
| 前端单元 | 107／107 | 0／0／0 |
| UI 真实局部／关闭播种后重启 | 5／5、1／1 | 各 0／0／0 |
| QA 原浏览器全体 | 25／29 | 4／0／0 |
| 仅两处 QA 定位建议 | 2／2 | 0／0／0 |

各组独立，重复执行不累计为覆盖率。9 个初始化错误不能解释为只有 9 个业务用例未通过；AN 整类 35 项在原始 Bean 冲突时未执行，解除后才实际运行。定向探针复跑不执行 `clean`，提取时只读取本轮覆盖的类 XML，避免混入旧报告。唯一 skip 为真实 ChangePort 已存在时不适用的缺端口分支。

## 隔离实验的重建顺序

先在独立目录组合 `source-context.json` 记录的 PM HEAD 与 QA HEAD，再复制该文件所列五份 PM 本地代码／测试。产品交付目录未合入 QA 或下列建议。

1. 原组合默认 `clean verify` 得到 108／119。
2. 只应用 `proposals/an-attribution.patch`：为空批准意见补非空事件摘要，两个 AN 测试端口标主 Bean；默认 `clean verify` 得到 151／153，唯一业务失败准确命中空详情。没有改 QA 版本断言。
3. 再应用 `cm-probes-diagnostic.patch`，只运行 `ProductionWorkflowIT#ownerOnlyStaffDoesNotRequireOwnConfirmation+staleChangeReturnsAuthorizedSnapshot`，2 项失败。此诊断源把 `published(true)` 设置为“所有者唯一 staff”，供指定探针使用，不作为替换默认回归的产品测试。
4. 再应用 `api27-cm-proposed.patch`，只运行 `PermissionsIT,ProductionWorkflowIT#staleChangeReturnsAuthorizedSnapshot`，14 项通过。此步骤没有运行或修复所有者自我确认探针；该缺陷仍开放。

Maven 命令使用 Java 21、专用 PostgreSQL，实际本机另带离线缓存参数，完整选择器见 `executions.json`。新数据库必须配置自己的口令，证据不提供认证值。

浏览器实验独立于上述 Java 诊断：始终运行交付目录打包的真实后端，**未使用 AN/CM 临时修复 jar**。`playwright.pm.config.ts` 只把 QA 原配置切至本机 Edge、单 worker、独立输出，并关闭认证轨迹；业务用例原样运行，结果 25／29。

随后在另一套全新演示 schema，仅在 QA 测试副本应用 `qa-locators-proposed.patch`，以包含按钮的方案卡与既有风险条定位，TC-09-2／TC-18-1 为 2／2。该建议没有放宽方案内容、风险和原安排保留的业务要求。产品前端、QA 分支及其原始 4 项失败均没有因此标记已修复。

UI 局部 5 项运行后停止该后端进程；使用原 schema、`--app.demo-seed=false` 新启进程，再在新浏览器执行持久化 1 项，账号／草稿／资源的状态均保留。实际启动日志与两次结果保留。

## 接收与发布限制

AN、CM、QA、UI、DS 的当前动作见管理报告。前端审计仍 8 项（6 moderate／2 critical，严重项在开发测试链）。没有执行干净 Compose 部署或完整备份恢复，release 材料未齐、全组 30 场景未通过，`fullAcceptancePassed` 与 `releaseApproved` 均为 false。没有创建新的远端评审、提交、签字、人时或发布记录。
