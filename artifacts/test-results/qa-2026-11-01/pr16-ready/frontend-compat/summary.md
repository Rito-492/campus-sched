# QA Playwright 与新合入 UI 真实交互兼容性修正 · 证据 2026-11-01

子代理 QA（frontend-compat）。只改 `qa/tests/**`（5 个文件）；`qa/playwright.config.ts`（trace=off）与
`qa/tests/login.spec.ts` 由父代理修改，未重复改动。未改 web/server 生产代码，未提交未推送。

## 一、检查命令与真实结果（完整 stdout 见同目录各 log）

| 命令 | exit | 结果 | 证据文件 |
|---|---|---|---|
| `npm --prefix web ci`（`npm_config_cache=/tmp/npm-cache-r1t0`） | 0 | 203 包安装成功 | `web-npm-ci.log` |
| `npm --prefix web run typecheck`（vue-tsc --noEmit） | 0 | 通过 | `web-typecheck.log` |
| `npm --prefix web run test:unit`（vitest run） | 0 | 21 文件 / 107 项全部通过，0 失败 0 跳过 | `web-unit.log` |
| `npm --prefix web run build` | 0 | vue-tsc + vite build + check-production + bundle 检查全过（3.90s，最大块 201.47kB） | `web-build.log` |
| `qa: npx tsc --noEmit` | 0 | 通过 | `qa-typecheck-and-list.log` |
| `qa: npx playwright test --list` | 0 | 29 项 / 7 文件全部注册 | `qa-typecheck-and-list.log` |

注：首次 `npm ci` 因 `/home/r1t0/.npm` 存在 root 属主缓存文件触发 EACCES 中断；改用临时缓存目录后成功。
本机 Node 为 v26.12.1，`web/package.json` engines 声明 `>=22.18 <23`（联调文档写 22.23.0）——运行偏差，如实记录。
npm warn：esbuild postinstall 脚本被 allowScripts 策略拦截，但构建实际通过（平台二进制随 optional 包分发）。

## 二、脚本改动（qa-only-diff.patch 为逐行证据）

1. **helpers.ts 向导**：`fillNewActivityForm` 改为 4 步向导逐步点「下一步」；标题用 `activity-title`，
   容量标签改「报名容量」（旧 regex 匹配不到真实标签）；开始/结束用 `activity-start`/`activity-end`。
2. **Element Plus 选择**：场地/设备改「点击选择框 + getByRole('option') 点选」（旧 `selectOption` 对
   el-select 无效）；设备行先点「添加设备」再逐行选择（旧代码只对同一控件重复 selectOption）。
   option 文案带「· 容量N人 / · 库存N」后缀，按资源名子串匹配。
3. **保存后提交**：`createActivityViaUi` 改点 `activity-save` 保存草稿并在详情路由取 ID；
   草稿保存不会自动提交审批，可选 `submit:true` 时在详情页点 `activity-submit`（05 §4）并断言 200。
4. **改期理由**：happy-path 选 `alternative-0` 后先等第二次 `/change-preview`（最终预览，断言 200），
   再断言未填原因时 `change-create` disabled → 填 `change-reason`（契约必填）→ 断言 enabled → 创建。
5. **拒绝改期**：`change-reject` → 弹窗标题「拒绝改期」→ `reject-reason` → `reject-confirm`；
   新增「未填理由时 reject-confirm 保持禁用」断言（不弱化，反增强）。
6. **学生名单权限**：booking.spec 改 `ownerApi` 读 API-23 完整名单，学生侧只断言
   `myRegistrationStatus`（旧 studentApi 读名单违反权限；403 边界已由 permissions.spec TC-21-2 覆盖）。
7. **状态文案兼容**（与真实 UI statusLabels/changeLabels 对齐，未弱化）：
   - 改期：`/待确认/`→`/等待工作人员确认/`、`/待批准人审批/`→`/等待批准人复审/`（旧文案在真实页面不存在）；
   - 活动：提交后断言 badge exact「待批准」、发布后 exact「开放报名」——旧 regex `/待审批//报名中/`
     匹配不到真实 badge，且「草稿 → 待批准 → …」常驻段落会造成假阳性，故改 exact 断言（更强）。
8. **入口/按钮 testid 化**：`change-entry`（发起改期）、`activity-publish`、`registration-submit`。

## 三、风险与未决（不改断言绕过，待父真实 E2E 判定）

1. **首选替代方案排序**：TC-09-2/happy-path 期望 `alternative-0` = 3102+3202×2 同时间（03 §1 demo 期望）。
   扩展资格资源（3111-3114/3211-3214）若进入候选改变排序，按指示不改 demo/expected；
   若失败应记录为产品/契约偏差而非改测试。
2. **N-AT-02 学生 UI 线**：el-option 对不可选资格资源渲染为 disabled（标签「· 当前身份不可申请」）。
   学生可能无法点选受限资源进入表单，导致 save 校验错误区拿不到具体资源名（错误文案退化为
   「所选资源不允许当前身份申请」）。脚本已按文档指定交互（点击+option）落好、断言未改；
   若真实失败 = UI 只在选项层阻止、未在提交错误区说明具体资源，属产品交互偏差。
3. **EventPort 400（APPROVE 空 comment）**：happy-path staff_a `staff-approve` 意见框为空会触发
   父已发现的后端缺陷；按指示保留原测试不填备注绕过，失败即缺陷证据。
4. **测试隔离**：多个 spec 共用 demo 活动 A（版本推进/抢占/故障互相污染），需父按计划每状态改动
   用例独立后端+数据库跑（选 grep）；本次未解决也未掩盖。
5. **“新断言先证明能真实失败”**：本轮仅有静态核对（spec 文案与 web 源码 label/label map 逐一对齐，
   见 qa-only-diff.patch 注释），运行级失败证明待父隔离环境 E2E（本代理未获准改数据运行）。

## 四、job 输出收集

bash-efe165fe8c63449a（npm ci）、bash-f1ac850b7c0b4327（typecheck）、bash-0b671847033d49a1（unit）、
bash-5707df39495548e8（build）四个后台 job 的 job_output 均已读取完毕；退出码与 stdout 已归档至本目录。
