# 主线集成与正式评审

2026-11-13，项目经理冀项阳（55230223），工作包 PM-01～05。PM 分支同步至 `db018298359dac00c687783c6615001033270762`。PR #20 的输入校验、资源自动授权历史和管理记录已通过交叉评审。PR #22 的空批准意见和 AN 测试端口冲突修复已合入。[PR #20](https://github.com/Rito-492/campus-sched/pull/20) · [PR #22](https://github.com/Rito-492/campus-sched/pull/22)

## 独立回归结果

Java 21、Maven Wrapper 3.9.11、PostgreSQL 17.6。每次数据库回归使用独立 schema。组合候选从实际 PR 提交构造。

| 检出及范围 | 单元通过 | 集成通过／总数 | 失败／错误／跳过 |
|---|---:|---:|---:|
| 主线 PM 指定集合 | 96 | 43／43 | 0／0／0 |
| CM #21 原提交 `ade8c2b`，生产链与发件箱 | 82 | 6／6 | 0／0／0 |
| 主线与 CM #21，独立的权限及存量探针 | 101 | 2／2 | 0／0／0 |
| QA #16 原提交 `ad1151f`，权限、身份与活动审批 | 96 | 23／24 | 1／0／0 |
| 主线与 QA #16、CM #21，默认后端验收 | 101 | 157／158 | 0／0／1 |
| 主线与任务 5 #23，定向边界回归 | 96 | 0／1 | 0／1／0 |
| 主线与 #16、#21、#23，定向边界回归 | 101 | 7／7 | 0／0／0 |

QA #16 原提交的 1 项失败包含两条分别执行的陈旧请求断言。组合默认测试唯一跳过的是在生产 ChangePort 到位后停用的缺端口回退测试。任务 5 单独组合的 1 项错误发生在装载阶段，7 个测试方法都未执行。Node 22 的前端构建、QA 类型、40 API／9 枚举／15 DTO 契约检查通过，四资源策略评测产生 160 条测量。

## GitHub 评审

| PR | 结论 | 证据和接收条件 |
|---|---|---|
| CM #21 | 放行 | 原提交通过独立回归。主线候选以真实 HTTP 和数据库验证存量所有者不能自签，撤回重建直接待复审，以及陈旧资源复核先鉴权、仅授权者获当前详情。任务 5 的两类工作人员快照用例也通过。[批准记录](https://github.com/Rito-492/campus-sched/pull/21#pullrequestreview-5885967185) |
| QA #16 初次复审 `ad1151f` | 必须改 | 待办快照、两种陈旧版本断言和资格覆盖统计均已按此前意见修正。整套浏览器 29 项中 28 项通过，TC-12-1 留下占用场地 3102 和设备的抢占预约，导致后续 TC-10-1／23-1 使用场地 C。原用例在干净库单跑成功。隔离副本在抢占测试结束后经 API-21 取消测试活动，原套件三视口 29／29 通过。作者后续提交 `8f30a49`，见下方复审。[初次复审](https://github.com/Rito-492/campus-sched/pull/16#pullrequestreview-5885967530) |
| QA 任务 5 #23 | 必须改 | 27 项设计检查自测通过。24 组 138 条用例为设计覆盖。Word 原稿通过实际分页并检查了 50 页版式。UC-12-TC06 须检查 API-27 的授权冲突详情，保留重新 GET 和无写入断言。任务 5 的真库边界测试依赖 #16 的装载修复和 #21 的产品修复，待合入后用最新主线复跑。Word 第 43 页的尾表可随重生成调整。[正式评审](https://github.com/Rito-492/campus-sched/pull/23#pullrequestreview-5885967754) |

## QA 作者修订复审

QA #16 的 `8f30a49` 由活动所有者经 API-21 取消抢占预约并核对 `CANCELLED`，Playwright 默认使用一个进程。与 #21 的 `ade8c2b` 组合后，原版浏览器 29 项全部通过。关闭演示数据初始化并重启后端，API 和新浏览器刷新仍显示版本 2、场地 3102 和上海时间 15:00。[29 项明细](../../artifacts/test-results/pm-2026-11-13/qa-8f30a49-browser.json) · [重启证据](../../artifacts/test-results/pm-2026-11-13/qa-8f30a49-restart.json)

组合的默认后端回归 101 项单元通过，158 项集成中 157 通过、1 跳过，退出码 0。QA 原提交定向复跑的两种陈旧请求均暴露主线空详情。抢占用例的评审项已关闭，后端接收待 #21 合入后以实际主线重跑。#23 的提交与评审要求未更新。[QA 最新复审](https://github.com/Rito-492/campus-sched/pull/16#pullrequestreview-5887771583) · [后端计数摘录](../../artifacts/test-results/pm-2026-11-13/maven-results-8f30a49.txt)

## 接收顺序

配置经理跟进 #21 的合并。测试经理在 #21 进入主线后复跑 #16 的默认后端验收，并修订 #23 的设计源、Markdown 和 Word。界面设计师与测试经理继续补齐 N-AT-02 的 UI 证据，目前八种直接 HTTP 组合已验证，UI 只有教师专用组有证据。

本机未执行干净 Compose 部署。D-AT-24、发布说明、发布清单和恢复演练仍由配置经理准备、QA 复验，PM 核对实际发布提交。课程任务 5 尚未接收，平台提交和回执仍待实际发生。

## 回归命令与记录

后端从各表项对应的检出运行，配置 Java 21、测试库连接和演示密码后执行。组合候选在 QA 提交上运行 `git merge --no-commit --no-ff ade8c2b`。

```powershell
./server/mvnw.cmd -f server/pom.xml "-Dit.test=FoundationIT,PublishedModulesIT,ProductionWorkflowIT,StandardMavenSchemaIT,DemoDataSeederIT,OutboxClaimIT" clean verify
./server/mvnw.cmd -f server/pom.xml clean verify
./server/mvnw.cmd -f server/pom.xml "-Dit.test=PermissionsIT#tc2001And02_staleVersionProtection" clean verify
npm --prefix web run build
npm --prefix qa run test:contract
npm --prefix qa run typecheck
npm --prefix qa run test:e2e -- --config=pm-review.config.ts
```

本机浏览器使用生产 Vite 预览、真实 Spring 服务及专用 PostgreSQL，Playwright 本地配置继承原配置，只更换 Edge 渠道和结果输出位置。QA 提交的单进程设置随原配置生效。原始浏览器运行的配置说明与日志见 [QA 作者记录](https://github.com/Rito-492/campus-sched/blob/8f30a49123ee085a8ab4e29b02be93eccc7cf104/artifacts/test-results/qa-2026-11-13/e2e/summary.md)。

[计数、来源与执行范围](../../artifacts/test-results/pm-2026-11-13/check-summary.json) · [前轮 Maven 原始计数摘录](../../artifacts/test-results/pm-2026-11-13/maven-results-original.txt)

QA 可在 `ad1151f` 的隔离检出中用 `git apply --unidiff-zero <补丁路径>` 重现[抢占预约清理](../../artifacts/test-results/pm-2026-11-13/e2e-qa-cleanup-proposed.patch)。CM 探针可在主线与 #21 的合并候选用 `git apply <补丁路径>` 重现。[CM 探针](../../artifacts/test-results/pm-2026-11-13/cm-authorization-legacy-probes.patch)
