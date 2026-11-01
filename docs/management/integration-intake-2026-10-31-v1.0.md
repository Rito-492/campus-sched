# 候选交付独立接收记录 v1.0

日期：2026-10-31；接收与复验：冀项阳55230223，PM-04/05。本文更新同日较早的feb0d70核查；以实际源码组合和本轮命令为准，不将候选PR视为已合并。

## 1 基准与来源

| 内容 | 实际版本 | 状态 |
|---|---|---|
| 最新main | c4f3d413fa1214d127bfff1b3cca0e1ed4b68ddd；含DS PR #15 | 已普通merge进入PM分支，保留用户工作区 |
| PM原交付 | PR #14，原提交1fc3dd9；同步后父提交e1e4f8a | 仍待交叉评审；本轮新增两项真实跨模块IT |
| QA候选 | PR #16；e1e175c1922b030006c060d08ced6ff59d6c95ea | 原作者的装载/验收修复，Draft，未合入main |
| UI候选 | PR #17；ychangz1/campus-sched的a327b16b171ab40da1ef336b8f1c0ab8d21bd834 | 原作者fork中的完整前端，Draft，未合入main；不能误读原仓库同名旧分支 |
| AN候选 | PR #13；86b15c085eb976651494376ee84fa5e2bbbbb9db | 原作者测试补强；未合入main |
| 独立组合工作区 | 三项候选依次普通merge于PM基准；组合提交03b02dacaf514fc63852d41f6e5dd4b98d1ebcc0，另叠本轮PM测试文件 | 仅用于复验，不推送、不合并main或作者分支 |

没有改写QA/UI/AN/DS源码或测试预期；唯一叠加的是PM拥有的PublishedModulesIT。251份候选源文件/配置/测试/契约的实际SHA-256与提交来源已记录，不能只靠PR描述确认交付。

## 2 本轮真实结果

环境：Java21.0.12.1、Maven3.9.11、PostgreSQL17.6；Node22.23.0（与UI固定版本一致）；真实浏览器使用本机Edge无界面模式。数据库仅为本机campus_test及独立随机schema。

| 检查范围 | 本轮实际结果 | 接收结论 |
|---|---|---|
| 最新main + PM本轮，标准POM指定模块 | 76单元 + 32 IT，全通过；0跳过，exit 0 | 本轮PM模块及依赖回归通过 |
| 同一源码的默认clean verify | 76单元通过；113 IT = 104通过、8初始化错误、1跳过，exit 1 | 主线QA装载仍失败；跳过项是缺真实ChangePort的切换成功测试，不能记通过 |
| PM/AN/DS/QA/UI候选组合默认clean verify | 76单元通过；142 IT = 115通过、24失败、2错误、1跳过，exit 1 | QA外键装载已解除，完整业务验收仍失败；失败集中CM端口503/接口404及级联 |
| 组合中的原QA数据与种子测试 | DbTestSupportIT、SeederRollbackIT、DemoDataSeederIT通过 | QA数据修复和种子回滚在Java21上限定范围接收；原QA报告的Java21待复验已获本轮独立证据 |
| 原QA SchedulingBenchmarkIT + 原evaluate脚本 | 160条测量、16组expected一致，evaluate exit 0 | D-AT-22此源码组合专项复验通过；不是main默认入口通过或完整性能/发布验收 |
| UI npm ci、types:check、typecheck、test:unit、build | 安装成功；18文件78项unit全部通过，其余退出0 | UI工程已实际交付候选，不再写“无人实现”；尚待PR评审 |
| UI原真实局部联调 | 5项浏览器测试通过，未使用route.fulfill或生产端口替身 | 认证、账号、资源、草稿及预览限定范围接收 |
| 关闭播种后重启同一后端/schema | 原持久化浏览器测试1项通过 | 草稿、资源资格、授权和停用状态在该局部路径保留；不等同完整D-AT-23/24 |
| QA契约、TypeScript检查 | 40API/9枚举/15DTO及类型检查通过 | 静态支撑检查通过；未运行QA全套浏览器E2E |
| UI npm audit | exit 1；6 moderate、2 critical，共8项 | 风险保留，不能将构建通过等同依赖审计通过；critical项为开发测试链的vitest/tinypool |

新增PublishedModulesIT验证：（1）API-39身份替换后，不重新登录的同一会话立即按新身份执行真实AN/DS资源预览，停用后旧会话401；（2）DS创建资源经AccountPort自动授权，API-39撤销资源范围后既有会话调用API-40返回403，资格、历史及失败回执均不写入。这两项在PM和候选组合中均通过；全部6项PublishedModulesIT没有CM替身。

首次新增测试存在导入冲突和夹具用户名假设，修正测试后才记录最终通过；UI首次启动误用不含dev种子的profile，纠正为dev,e2e且显式指向测试schema后重跑通过。初次诊断留在本地，不伪装成产品缺陷或最终通过。最终证据未覆盖上一轮日志。

## 3 复验入口与证据

```powershell
# PM分支，配置TEST_DATABASE_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD/DEMO_PASSWORD后
./server/mvnw.cmd -f server/pom.xml '-Dit.test=FoundationIT,PublishedModulesIT,StandardMavenSchemaIT,DemoDataSeederIT' clean verify
./server/mvnw.cmd -f server/pom.xml clean verify

# 候选组合工作区，默认入口不排除任何原验收
./server/mvnw.cmd -f server/pom.xml clean verify
cd qa
npm ci
npm run test:contract
npm run typecheck
npm run evaluate
# web中：npm ci，再运行types:check、typecheck、test:unit、build
# 隔离真实服务可用时：npm run test:integration；关闭播种重启后npm run test:persistence
```

独立组合的源码快照与实际结果见[本轮证据](../../artifacts/test-results/pm-2026-10-31-intake/check-summary.json)、[来源与251份SHA-256](../../artifacts/test-results/pm-2026-10-31-intake/source-manifest.json)。各组日志、无敏感环境属性的JUnit计数摘要、原始160行JSON/CSV、原evaluate摘要和依赖审计同目录。首轮PM证据仍在pm-2026-10-31目录，不改写历史数字。

UI启动的后端、前端进程及本轮UI随机schema已清理；本轮启动的PostgreSQL已停止。未使用或清理开发库、既有卷或他人测试schema。

## 4 接收后剩余工作

| 优先事项 | 责任人 | 交付/退出条件 |
|---|---|---|
| QA候选进入评审 | 汤雨润；PM/非作者复核 | 修复已独立复验，保持真实24失败/2错误及1跳过；不靠跳过/改expected求全绿 |
| CM真实成功链 | 杨瀚霖 | 交EventPort、独立ChangePortAdapter及API26–36；审计/outbox、改期原子切换、通知重试与异常回滚真实验证 |
| UI候选评审与整改 | 杨畅 | 处理依赖审计、补充平面字符密码计数；保持构建/78项unit/真实局部联调，不自动升级破坏性版本 |
| 活动访问与资源边界 | AN冯玉琪、DS黄方勇、QA；PM组织 | 见[具体协调方案](access-coordination-v1.0.md)，状态矩阵和最小签名须共同接收 |
| M2/M3接通 | PM组织AN/DS/CM/UI/QA | CM生产依赖到位后重跑创建至取消、改期成功/冲突/异常，全程真实接口/数据库 |
| M4与课程移交 | QA验证、CM发布、UI手册、PM核对 | 30场景、QA E2E、三视口、干净部署/重启恢复、实际发布commit对应文档；真实日期/投入/课程回执仍待提供 |

这是PM本人给出的限定范围独立接收结论，不是作者自审批准、他人接收签字、PR合并许可或发布放行。QA/UI候选尚未进入main，M0～M4均不据此自动通过。
