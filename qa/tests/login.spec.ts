/**
 * D-AT-01 登录、令牌与响应脱敏（E2E）。
 *
 * 用例依据：`docs/testing/test-design.md` TC-01-1 / TC-01-2；
 * 契约依据：`agents/common/02-数据与接口契约.md` API-01/API-02、§1 响应信封、
 *           `agents/common/05-界面规范.md` §2 /login、§4 login-submit。
 *
 * 执行环境：真实后端（BASE_URL 指向独立 Compose 测试环境，dev,e2e profile），禁止 Mock。
 * 时序：状态/跳转一律 expect 轮询，不用 page.waitForTimeout。
 */
import { expect, test } from '@playwright/test';
import { DEMO_PASSWORD, TESTID, USERS } from './helpers.js';

/** 递归扫描 JSON 任意层级是否出现 password/passwordHash 键（TC-01-1 观察点）。 */
function findPasswordKeys(value: unknown, path = '$'): string[] {
  if (value === null || typeof value !== 'object') return [];
  const violations: string[] = [];
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    if (/password/i.test(key)) violations.push(`${path}.${key}`);
    violations.push(...findPasswordKeys(child, `${path}.${key}`));
  }
  return violations;
}

const BCRYPT_PATTERN = /\$2[aby]\$/;
const HASH_KEY_PATTERN = /passwordHash|password_hash/i;

test.describe('D-AT-01 登录、令牌与响应脱敏', () => {
  test(
    'TC-01-1 organizer_a 正常登录进入主页，登录响应无密码字段、页面无散列信息',
    { annotation: [{ type: 'D-AT', description: 'D-AT-01' }, { type: 'TC', description: 'TC-01-1' }] },
    async ({ browser }) => {
      const context = await browser.newContext();
      const page = await context.newPage();
      try {
        await page.goto('/login');
        await page.getByLabel(/账号|用户名/).fill(USERS.organizerA);
        await page.getByLabel(/密码/).fill(DEMO_PASSWORD);

        const [loginResponse] = await Promise.all([
          page.waitForResponse((res) => res.url().includes('/api/v1/auth/login')),
          page.getByTestId(TESTID.loginSubmit).click(),
        ]);

        // API-01：登录成功 200，响应信封 {data:{token,expiresAt,user},requestId}
        expect(loginResponse.status(), '登录应返回 200').toBe(200);
        const body = (await loginResponse.json()) as Record<string, unknown>;
        expect(body, '登录响应应含 requestId').toHaveProperty('requestId');
        expect(findPasswordKeys(body), '登录响应任意层级不得出现 password/passwordHash 键').toEqual([]);

        // 登录成功进入 /activities（05 §2）
        await expect(page).toHaveURL(/\/activities(?:[/?#]|$)/);

        // 页面不得出现 BCrypt 散列或散列字段名
        const html = await page.content();
        expect(html, '页面不得出现 BCrypt 散列').not.toMatch(BCRYPT_PATTERN);
        expect(html, '页面不得出现 passwordHash/password_hash').not.toMatch(HASH_KEY_PATTERN);
      } finally {
        await context.close();
      }
    },
  );

  test(
    'TC-01-2 错误密码返回 401 并显示错误提示，停留登录页，随后正确密码仍可登录',
    { annotation: [{ type: 'D-AT', description: 'D-AT-01' }, { type: 'TC', description: 'TC-01-2' }] },
    async ({ browser }) => {
      const context = await browser.newContext();
      const page = await context.newPage();
      try {
        await page.goto('/login');
        await page.getByLabel(/账号|用户名/).fill(USERS.organizerA);
        await page.getByLabel(/密码/).fill('wrong-password-for-dat01');

        const [failResponse] = await Promise.all([
          page.waitForResponse((res) => res.url().includes('/api/v1/auth/login')),
          page.getByTestId(TESTID.loginSubmit).click(),
        ]);

        expect(failResponse.status(), '错误密码应返回 401').toBe(401);
        const failBody = (await failResponse.json()) as {
          error?: { code?: string; message?: string; details?: unknown };
          requestId?: string;
        };
        expect(failBody.error?.code, '错误码应为 UNAUTHENTICATED（02 §1）').toBe('UNAUTHENTICATED');
        expect(failBody.error?.message, '错误应含中文可读原因').toBeTruthy();
        expect(findPasswordKeys(failBody), '错误响应不得含 passwordHash').toEqual([]);

        // 仍停留在登录页，并显示错误提示（05 §2 login 提交反馈/错误原因）
        await expect(page).toHaveURL(/\/login(?:[/?#]|$)/);
        // 登录错误提示 testid 待 UI 提供，见 handoff；用 alert 语义定位
        await expect(page.getByRole('alert').filter({ hasText: /密码|账号|认证|失败/ })).toBeVisible();

        // 账号未被锁死：正确密码再次登录 200
        await page.getByLabel(/密码/).fill(DEMO_PASSWORD);
        const [okResponse] = await Promise.all([
          page.waitForResponse((res) => res.url().includes('/api/v1/auth/login')),
          page.getByTestId(TESTID.loginSubmit).click(),
        ]);
        expect(okResponse.status(), '正确密码重试应 200').toBe(200);
        await expect(page).toHaveURL(/\/activities(?:[/?#]|$)/);
      } finally {
        await context.close();
      }
    },
  );

  test(
    'D-AT-01 登录后页面与浏览器存储不出现密码散列或明文口令',
    { annotation: [{ type: 'D-AT', description: 'D-AT-01' }] },
    async ({ browser }) => {
      const context = await browser.newContext();
      const page = await context.newPage();
      try {
        await page.goto('/login');
        await page.getByLabel(/账号|用户名/).fill(USERS.organizerA);
        await page.getByLabel(/密码/).fill(DEMO_PASSWORD);
        await page.getByTestId(TESTID.loginSubmit).click();
        await expect(page).toHaveURL(/\/activities(?:[/?#]|$)/);

        const html = await page.content();
        const storage = await page.evaluate(() =>
          JSON.stringify({ localStorage: { ...localStorage }, sessionStorage: { ...sessionStorage } }),
        );

        expect(html, '页面不得出现 BCrypt 散列').not.toMatch(BCRYPT_PATTERN);
        expect(storage, '浏览器存储不得出现 BCrypt 散列').not.toMatch(BCRYPT_PATTERN);
        expect(storage, '浏览器存储不得出现 passwordHash/password_hash').not.toMatch(HASH_KEY_PATTERN);
        expect(html, '页面不得出现明文演示口令').not.toContain(DEMO_PASSWORD);
      } finally {
        await context.close();
      }
    },
  );
});
