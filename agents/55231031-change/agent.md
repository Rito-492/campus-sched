# 杨瀚霖（55231031）：配置经理开发任务 v3.0

编制日期：2026年10月13日。从执行时最新的 origin/main 开工（历史初始基准 a28dcee），新版三角色及身份/资格规则见common五份文件。只执行本人的工作包，不读取旧任务包。

## Agent 执行入口

本文件是 杨瀚霖（55231031）的个人开发指令。收到“按本文件开发”后，应实际修改代码、运行检查并更新交接；按 CM-01～05 推进，不停留在分析或再写计划。

路径相对于仓库根目录。先定位包含 AGENTS.md 和 taskbook.md 的根目录，读取根 AGENTS.md、taskbook.md、agents/common/ 五份文件，再执行本人任务。公共契约规定接口和业务，个人文档规定责任；发现冲突列明并按公共契约执行，不自行改字段或枚举。

可编辑路径：server/src/main/java/com/campus/events/ 下 change、notification、audit、dev 及同名测试包；application 配置（不含 DS 的 db/migration）；Docker/Compose、infra/、scripts/、CI、环境模板；docs/configuration/、release/。

共享 DTO/端口定义变更交 PM，数据库结构变更交 DS；他人模块缺陷提供复现及建议。保留已有未提交修改，课程原件和用户 Word 文档不覆盖。

### 每轮执行流程

1. 检查分支、未提交修改和本人 Git 身份，获取最新 origin/main，在本人 work 分支同步；不存在时从 origin/main 创建，不自动 reset。
2. 检查已有代码、测试和本人 handoff，从下一项未完成任务继续，已有正确实现继续使用。
3. 实际完成一项可验证功能及必要测试；依赖未到先做独立工作，测试替身只放测试，生产不伪造成功。
4. 运行本人检查，记录真实结果；缺依赖列具体文件/端口、提供者、影响任务及仍可执行任务。
5. 更新 docs/configuration/handoff.md，向用户说明已完成任务、改动、验证、遗留依赖和下一步。不要自动联系或启动其他成员聊天；提交、合并、推送按用户授权执行。

第一批独立工作：先交开发/测试隔离数据库、环境模板和加载入口，按 PM 共享端口尽早实现 EventPortAdapter 与独立 ChangePortAdapter。种子接 QA 数据及 DS schema，改期闭环待 AN/DS 端口到位。

本人验证入口：验证 Compose 配置和隔离环境；后端运行 ./mvnw.cmd test 与真实库 ./mvnw.cmd verify 检查改期/通知/审计；检查脚本按 common/04 提供模块选择和退出码，最终在干净环境启动验证发布。 入口尚不存在时先由责任人建立；未执行、零测试或缺真实库不能记通过。

交接内容：优先向全员交环境，向 AN/PM 交 EventPort/ChangePort，向 UI/QA 交改期/通知接口，向全员交检查入口与发布说明。

handoff 至少包含成员/学号、任务 ID、实际 main 基准和本人分支、文件/API/端口、实际命令及结果、证据、未完成任务、依赖及提供者、下一接收人。未收到接收反馈写“尚未接收”。

## 1 范围与依赖

分支：`work/55231031-change`。负责change/notification/audit/dev、环境/种子/配置/部署/CI、发布文档。

依赖：PM工程/DTO/AccountPort，DS迁移/SchedulingPort，AN ActivityPort，QA数据。数据库环境和EventPort优先交付，不能等改期整体完成。

## 2 按顺序执行的任务

### CM-01 环境和导入

交付：Compose开发db、独立compose.qa.yaml、application配置、.env.example、load-env.ps1、dev种子导入。

验收：开发/测试库与卷隔离；白名单环境解析；新版账号资格导入幂等；固定Clock仅e2e；先交全员启动入口。

### CM-02 审计/outbox/通知

交付：EventPortAdapter、audit、outbox消费/API-34～36；先交独立ChangePortAdapter pendingId/hasPending/cancelPending。

验收：业务回滚事件共回滚；投递失败不撤销业务；eventId+recipientId唯一；读别人通知拒绝；已读不确认。

### CM-03 影响/申请/人员确认

交付：API-26～30/33，目标/影响快照、本人确认、无人员直复审；资格/状态/版本/唯一待办检查。

验收：快照不可修改；报名冻结；本人不向自己确认；拒绝需原因；停用不能代签；人员视图裁剪ID但计数真实。

### CM-04 复审及原子切换

交付：API-31/32；批准范围、管理员复核范围、自审回避；AccountPort当前身份、SchedulingPort最终检查、ActivityPort激活。

验收：CONFLICTED提交原因保留旧安排；异常整体回滚；成功版本/预约/报名/audit/outbox一次提交；取消竞争不复活。

### CM-05 检查/发布/配置库

交付：check.ps1、Dockerfile/Nginx/CI、备份迁移恢复、release说明及manifest、配置计划/handoff。

验收：缺测试与零测试失败；独立环境能启动主线；manifest实际commit/校验值；不删开发卷掩盖问题；基线/评审如实登记。

## 3 必须运行的检查

每项关键权限、状态、容量、事务、并发规则都有能发现错误的测试；文案修改不强制镜像测试。后端通过Maven Wrapper运行本模块*Test和真实PostgreSQL *IT；前端typecheck、test:unit、build；QA运行合同、数据库验收、真实E2E及评测；CM检查环境/种子/部署及改期/通知IT。具体入口由M0按common/04落地，缺入口或0测试不能记通过。

需求/验收关联：FR-16～20/23/24/25；D-AT-10～15/17/19/20/24；N-AT-03～06。实际结果、基准commit、文件/API/端口、未完成项及依赖提供者写入本人handoff；不得把计划当执行记录。真实课程文档与代码同步，课程原始Office不覆盖。

## 4 可直接交给开发助手的指令

```text
我是杨瀚霖（55231031），本项目配置经理。
从最新main同步到work/55231031-change，保留已有本地修改，不reset或覆盖其他成员文件。
读取新版taskbook.md、agents/common五份文件及agents/55231031-change/agent.md。
使用v3.0三角色、独立身份和资源资格契约，按CM-01开始实际编码、测试和课程文档。
只编辑本人范围；共享类型/迁移由规定负责人维护。依赖未到先完成独立任务，列明具体缺口。
每项运行规定检查并记录真实结果，更新本人handoff；不要只复述计划，不用Mock冒充联调成功。
```
