# 空批准意见修复验证

冯玉琪22220314，2026-11-08，基线16de4a5。标准server/pom.xml，Java21.0.12.1、PostgreSQL17.6，本机campus_test随机schema。凭据来自环境变量。

- an-approval-before.log及before-ActivityApprovalIT.xml由修复前实际运行产生，5 IT中2失败。空字符串/纯空白APPROVE期望200、实际400，事件字段不完整。
- an-approval-after.log是首次修复回归，25单测+42 IT通过，但AN测试清理schema时后台调度仍可能运行，日志保留该清理错误。
- an-approval-final.log是修正AN测试清理顺序后的最终回归，77单测+42 IT通过，0失败/错误/跳过。

最终命令为 `./server/mvnw.cmd -B -ntp -f server/pom.xml "-Dmaven.repo.local=$anRepo" '-Dit.test=ActivityApprovalIT,ActivityRegistrationIT,ProductionWorkflowIT' verify`。首次复现命令另设`-Dtest=ActivityStateMachineTest,ActivityServiceTest -Dit.test=ActivityApprovalIT`。本地Maven缓存仅影响依赖下载位置。

IT分布为ActivityApprovalIT 5项（真实生产端口），ActivityRegistrationIT 35项（包含可注入失败的CM测试替身及10次竞争执行），ProductionWorkflowIT 2项（五类真实生产端口）。全仓库其他IT未在最终命令中执行。JUnit XML导出时仅移除运行环境properties，断言结果与输出保留，源码SHA256见source-manifest.json。
