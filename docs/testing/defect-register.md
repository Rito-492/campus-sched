# 缺陷登记 v0.2

> 归属：测试经理（55230316）。格式依据课程课件《6.2 缺陷记录与测试评估》：缺陷标识、用例标识、测试人员、缺陷等级、打开日期、状态 + 概要/再现步骤/隔离。
> 等级：A（关键，严重影响系统运行）/B（高，影响系统运行）/C（中，不影响运行但必须修改）/D（低，所提建议）。状态流转：open→assign→test→close（修复后回归通过才 close）。
> **如实登记**：以下仅登记本轮实际重现的问题；未运行的产品业务场景不作为缺陷。Seeder PR 副本差异尚待PM/CM指定唯一权威后按该来源验收，不重复登记同一问题。

## 缺陷清单

| 缺陷标识 | 用例标识 | 测试人员 | 等级 | 打开日期 | 状态 |
|---|---|---|---|---|---|
| DEF-QA-2026-01 | D-AT-24 / 默认构建探测 | 汤雨润（55230316） | B | 2026-10-26 | assign（CM） |

## 明细

### DEF-QA-2026-01
- 概要：当前 `origin/main=34dc4d5abd3d859e3a63ff476228593555f2e03b` 的默认后端编译被 CM Seeder 阻塞；`seed()` 调用声明 `throws Exception` 的 `seedActivity()` 未处理受检异常。
- 再现步骤：在仓库根目录运行 `./server/mvnw -f server/pom.xml -DskipTests compile`。
- 隔离：Linux 工作区；JDK 21 Maven Wrapper（Maven 3.9.11）；不需要数据库或密码，稳定复现；停止于编译阶段，尚未运行任何测试。
- 预期 vs 实际：预期默认源码编译成功；实际 `DemoDataSeeder.java:118` 报 `unreported exception java.lang.Exception`，退出码1。
- 证据：`artifacts/test-results/qa-2026-10-26/default-compile.log`（本轮真实输出归档）。
- 责任接收人：CM 杨瀚霖（55231031）；由CM修复并提交后QA在唯一指定来源上复验。回归记录：待处理。

Seeder PR #8/#10 的同一源码副本问题不重复建立缺陷记录；其最新review要求决策与密码问题见 handoff，本轮未合入任一副本。

```markdown
### DEF-xxx
- 概要（Summary）：……
- 再现步骤（Steps to Reproduce）：1、… 2、…
- 隔离（Isolation）：环境/版本/数据/是否可稳定复现/临时规避方式
- 预期 vs 实际：……
- 证据：日志/截图路径（artifacts/…）
- 责任接收人：……；回归记录：……
```
