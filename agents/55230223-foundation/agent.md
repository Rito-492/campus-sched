# 冀项阳（55230223）：项目经理开发任务 v3.0

编制日期：2026年10月13日。从执行时最新的 origin/main 开工（历史初始基准 a28dcee），新版三角色及身份/资格规则见common五份文件。只执行本人的工作包，不读取旧任务包。

## Agent 执行入口

本文件是 冀项阳（55230223）的个人开发指令。收到“按本文件开发”后，应实际修改代码、运行检查并更新交接；按 PM-01～05 推进，不停留在分析或再写计划。

路径相对于仓库根目录。先定位包含 AGENTS.md 和 taskbook.md 的根目录，读取根 AGENTS.md、taskbook.md、agents/common/ 五份文件，再执行本人任务。公共契约规定接口和业务，个人文档规定责任；发现冲突列明并按公共契约执行，不自行改字段或枚举。

可编辑路径：server/pom.xml、server/mvnw*、server/.mvn/；server/src/main/java/com/campus/events/ 下 foundation、auth、contract 及应用入口；contracts/openapi.yaml；docs/management/、根 README 和任务包；同名测试包。

共享 DTO/端口定义变更交 PM，数据库结构变更交 DS；他人模块缺陷提供复现及建议。保留已有未提交修改，课程原件和用户 Word 文档不覆盖。

### 每轮执行流程

1. 检查分支、未提交修改和本人 Git 身份，获取最新 origin/main，在本人 work 分支同步；不存在时从 origin/main 创建，不自动 reset。
2. 检查已有代码、测试和本人 handoff，从下一项未完成任务继续，已有正确实现继续使用。
3. 实际完成一项可验证功能及必要测试；依赖未到先做独立工作，测试替身只放测试，生产不伪造成功。
4. 运行本人检查，记录真实结果；缺依赖列具体文件/端口、提供者、影响任务及仍可执行任务。
5. 更新 docs/management/handoff.md，向用户说明已完成任务、改动、验证、遗留依赖和下一步。不要自动联系或启动其他成员聊天；提交、合并、推送按用户授权执行。

第一批独立工作：检查现有 server 和 contracts，建立可编译 Maven 工程、启动类、共享响应/异常/Clock，再按 common/02 定义 DTO 和五类端口。数据库相关测试待 DS/CM 环境到位，工程与纯逻辑测试先完成。

本人验证入口：后端目录运行 ./mvnw.cmd test；真实库到位后运行 ./mvnw.cmd verify。落实 Surefire/Failsafe 并核对实际执行数量，负责公共工程及 auth/foundation 测试。 入口尚不存在时先由责任人建立；未执行、零测试或缺真实库不能记通过。

交接内容：向全员交工程、DTO/端口、认证与幂等用法，向 UI/QA 交 OpenAPI 和真实请求/失败示例。

handoff 至少包含成员/学号、任务 ID、实际 main 基准和本人分支、文件/API/端口、实际命令及结果、证据、未完成任务、依赖及提供者、下一接收人。未收到接收反馈写“尚未接收”。

## 1 范围与依赖

分支：`work/55230223-foundation`。负责后端构建、foundation/auth/contract、AccountPort、OpenAPI、集成与任务包。

依赖：DS的V1迁移、CM数据库/种子及配置、QA测试数据。工程和DTO可先实现，不等业务模块。

## 2 按顺序执行的任务

### PM-01 后端公共工程

交付：server/pom.xml、Maven Wrapper、CampusEventsApplication、Clock；配置Surefire/Failsafe，公共Responses/异常/requestId，40项OpenAPI及全部record/端口。

验收：Java21工程可编译；单元与数据库IT入口区分；Contract字段与common/02一致；不得跳过缺失测试。

### PM-02 账号、身份和权限

交付：auth登录/退出/me、账号创建/启停、API-39完整授权替换、组织查询；密码哈希、8小时会话、AccountPort get/lock；三角色、身份、组织类别范围与资源细分权限。

验收：账号停用旧会话失效；身份不产生审批权；所有者回避和范围完整检查有测试；用户授权前后值共事务记录。

### PM-03 命令一致性

交付：foundation/CommandExecutor、receipt、规范化请求hash、事务；锁序和完整首次响应保存。

验收：同key同body重放首次响应/requestId；同key异body409；并发同key仅执行业务一次；异常回滚receipt及业务。

### PM-04 合同协调与主线集成

交付：按40API登记实现者/调用者；接收Activity/Scheduling/Change/Event端口；同步新版技术标识；整合M0/M2/M3。

验收：生产无替身、无循环依赖、无双重响应包装；真实创建至取消及改期主线接通。

### PM-05 课程计划与交接

交付：更新初始/详细计划、工作量实际记录、集成日志和docs/management/handoff.md。

验收：阶段完成有实际commit/命令；未通过场景列清；不代签课程表或虚构工时。

## 3 必须运行的检查

每项关键权限、状态、容量、事务、并发规则都有能发现错误的测试；文案修改不强制镜像测试。后端通过Maven Wrapper运行本模块*Test和真实PostgreSQL *IT；前端typecheck、test:unit、build；QA运行合同、数据库验收、真实E2E及评测；CM检查环境/种子/部署及改期/通知IT。具体入口由M0按common/04落地，缺入口或0测试不能记通过。

需求/验收关联：FR-01/02/03/25；N-AT-01/05/06。实际结果、基准commit、文件/API/端口、未完成项及依赖提供者写入本人handoff；不得把计划当执行记录。真实课程文档与代码同步，课程原始Office不覆盖。

## 4 可直接交给开发助手的指令

```text
我是冀项阳（55230223），本项目项目经理。
从最新main同步到work/55230223-foundation，保留已有本地修改，不reset或覆盖其他成员文件。
读取新版taskbook.md、agents/common五份文件及agents/55230223-foundation/agent.md。
使用v3.0三角色、独立身份和资源资格契约，按PM-01开始实际编码、测试和课程文档。
只编辑本人范围；共享类型/迁移由规定负责人维护。依赖未到先完成独立任务，列明具体缺口。
每项运行规定检查并记录真实结果，更新本人handoff；不要只复述计划，不用Mock冒充联调成功。
```
