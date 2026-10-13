# 冯玉琪（22220314）：系统分析员开发任务 v3.0

编制日期：2026年10月13日。从执行时最新的 origin/main 开工（历史初始基准 a28dcee），新版三角色及身份/资格规则见common五份文件。只执行本人的工作包，不读取旧任务包。

## Agent 执行入口

本文件是 冯玉琪（22220314）的个人开发指令。收到“按本文件开发”后，应实际修改代码、运行检查并更新交接；按 AN-01～05 推进，不停留在分析或再写计划。

路径相对于仓库根目录。先定位包含 AGENTS.md 和 taskbook.md 的根目录，读取根 AGENTS.md、taskbook.md、agents/common/ 五份文件，再执行本人任务。公共契约规定接口和业务，个人文档规定责任；发现冲突列明并按公共契约执行，不自行改字段或枚举。

可编辑路径：server/src/main/java/com/campus/events/ 下 activity、registration 及同名测试包；ActivityPortAdapter 放 activity；docs/requirements/。

共享 DTO/端口定义变更交 PM，数据库结构变更交 DS；他人模块缺陷提供复现及建议。保留已有未提交修改，课程原件和用户 Word 文档不覆盖。

### 每轮执行流程

1. 检查分支、未提交修改和本人 Git 身份，获取最新 origin/main，在本人 work 分支同步；不存在时从 origin/main 创建，不自动 reset。
2. 检查已有代码、测试和本人 handoff，从下一项未完成任务继续，已有正确实现继续使用。
3. 实际完成一项可验证功能及必要测试；依赖未到先做独立工作，测试替身只放测试，生产不伪造成功。
4. 运行本人检查，记录真实结果；缺依赖列具体文件/端口、提供者、影响任务及仍可执行任务。
5. 更新 docs/requirements/handoff.md，向用户说明已完成任务、改动、验证、遗留依赖和下一步。不要自动联系或启动其他成员聊天；提交、合并、推送按用户授权执行。

第一批独立工作：检查 main 和本人分支已有活动代码，对照新版状态机逐项复用。先实现纯状态机、合法/非法转换测试及需求技术标识同步，再接持久化和审批。

本人验证入口：后端目录运行 ./mvnw.cmd test 检查活动/报名单元测试；真实库和依赖到位后运行 ./mvnw.cmd verify 验证活动/报名 IT，报告本人测试类和数量。 入口尚不存在时先由责任人建立；未执行、零测试或缺真实库不能记通过。

交接内容：向 CM 交 ActivityPortAdapter，向 UI/QA 交 API-12～23 真实示例、状态转换和失败码，向 PM 交模块集成结果。

handoff 至少包含成员/学号、任务 ID、实际 main 基准和本人分支、文件/API/端口、实际命令及结果、证据、未完成任务、依赖及提供者、下一接收人。未收到接收反馈写“尚未接收”。

## 1 范围与依赖

分支：`work/22220314-activity`。负责activity/registration、ActivityPortAdapter及模块测试、需求文档。

依赖：PM共享类型/认证/CommandExecutor，DS schema和SchedulingPort，CM ChangePort/EventPort。先写新版纯状态机；已有分支代码逐项审查后复用。

## 2 按顺序执行的任务

### AN-01 需求落实与状态机

交付：同步规约/用例/规则/追踪中的PENDING_APPROVER与审批URL技术标识；活动纯状态机及单元测试。

验收：创建/编辑/提交/撤回/批准/预约/发布/取消有合法及非法转换测试；内容版本、并发版本分开。

### AN-02 活动持久化及端口

交付：ActivityRepository/Service/Controller，API-12～17；不可变版本、本人列表/详情；ActivityPortAdapter get/lock/activatePlan/bumpLockVersion。

验收：草稿完整保存；旧lockVersion拒绝；审批绑定内容；get隐私裁剪，内部快照完整，适配器无循环依赖。

### AN-03 审批和首次预约

交付：API-18～20；批准人范围和自审回避；资源复核管理员权限/范围/回避；提交与确认重查所有者资格；统一SchedulingPort预约。

验收：同意不占资源；确认全部成功或失败无部分占用；受限资源提交拒绝；未开始无风险才发布。

### AN-04 报名及取消

交付：API-21～23；本人学生身份报名/退出、真实容量和版本；冻结；取消调用ChangePort关闭、SchedulingPort释放、EventPort记录。

验收：20账号抢1名额仅1成功；重复不增人数；取消/改期竞争不复活；所有者重复取消返回当前结果。

### AN-05 文档及测试交接

交付：模块单元/IT，需求→API/代码/验收映射、docs/requirements/handoff.md。

验收：数据库刷新/重启一致；名单隐私、本人申请回避、资格变化有实际证据；接口交UI/QA/DS/CM。

## 3 必须运行的检查

每项关键权限、状态、容量、事务、并发规则都有能发现错误的测试；文案修改不强制镜像测试。后端通过Maven Wrapper运行本模块*Test和真实PostgreSQL *IT；前端typecheck、test:unit、build；QA运行合同、数据库验收、真实E2E及评测；CM检查环境/种子/部署及改期/通知IT。具体入口由M0按common/04落地，缺入口或0测试不能记通过。

需求/验收关联：FR-07～13/21/24；D-AT-03/15/16/17/20；N-AT-02/05/06。实际结果、基准commit、文件/API/端口、未完成项及依赖提供者写入本人handoff；不得把计划当执行记录。真实课程文档与代码同步，课程原始Office不覆盖。

## 4 可直接交给开发助手的指令

```text
我是冯玉琪（22220314），本项目系统分析员。
从最新main同步到work/22220314-activity，保留已有本地修改，不reset或覆盖其他成员文件。
读取新版taskbook.md、agents/common五份文件及agents/22220314-activity/agent.md。
使用v3.0三角色、独立身份和资源资格契约，按AN-01开始实际编码、测试和课程文档。
只编辑本人范围；共享类型/迁移由规定负责人维护。依赖未到先完成独立任务，列明具体缺口。
每项运行规定检查并记录真实结果，更新本人handoff；不要只复述计划，不用Mock冒充联调成功。
```
