# 项目经理执行与集成核查记录

核查日期：2026-11-07。岗位：冀项阳（55230223），PM-01～05；本轮代码与验证由开发助手执行，成员接收尚未确认。

本轮完成公共请求解析和资源自动授权历史两项修复，并补齐回归、计划和交接记录。PM 指定回归通过；默认全量、QA 候选和完整发布验收仍有明确未通过项，不能登记整组完成。

## 1 基准与阅读范围

以下提交／推送状态为本轮验证完成时的历史快照；用户随后授权推送，后续同步状态以该分支的 Git 提交历史为准。

- 实际主线：`16de4a507aa434df6505a49f67619990bc2926c2`。
- PM 分支：`work/55230223-foundation`；开工及本轮交付的已提交 HEAD 为 `984892cefc04411c463e92411d7c09666c03fbfe`。本轮新增修改保留在本地，未提交、推送或合并。
- QA PR #16：`6596a7dc7e0a1851861d268136e82cd5f63e61a5`。与 PM HEAD 的独立合并树为 `bb90b625ab197258928c04c8f291ff0afd94b221`，再复制本轮五份 PM 源码／测试文件；没有把 QA 分支合入交付目录。
- [现有 PM PR #20](https://github.com/Rito-492/campus-sched/pull/20)尚不包含本轮未提交修改。本轮只进行本地复核，没有提交新的 GitHub 评审或代替非作者批准。

核查涵盖任务书、五份公共基线、六岗位分工、各模块交接及相关需求／设计／配置／测试资料；后端 foundation/auth/contract、活动与报名、资源与调度、改期与通知、种子和配置；前端客户端、会话、路由、领域规则、页面与组件；单元／数据库／浏览器验收、演示数据、OpenAPI、迁移和部署脚本。历史测试证据用于比对，不作为本轮执行结果；课程原件和已有 Office 文件保持原样。源码摘要与原件保全清单随本轮证据保存。

## 2 本轮实际修复

### PM-01：请求中的整数与枚举严格按契约解析

旧公共 Jackson 配置会将 `expectedLockVersion: 0.9` 截断为 `0`，允许修改锁版本为 0 的草稿；资源数量 `2.9` 会落库为 `2`。身份、角色、权限等枚举也接受数字序号或数字字符串，例如数字身份会被解释为 `TEACHER`。

`FoundationConfiguration.contractJson()` 现关闭小数到整数的强制转换，启用数字枚举拒绝。上述请求返回 `400 VALIDATION_ERROR`，不创建或修改业务数据、授权历史及命令回执。正常整数、具名枚举、允许为空的资源容量及原有缺字段／未知字段／重复键等规则保持兼容；未修改 DTO、枚举、端口或数据库结构。

新增 `ContractJsonTest` 实际执行 19 项：7 种小数请求、5 类枚举的数字及数字字符串、有效输入兼容和既有严格解析规则。HTTP／真库回归进一步检查非法授权、小数库存及草稿锁版本不会产生写入。

### PM-02／03：新资源自动授权与历史共同提交

旧 `AccountPortAdapter.grantResource()` 只插入资源范围，未记录 `authorization_change`。现在由 PM 端口读取完整授权前值，新增范围成功后保存完整后值及创建者操作身份，复用现有 `MANDATORY` 事务。重复授权直接返回，不重复追加历史。

真实 API-08 回归核对资源、自动范围、完整前后授权、首次命令回执及相同幂等键重放；另验证重复端口调用不增加历史，异常时授权与历史一并回滚，随后原操作可成功重试。DS 仍先锁创建者，再创建资源并调用 AccountPort，未跨模块直接写表。

修复前的负向对照：JSON 19 项中 17 项失败；6 项针对性 HTTP／历史检查均失败。修复后全部通过，详见证据中的 `contract-before.json`、`integration-before.json` 和 PM 回归摘要。

## 3 验证结果

Java 21.0.12.1、Maven Wrapper 3.9.11、PostgreSQL 17.6、Node 22.23.0。数据库为本机专用 `campus_test`，各 IT 使用独立随机 schema；浏览器使用真实前后端、固定 e2e 时钟和独立演示数据。未使用 H2；测试端口仅保留在原模块测试，PM 全生产链不替换业务端口，未把替身当作生产实现或使用假成功页面。

下列各行分别计数，不能累计为独立用例覆盖率。初始化错误可能阻止整类执行，不能将其当作只缺一个业务用例。

| 范围 | 单元 | 数据库 IT | 退出码与结论 |
|---|---:|---|---|
| PM 指定生产模块集合 | 96／96 | 43／43 通过，0 跳过 | 0，通过 |
| 交付目录默认 `clean verify` | 96／96 | 92 条报告：82 通过、9 初始化错误、1 跳过 | 1，未通过 |
| 最新 QA 与 PM 修复独立组合，默认 | 96／96 | 119 条报告：108 通过、9 失败、1 初始化错误、1 跳过 | 1，未通过 |
| 同一副本仅临时解除 AN 前置问题，默认 | 96／96 | 153 项：151 通过、1 失败、1 跳过 | 1，失败命中 CM 版本冲突详情 |
| CM 独立探针，CM 原实现 | 96／96 | 2／2 失败 | 1，复现自我确认及空冲突详情 |
| AN 解阻副本再临时应用 CM 详情建议 | 96／96 | 14／14 通过 | 0，仅证明建议有效 |

PM 指定集合为 `FoundationIT,PublishedModulesIT,ProductionWorkflowIT,StandardMavenSchemaIT,DemoDataSeederIT,OutboxClaimIT`。43 项覆盖公共模块、真实模块权限变化、无业务替身的创建／改期／通知／取消、schema、种子及 outbox 领取。原有同键并发一次执行、不同键锁竞争、停用旧会话、授权替换和事务回滚均保留。

默认 9 个初始化错误中，8 类是尚未接收 QA 修复的数据装载外键顺序，1 类是 AN `ActivityRegistrationIT` 的测试端口与生产 Bean 冲突。最新 QA 已修复装载；组合后的 9 项失败均停在 AN 合法空审批意见返回 400。AN 两处临时解阻后，35 项模块测试实际执行，唯一业务失败是最新 QA 正确发现的 CM 详情缺失。唯一跳过为 `missingChangePortRejectsActivityLockWithoutPartialWrites`：真实 ChangePort 已存在，该缺端口分支不适用；不将跳过记为执行通过。

| 前端／QA 范围 | 本轮实际结果 |
|---|---|
| UI `types:check`、`typecheck`、`test:unit`、`build` | 类型一致，107 项单元通过，生产构建及包体积／fixture 隔离检查通过 |
| QA `test:contract`、`typecheck` | 40 API、9 组枚举、15 DTO 和类型检查通过 |
| QA 原始调度测量及 `evaluate` | 160 行真实结果生成成功，8 场景 × 2 策略 × 10 次测量，保留原始数据、CSV 和摘要 |
| UI 真实局部联调 | 5／5 通过；登录退出、完整授权与启停、资源资格、草稿创建／编辑／预览及刷新 |
| UI 关闭播种后实际重启服务 | 1／1 通过；草稿版本、资源资格、账号身份及停用状态保留 |
| 最新 QA 原有浏览器用例，三种窗口尺寸 | 29 项中 25 通过、4 失败，0 跳过 |
| 仅在独立副本修改两处 QA 定位后复验 | 2／2 通过，不改产品页面或后端 |
| UI `npm audit --json` | 8 项：6 moderate、2 critical；critical 位于 vitest/tinypool 开发测试链，仍待 UI 处理 |

浏览器连接本机 Vite 与真实 Spring/PostgreSQL，未运行 Compose；不能据此声明镜像、Nginx 或干净安装验收通过。UI 局部联调包含 1366×768、1920×1080、390×844 截图与横向溢出检查。QA 原始截图／错误上下文与结果归档，认证轨迹关闭。

## 4 跨模块结论与下一接收条件

| 责任人／任务 | 具体问题、复现与接收条件 |
|---|---|
| AN 冯玉琪 | `ActivityService.decide()` 将合法空 APPROVE 意见直接用作事件摘要，EventPort 拒绝并回滚为 400；数据库 9 项、浏览器 TC-03-1／TC-12-1 均受影响。为事件补非空摘要，保持空批准意见合法；模块测试的两个替身均明确主 Bean，原 35 项须执行。建议补丁只在诊断副本使用。 |
| CM 杨瀚霖：API-27 详情 | 所有者已校验后，两种陈旧版本仍返回 `details={}`。common/02 §3 要求在适用的 409 中至少提供准确的当前 `versionNo`、`lockVersion` 或 `status`。建议补丁使最新 PermissionsIT 13 项及 PM 探针 1 项通过，探针同时确认非所有者 403 无详情、无改期／回执部分写入。 |
| CM 杨瀚霖：所有者确认 | 所有者是唯一 staff 时，真实探针期望 `PENDING_APPROVER`，实际 `AWAITING_CONFIRMATIONS`。requiredStaff 应排除所有者，通知仍保留所有者；还须验证其他工作人员继续逐人确认。本轮未将该缺陷标记已修复。 |
| QA 汤雨润：#16 断言 | 最新 `6596a7d` 已正确检查两类陈旧请求的至少一项当前状态，保留 GET／无新版本检查；待办快照修订也保留。技术复核通过这两项修订，整个候选仍因生产缺陷未通过；本轮未提交正式远端审核。 |
| QA／UI：浏览器定位 | TC-09-2 把 `alternative-0` 按钮当作整张方案卡，正文和“当前排序首选”在其父卡片；TC-18-1 的文字正则未匹配实际“当前资源受故障影响”。建议限定到包含该按钮的方案卡及既有 `risk-banner`，两项真实复验通过。建议不降低业务断言，交 QA 接收，UI 配合保持稳定定位。 |
| UI 杨畅 | 处理实际依赖审计并做兼容回归；构建通过不关闭审计问题。PM 未自动升级前端依赖或锁文件。 |
| DS／CM／QA | V2 迁移归属与已存在真实端口时的跳过边界仍需确认；技术检查不代替接收签字。访问矩阵 B／D／I 仍按既有协调方案待接收。 |
| CM 发布、QA 验证、PM 核对 | release 仍只有占位文件；本机未找到 Docker CLI，没有执行干净 Compose 部署、备份／完整恢复。30 项验收、发布说明／manifest、实际发布 commit 和证据齐全后再评价 M4。 |

当前 PM-01～03 的本轮修复与指定回归完成；PM-04 已完成本轮契约复核、真实集成和归因，整组接收仍待 AN／CM／QA／UI 的独立修复。PM-05 已更新 Markdown 计划、实际工作记录、接口登记、风险、决定和交接。实际人时、剩余估算、成员容量、课程截止回执及签字未提供，均不填造。

## 5 复跑与证据

在仓库根目录，配置 Java 21 及专用测试库的 `TEST_DATABASE_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD/DEMO_PASSWORD` 后：

```powershell
./server/mvnw.cmd -f server/pom.xml clean verify
./server/mvnw.cmd -f server/pom.xml "-Dit.test=FoundationIT,PublishedModulesIT,ProductionWorkflowIT,StandardMavenSchemaIT,DemoDataSeederIT,OutboxClaimIT" clean verify
```

本机实际命令另带 `-o "-Dmaven.repo.local=$env:MAVEN_USER_HOME\repository"`，使用已解析的隔离 Maven 缓存；未添加跳过测试参数。前端在 `web` 依次运行 `npm ci`、`npm run types:check`、`typecheck`、`test:unit`、`build`；QA 在 `qa` 运行 `npm ci`、`test:contract`、`typecheck`。浏览器命令、单独副本的构造及每次命令退出码见机器可读记录。

证据目录为 [pm-2026-11-07](../../artifacts/test-results/pm-2026-11-07/README.md)，入口为 [check-summary.json](../../artifacts/test-results/pm-2026-11-07/check-summary.json)。其中保留修复前负向对照、各次独立计数、日志、来源摘要、建议补丁及测量数据。定向 IT 报告只统计本次实际选中的类，排除上轮未清理的历史 XML；不把实验、重跑或跳过累计为覆盖率。

本轮未覆盖课程原件或已有 Office 文件，未创建成员接收、实际人时、评审签字、发布标签或课程提交回执。下一接收人与条件如上，状态为“尚未接收”。

测试结束后已关闭本轮专用后端、前端和 PostgreSQL，8080／5173／5547 无测试监听；保全检查确认 23 份已有 Office／PDF 与开工 HEAD 内容相同。清理记录和最终交付核查随证据保存。
