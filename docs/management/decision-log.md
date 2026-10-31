# 项目经理决策记录

日期：2026-10-19；责任人：冀项阳（55230223）；依据：任务包v3.0。以下为本轮实际执行选择，不代表团队评审签字或已批准发布基线。

| 编号 | 决策与依据 | 影响及状态 |
|---|---|---|
| PM-D01 | 沿用Java21、Spring Boot3.5.16，Maven Wrapper固定3.9.11/Wrapper3.3.4并校验发行包SHA-256 | 按01基线和PM-01落实；依赖已实际解析。未更改公共版本契约 |
| PM-D02 | 只修改PM所有权路径；保留未提交Word及课程原件 | CM编译问题和QA/DS缺口形成可复现交接，不越权重写他人代码 |
| PM-D03 | 默认test/verify不排除测试；提供显式pm-isolated配置仅检查PM源码和PM测试 | 对应“依赖未到先独立工作”。隔离结果不得关闭M0/M2/M3或作为发布包；默认构建失败如实保留 |
| PM-D04 | 三角色、独立身份、显式范围；移除角色时要求对应权限/范围一并清空 | 沿用02契约。无通配符、无五角色兼容；最新授权从数据库读取，重要写入先锁用户 |
| PM-D05 | 复用DS的V1，不修改迁移；用真实PostgreSQL17独立campus_test与随机schema验证PM | 只清理本测试schema，未使用开发数据或H2替代；未将PM fixture等同QA演示数据 |
| PM-D06 | 初次本地交付时未创建提交、推送、PR、评审或发布标签 | 记录首次交付状态；后续用户提交授权见PM-D07，业务接收仍须真实反馈 |
| PM-D07 | 用户明确要求提交给别人评审，提交并推送本人work分支，创建面向main的PR并请求Kimcra1交叉评审 | 保留真实失败日志；不混入用户Word、不自审、不合并main，实际状态以GitHub反馈为准 |

技术核查来源：[Spring Boot 3.5.16发布说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)、[Apache Maven Wrapper说明](https://maven.apache.org/tools/wrapper/index.html)、[Zonky PostgreSQL二进制项目](https://github.com/zonkyio/embedded-postgres-binaries)。后者只用于本次临时测试环境，未添加到生产依赖。

## 2026-10-31 补充决定

| 编号 | 本轮落实 | 影响及限制 |
|---|---|---|
| PM-D08 | 新建密码落实已存在的NFR-08，至少8字符且UTF-8不超过72字节 | API-05及UI/QA输入；不改字段/表/端口，不改变既有登录门禁；HTTP及直接Service均检查 |
| PM-D09 | 已实现接口缺必需依赖采用现行503 INTERNAL_ERROR及统一信封 | 同步common/02与OpenAPI；错误整体回滚，未实现控制器不冒充可用；AN/DS既有实现不改名 |
| PM-D10 | 标准Failsafe独立test环境、运行期随机schema绑定，缺配置失败 | 外置properties保留Spring占位符；动态模块配置优先，不回落开发库/public；真实schema回归验证 |
| PM-D11 | 通过的模块集合与失败的默认全量分别记录 | 不排除默认验收，不以mock或指定集合宣称M0/M2/M3/M4通过；保留本轮全部实际错误 |
| PM-D12 | 保留用户本地详细计划及Word，不自动发布到课程平台 | 仓库计划只作执行增补；实际工时/容量、评审与接收未提供时不编造 |

AN建议B/D/I及名单裁剪的后续范围见[契约协调记录](contract-coordination-2026-10-31.md)，没有将未确认建议误记为已实施。
