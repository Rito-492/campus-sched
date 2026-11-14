# 待提交UI PR本地自查 · 2026-11-11

## 修正后复核：本轮意见已处理

2026-11-11按用户要求完成P3文档修正：验证报告现在按原始记录明确写出新进程22:28:44启动、持久化检查22:29:16开始并在约12.3秒后通过、健康及进程参数22:29:53补核对。已重新对照两份JSON的时间字段检查；只修改文档，无需重跑业务测试。**未处理的必须改0项，未处理的建议改0项**；可提交PR供独立交叉评审，不代表正式Approve或发布放行。

原始证据与初审manifest保持不变；修正核对及新文档哈希见评审证据目录的`resolution.json`。下面保留初审意见和当时的执行记录，已处理状态以上述复核为准。

## 初审结论：建议改（历史记录）

未发现阻塞本次提交的必须改项；发现1项P3文档时间顺序建议。当前候选可创建PR供独立交叉评审，本报告不代表正式Approve或项目发布放行。

作者：杨畅（17231018）的本地开发助手。本人参与了候选实现，因此这是用户明确要求的**自查**；按仓库`docs/testing/pr-review-checklist.md`和`agents/common/04-协作与集成规程.md`，正式合并仍需其他成员/岗位交叉评审。

对象为`work/17231018-ui`上本次完整未提交候选，不是已合并PR #17。本地HEAD与2026-11-11重新读取的远端main均为`ca162bf975b2ebae0bd0dd59d7c63b23bc505b35`。包含9个已跟踪文件的修改和118个新增文件（其中84张截图、31份原始证据、2份交付文档、1个测试文件），不含本轮评审新增文件。范围快照见`candidate-checks.json`。

## 必须改（阻塞合并）

无。本次未发现产品行为回归、共享契约变更、敏感值泄露或让测试表面通过的处理。

## 初审建议改（现已处理）

- **[P3] 校正重启验证描述的时间顺序**：[验证报告第60行](ui-validation-2026-11-10.md#环境与执行结果)写成确认健康状态后执行4项检查。但`restart-results.json`记录测试开始于2026-11-10 22:29:16（北京时间），运行约12.3秒；`backend-restart.json`的健康/进程核对时间为22:29:53。现有证据支持“实际重启后执行检查，并补核对新进程、关闭播种参数和健康状态”，不支持当前所写的先后顺序。建议按上述措辞调整，仅修正文档，不需重跑业务。依据：评审清单§3真实性、§6结论可溯源。新进程22:28:44已启动，4项真实检查均通过，因此该描述问题不否定已执行的重启验证。

本轮按评审请求只记录问题，没有修改被评审的产品、依赖或测试，也没有顺手改写上述待修正文档。

## 已核对无问题的维度

| 维度 | 核对内容与结论 |
|---|---|
| 范围归属 | 候选仅web、docs/ui和本人artifacts；无后端、QA、共享契约、课程Office改动；运行目录和dist/target仍忽略，manifest只记录构建物哈希 |
| 依赖变更 | 逐项比较锁文件，除根devDependencies外均为开发依赖变化；生产依赖条目无变化；@vue/test-utils局部overrides未变成全局覆盖；Node22符合项目及新版本要求 |
| 兼容性 | 新测试实际经过wrapper.html、js-beautify CLI、glob文件匹配和EditorConfig规则；indent_size=2区别于默认缩进，并核对非匹配文件不被修改；本轮重跑2/2通过 |
| 契约一致 | 新增真实用例使用现有ApprovalCommand、ActivityView、CalendarEntry及既有URL；请求携带当前expectedLockVersion；核对原样空串/纯空白，未向生产增加默认意见或修改必填规则 |
| 测试质量 | 通过真实API创建唯一资源与最小权限账号；断言状态、锁版本、有效版本、唯一审计、唯一占用及重启后的完整安排；无新增mock、跳过或固定sleep；持久化前置运行/实际重启要求已写明 |
| 证据真实性 | 原manifest的272项SHA-256全部一致；原109项单测和33项浏览器检查结果、84张PNG、进程重启记录与汇总对应；明确区分本轮重跑与历史复用 |
| 敏感信息 | 候选文本与本地隔离环境秘密值匹配扫描0命中；未提交密码文件、会话、trace或录像；截图为隔离测试/演示数据 |
| 文档边界 | PR #17已合并、#22空意见修复已验证；CM #21、完整QA、Compose和发布签收仍列待完成；未把backend -DskipTests package描述为全量verify通过 |

## 本轮实际执行与证据复用

证据目录：[ui-local-review-2026-11-11](../../artifacts/test-results/ui-local-review-2026-11-11/)。

| 动作 | 结果 | 证据 |
|---|---|---|
| git ls-remote origin refs/heads/main | ca162bf，与本地HEAD一致 | 本报告基准 |
| 原manifest哈希核对 | 272/272一致 | inspection.json |
| 候选范围、原始截图格式、秘密值扫描 | 127项候选文件；84张有效PNG；无越界，凭据值0命中 | candidate-checks.json、security-scan.json |
| Node22执行vitest run tests/tooling-compatibility.test.ts | 1文件2项通过 | tooling.log、tooling-results.json |
| Node22执行npm audit --audit-level=moderate --json | 0漏洞，退出码0 | audit.json |
| npm ls vitest @vue/test-utils glob minimatch --all --json | 退出码0，无无效依赖树 | dependencies.json |
| git diff --check | 通过；仅提示部分CRLF将按Git规则转换为LF | 实际命令返回 |

本轮未重跑npm ci、全量109项单测、类型检查、构建、33项浏览器用例、后端或Compose。上述成功结果复用2026-11-10原始输出，并先核对其绑定源码/产物/证据哈希；没有把复用结果写成本轮新执行。

扫描过程说明：首轮将本地设置中的所有长字符串一并匹配，命中了既有测试注释中的测试库名，检查因此返回非零。随后核对命中键为非秘密的databaseName，并对databasePassword/demoPassword凭据值单独扫描，0命中。`candidate-checks.json`保留广义匹配，最终分类见`security-scan.json`，未输出或删除真实凭据。

评审只新增本报告、评审证据并在handoff顶部记录结论。因此旧manifest保持原候选快照，不覆盖它；handoff追加后的新哈希由本轮`review-manifest.json`记录。

## 遗留风险与交接

- glob/minimatch为跨主版本局部覆盖，已验证项目实际消费路径，但不等于覆盖上游全部API；将来升级@vue/test-utils时需检查覆盖是否仍必要。
- `audit:dependencies`是手动入口；现有CM维护的`scripts/check.ps1` UI流程仍只跑typecheck/test:unit/build。后续可由CM将审计加入统一检查，本次未宣称自动CI门禁已接入，也不越权修改该脚本。
- CM PR #21的所有者自确认与409完整信息不在本次已通过基线；本地自查不能代替其合入后的真实联调，也不能代替QA/Compose/发布签收。
- 下一接收人：QA/其他独立评审人、CM、PM，尚未接收。未提交、推送、创建PR、发送评审消息或执行合并。
