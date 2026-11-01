# AN 标准构建与 CM 接入检查 v1.0

日期：2026-10-31；冯玉琪（22220314）；基线 `feb0d70e2ff9da1669eb9c4c3e4beeb592a43ae9`；分支 `work/22220314-activity`。

## 同步及范围

开始时工作区干净。GitHub直连读取失败后，通过此前提供的公开代理只读获取main，提交SHA与GitHub插件查询一致；以`git merge --ff-only feb0d70`安全快进，没有reset、重做PR #11或修改他人文件。PR #11已合并（merge commit 73c2bcc），API-12～23生产实现原样保留。taskbook、common五份及本人agent.md已复核，较前轮无变化。

## 生产依赖核验：阻塞

在本基线`server/src/main/java`中，ChangePort/EventPort只有contract接口与调用方，没有实现类或生产Bean；change/notification/audit生产包未交付。CM此次合入的是Seeder修复和配置文档，不等于改期/事件业务交付。

| 重点 | 本模块现有验证 | 真实CM联调状态及接收条件 |
|---|---|---|
| 取消（API-22） | 关闭待处理改期、释放预约、取消报名、事件失败整体回滚；10次取消/切换竞争不复活 | 阻塞：需CM ChangePort.cancelPending及EventPort.append生产Bean；届时核对真实改期状态、审计/outbox与回执共事务 |
| 报名冻结（API-21） | AWAITING_CONFIRMATIONS/PENDING_APPROVER/PENDING_RESOURCE分别拒绝JOIN/WITHDRAW；WITHDRAWN后解冻 | 阻塞：当前改期状态由测试夹具创建，hasPending由测试端口读取；须由真实API-27～33驱动 |
| 待办（API-13/14） | review HTTP读取及activeChangeId随待处理/终态更新 | 阻塞：需真实pendingId及API-28等改期列表，不能据此认定UI待办闭环；原评审列表范围口径仍待PM明确 |
| 版本切换（ActivityPortAdapter） | 事务内版本/预约/ACTIVE报名版本更新，旧版本退出409且人数保留；失败回滚 | 阻塞：当前由测试直接编排AN/DS端口，尚无CM最终确认调用链 |

提供者：CM杨瀚霖（55231031）。接入时应提供生产ChangePort（pendingId/hasPending/cancelPending）、EventPort.append和真实改期流程；生产端口按既有类型自动注入，不另建公共类型或AN替代实现。没有真端口前保持显式失败和事务回滚。现有503/INTERNAL_ERROR配对仍为PM待决项，本轮不擅自改契约。

## 验证方式与限制

使用标准`server/pom.xml`和Maven Wrapper，未启用隔离profile、源码排除或跳过测试。命令：

```powershell
./server/mvnw.cmd -B -ntp -f server/pom.xml "-Dmaven.repo.local=$anRepo" '-Dtest=ActivityStateMachineTest,ActivityServiceTest' '-Dit.test=ActivityRegistrationIT' verify
```

Java21.0.12.1，真实PostgreSQL17.6，TEST_DATABASE_URL指向本机5440/campus_test；用户名/密码经环境变量传入，不入库。每轮随机独立schema且测试后删除。`$anRepo`仅指定本机target Maven缓存。标准POM编译全部生产/测试源码，执行集合限定AN；不代表全仓库所有测试通过。

ActivityRegistrationIT仍显式导入测试专用Ports：ChangePort为数据库测试实现、EventPort为测试审计/outbox实现。不能将真实数据库或标准构建通过解释为真实CM接入完成。此次不声称M2/M3、通知消费、应用重启或E2E通过。

## 实际结果

标准POM初次verify：25单测+33 PostgreSQL IT通过。补强三种冻结状态和旧版本拒绝后再次verify：**25单测+35 PostgreSQL IT通过，0失败/错误/跳过，退出0**。IT数量含10次竞争回归，非35个不同业务场景。日志及3份JUnit XML/TXT和源码SHA256归档[an-2026-10-31](../../artifacts/test-results/an-2026-10-31/)。Seeder原编译阻塞已解除；真实CM集成仍阻塞。接收人CM/PM/QA/UI；尚未接收。
