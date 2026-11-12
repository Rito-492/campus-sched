# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: change-conflict.spec.ts >> D-AT-18 资源故障与风险提示 >> TC-18-1 故障使 A 原安排标 AT_RISK，页面展示风险提示且不谎报可用
- Location: tests\change-conflict.spec.ts:311:3

# Error details

```
Error: 页面应展示 AT_RISK/风险提示

expect(locator).toBeVisible() failed

Locator: getByText(/AT_RISK|资源风险|受影响|不可用/).first()
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - 页面应展示 AT_RISK/风险提示 getByText(/AT_RISK|资源风险|受影响|不可用/).first() with timeout 10000ms
  - waiting for getByText(/AT_RISK|资源风险|受影响|不可用/).first()

```

```yaml
- link "跳到主要内容":
  - /url: "#main"
- complementary:
  - link "校 校园活动 协同与资源调度":
    - /url: /activities
  - navigation "主导航":
    - paragraph: 协同工作
    - link "活动工作台":
      - /url: /activities
    - link "资源日历":
      - /url: /resources/calendar
    - link "通知中心":
      - /url: /notifications
  - paragraph: 让每一次校园活动 安排有序，协作有据。
- banner:
  - text: 校园活动协同平台 申请人A（演示） 教师
  - button "退出"
- main:
  - link "← 活动工作台":
    - /url: /activities
  - heading "校园技术分享会" [level=1]
  - paragraph: 当前安排、审批与报名状态以服务端最新记录为准。
  - paragraph: ⚠ 当前资源受故障影响。原安排仍保留，请联系负责人调整资源。
  - heading "当前安排" [level=2]
  - text: 开放报名
  - paragraph: 面向软件学院师生的技术分享与交流，含主题演讲与自由讨论环节。（演示数据）
  - term: 活动时间
  - definition: 10/20 10:00 — 10/20 11:00
  - term: 布场 / 撤场
  - definition: 30 / 30 分钟
  - term: 场地
  - definition: 学术报告厅A
  - term: 设备
  - definition:
    - paragraph: 投影设备组A × 2
  - term: 有效版本
  - definition: "1"
  - button "刷新状态"
  - heading "报名参与" [level=2]
  - paragraph: 1 / 20 人
  - paragraph: 你尚未报名
  - paragraph: 仅学生身份的申请人可报名或退出
  - button "报名参加" [disabled]
  - heading "办理活动" [level=2]
  - paragraph: 草稿 → 待批准 → 待资源确认 → 已预约 → 开放报名。业务批准不会占用资源。
  - link "发起改期":
    - /url: /activities/00000000-0000-0000-0000-000000004001/change
  - text: 原因或审批意见（拒绝、撤回、取消必填）
  - textbox "原因或审批意见（拒绝、撤回、取消必填）"
  - button "取消活动" [disabled]
  - heading "人员与记录" [level=2]
  - tablist:
    - tab "工作人员" [selected]
    - tab "报名名单"
    - tab "改期记录"
    - tab "操作历史"
  - tabpanel "工作人员"
  - paragraph: 工作人员A（演示）
```

# Test source

```ts
  251 |         apis.push(organizerApi);
  252 |         const before = await getActivity(organizerApi, ACTIVITIES.a.id);
  253 | 
  254 |         // 抢占前预览可行（无冲突）
  255 |         const preview = await changePreview(organizerApi, ACTIVITIES.a.id, {
  256 |           baseVersionNo: before.versionNo,
  257 |           targetPlan: PREFERRED_PLAN,
  258 |         });
  259 |         expect(preview.check.feasible, '初始预览应可行').toBe(true);
  260 |         expect(preview.check.conflicts, '初始预览应无冲突').toEqual([]);
  261 | 
  262 |         // 另一活动先占用候选资源 3102 + 3202×2
  263 |         await preemptPreferredResources();
  264 | 
  265 |         const changeId = await createFeasibleChange(organizerApi);
  266 |         const staffApi = await apiLogin(USERS.staffA);
  267 |         apis.push(staffApi);
  268 |         await confirmChange(staffApi, changeId, 'APPROVE');
  269 |         const approverApi = await apiLogin(USERS.approverA);
  270 |         apis.push(approverApi);
  271 |         await changeApproverDecision(approverApi, changeId, 'APPROVE');
  272 | 
  273 |         // resource_admin 最终确认：重新检查发现冲突 → 409，事务正常提交 CONFLICTED 记录
  274 |         const resource = await openSession(browser, USERS.resourceAdmin);
  275 |         sessions.push(resource);
  276 |         await resource.page.goto(`/changes/${changeId}`);
  277 |         const decisionResponse = resource.page.waitForResponse((res) =>
  278 |           res.url().includes(`/changes/${changeId}/resource-decision`),
  279 |         );
  280 |         await resource.page.getByTestId(TESTID.changeResourceApprove).click();
  281 |         const err = await readError(
  282 |           await decisionResponse,
  283 |           409,
  284 |           '最终资源确认应 409 RESOURCE_CONFLICT',
  285 |         );
  286 |         expect(err.error.code, '错误码应为 RESOURCE_CONFLICT').toBe('RESOURCE_CONFLICT');
  287 |         const details = err.error.details as { change?: { status?: string } } | undefined;
  288 |         expect(details?.change?.status, '409 details.change 应为 CONFLICTED（02 §3）').toBe('CONFLICTED');
  289 | 
  290 |         await expectChangeStatus(resource.page, /CONFLICTED|冲突/);
  291 |         // UI 收到 409 显示原因（02 §1：保留未提交表单/展示原因）
  292 |         await expect(resource.page.getByRole('alert').filter({ hasText: /冲突|占用|不可行|失败/ })).toBeVisible();
  293 | 
  294 |         // 原有效安排保留：无新版本、旧预约仍 ACTIVE
  295 |         const after = await getActivity(organizerApi, ACTIVITIES.a.id);
  296 |         expect(after.versionNo, '抢占失败不得产生新版本').toBe(1);
  297 |         expect(after.effectiveVersionNo, '有效版本保持 1').toBe(1);
  298 | 
  299 |         const organizer = await openSession(browser, USERS.applicantA);
  300 |         sessions.push(organizer);
  301 |         await expectOriginalPlanPreserved(organizer.page);
  302 |       } finally {
  303 |         await closeSessions(sessions);
  304 |         await apiClose(apis);
  305 |       }
  306 |     },
  307 |   );
  308 | });
  309 | 
  310 | test.describe('D-AT-18 资源故障与风险提示', () => {
  311 |   test(
  312 |     'TC-18-1 故障使 A 原安排标 AT_RISK，页面展示风险提示且不谎报可用',
  313 |     { annotation: [{ type: 'D-AT', description: 'D-AT-18' }, { type: 'TC', description: 'TC-18-1' }] },
  314 |     async ({ browser }) => {
  315 |       const sessions: UiSession[] = [];
  316 |       const apis: ApiSession[] = [];
  317 |       try {
  318 |         const organizerApi = await apiLogin(USERS.applicantA);
  319 |         apis.push(organizerApi);
  320 |         const resourceApi = await apiLogin(USERS.resourceAdmin);
  321 |         apis.push(resourceApi);
  322 | 
  323 |         // 对 A 当前实际占用的场地创建重叠故障（venue quantity=1）
  324 |         const activity = await getActivity(organizerApi, ACTIVITIES.a.id);
  325 |         const plan = activity.input.plan;
  326 |         const fault = await createResourceFault(resourceApi, plan.venueId, {
  327 |           startAt: plan.startAt,
  328 |           endAt: plan.endAt,
  329 |           unavailableQuantity: 1,
  330 |           reason: '设备检修（测试数据）',
  331 |         });
  332 |         expect(
  333 |           fault.affectedActivities.map((a) => a.activityId),
  334 |           'FaultView.affectedActivities 应含活动 A（02 §2）',
  335 |         ).toContain(ACTIVITIES.a.id);
  336 | 
  337 |         const faults = await getResourceFaults(resourceApi, plan.venueId, at('00:00'), at('23:59'));
  338 |         expect(faults.length, 'resource_fault 应落库').toBeGreaterThanOrEqual(1);
  339 | 
  340 |         // 原 reservation 仍在但查询时计算为 AT_RISK
  341 |         const refreshed = await getActivity(organizerApi, ACTIVITIES.a.id);
  342 |         expect(refreshed.resourceRisk, 'ActivityView.resourceRisk 应为 AT_RISK').toBe(RISK.atRisk);
  343 | 
  344 |         // 页面必须展示风险提示，不能把原安排显示为可正常使用
  345 |         const organizer = await openSession(browser, USERS.applicantA);
  346 |         sessions.push(organizer);
  347 |         await organizer.page.goto(`/activities/${ACTIVITIES.a.id}`);
  348 |         await expect(
  349 |           organizer.page.getByText(/AT_RISK|资源风险|受影响|不可用/).first(),
  350 |           '页面应展示 AT_RISK/风险提示',
> 351 |         ).toBeVisible();
      |           ^ Error: 页面应展示 AT_RISK/风险提示
  352 |         await expect(
  353 |           organizer.page.getByText(/可正常使用|安排正常/),
  354 |           '页面不得把原安排显示为可正常使用',
  355 |         ).toHaveCount(0);
  356 |       } finally {
  357 |         await closeSessions(sessions);
  358 |         await apiClose(apis);
  359 |       }
  360 |     },
  361 |   );
  362 | });
  363 | 
```