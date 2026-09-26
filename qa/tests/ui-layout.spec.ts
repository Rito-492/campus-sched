/**
 * 附表 视觉核对（QA-03）——D-AT-23 页面恢复的界面证据，独立于 D-AT 编号。
 *
 * 依据：`docs/testing/test-design.md` 附表「界面与契约核对」；`agents/common/05-界面规范.md`
 *       §1（视觉约定/1366 不横向溢出、390 可查看）、§5（1366×768、1920×1080、390×844 截图核对）。
 *
 * 断言（自动部分）：
 * - 无横向溢出：document.scrollingElement.scrollWidth ≤ clientWidth（日历内部横向滚动不应撑破页面）；
 * - 无错误横幅：核心页面不出现可见的错误/失败 alert；
 * - 文字可读：正文计算字号 ≥ 12px；
 * - 截图保存到 `artifacts/screenshots/`（文件名带 project 名区分视口）。
 * 人工审查层次/文本/对比，不由本 spec 声称「美观通过」（05 §5）。
 */
import { expect, test, type Page, type TestInfo } from '@playwright/test';
import {
  ACTIVITIES,
  TESTID,
  USERS,
  activityPath,
  changeWorkbenchPath,
  closeSessions,
  expectNoErrorBanner,
  expectNoHorizontalOverflow,
  expectReadableBodyText,
  openSession,
  saveScreenshot,
  type UiSession,
} from './helpers.js';

async function auditPage(page: Page, testInfo: TestInfo, name: string): Promise<void> {
  await expectNoErrorBanner(page);
  await expectNoHorizontalOverflow(page);
  await expectReadableBodyText(page);
  await saveScreenshot(page, `${testInfo.project.name}-${name}.png`);
}

test.describe('附表 界面与视觉核对（desktop / desktop-1920 / mobile-390）', () => {
  test(
    '登录页：无横向溢出、无错误横幅、文字可读并留存截图',
    { annotation: [{ type: 'SCREEN', description: '登录页 05 §2 /login' }] },
    async ({ browser }, testInfo) => {
      const context = await browser.newContext();
      const page = await context.newPage();
      try {
        await page.goto('/login');
        await expect(page.getByTestId(TESTID.loginSubmit)).toBeVisible();
        await auditPage(page, testInfo, 'login');
      } finally {
        await context.close();
      }
    },
  );

  test(
    '活动列表：无横向溢出、无错误横幅、文字可读并留存截图',
    { annotation: [{ type: 'SCREEN', description: '活动列表 05 §2 /activities' }] },
    async ({ browser }, testInfo) => {
      const sessions: UiSession[] = [];
      try {
        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);
        await organizer.page.goto('/activities');
        await expect(organizer.page.getByText(ACTIVITIES.a.title).first()).toBeVisible();
        await auditPage(organizer.page, testInfo, 'activities');
      } finally {
        await closeSessions(sessions);
      }
    },
  );

  test(
    '活动详情：无横向溢出、无错误横幅、文字可读并留存截图',
    { annotation: [{ type: 'SCREEN', description: '活动详情 05 §2 /activities/:id' }] },
    async ({ browser }, testInfo) => {
      const sessions: UiSession[] = [];
      try {
        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);
        await organizer.page.goto(activityPath(ACTIVITIES.a.id));
        await expect(organizer.page.getByTestId(TESTID.currentVersion)).toBeVisible();
        await auditPage(organizer.page, testInfo, 'activity-detail');
      } finally {
        await closeSessions(sessions);
      }
    },
  );

  test(
    '改期工作台：无横向溢出、无错误横幅、文字可读并留存截图',
    { annotation: [{ type: 'SCREEN', description: '改期工作台 05 §2 /activities/:id/change' }] },
    async ({ browser }, testInfo) => {
      const sessions: UiSession[] = [];
      try {
        const organizer = await openSession(browser, USERS.organizerA);
        sessions.push(organizer);
        await organizer.page.goto(changeWorkbenchPath(ACTIVITIES.a.id));
        await expect(organizer.page.getByTestId(TESTID.changePreview)).toBeVisible();
        await auditPage(organizer.page, testInfo, 'change-workbench');
      } finally {
        await closeSessions(sessions);
      }
    },
  );
});
