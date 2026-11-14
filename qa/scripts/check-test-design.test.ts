/**
 * check-test-design.ts 质量门禁的本地自测（Node 内置 node:test，不依赖产品可执行/数据库）。
 *
 * 要求（任务书）：先证明检查能真实失败，再确认正例通过——
 * 本文件以「有效文档 + 一组反例」双向断言门禁本身：
 *   有效：结构/必填/唯一ID/UC-01～20/每场景1+2极性/未执行状态/30验收/40API 全满足时必须通过；
 *   反例：缺负面、空输入、重复ID、漏UC/验收/API、错误status、占位内容、未知编号、
 *         JSON非法、文件缺失等必须失败并给出对应错误（不是仅返回非0）。
 *
 * 运行：node --test scripts/check-test-design.test.ts   （在 qa/ 目录）
 *      或 node --test qa/scripts/check-test-design.test.ts（在仓库根目录）
 */
import { strict as assert } from 'node:assert';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

/** 门禁模块的导出形状（运行时用动态导入加载，避免 tsc 对 .ts 直引路径的限制）。 */
interface GateModule {
  validate: (doc: unknown) => GateResult;
  validateJsonText: (text: string) => GateResult;
  formatReport: (result: GateResult, filePath?: string) => string;
  EXPECTED_UC_IDS: string[];
  EXPECTED_ACCEPTANCE_IDS: string[];
  EXPECTED_API_COUNT: number;
}
interface GateResult {
  ok: boolean;
  errors: string[];
  warnings: string[];
  stats: {
    scenarioCount: number;
    caseCount: number;
    positiveCount: number;
    negativeCount: number;
    ucCovered: number;
    acceptanceCovered: number;
    apiCovered: number;
  };
}

const gate: GateModule = await import(new URL('./check-test-design.ts', import.meta.url).href);
const { validate, validateJsonText, formatReport, EXPECTED_UC_IDS, EXPECTED_ACCEPTANCE_IDS, EXPECTED_API_COUNT } = gate;

const scriptDir = dirname(fileURLToPath(import.meta.url));
const CLI = join(scriptDir, 'check-test-design.ts');

// ---------------------------------------------------------------- 夹具构造

type AnyObj = Record<string, any>;

function makeCase(id: string, polarity: 'positive' | 'negative', apiRef: number, expectedText?: string): AnyObj {
  return {
    id,
    title: `${id} 用例标题（自测夹具）`,
    polarity,
    priority: polarity === 'positive' ? 'P0' : 'P1',
    flow: polarity === 'positive' ? '基本流' : '备选流1',
    preconditions: [`已登录演示账号，钟固定 ${'2030-10-19T00:00:00Z'}`],
    inputs: [`请求体含 title=自测活动${id}，开始 2030-10-21T10:00:00+08:00`],
    steps: [`调用 API-${String(apiRef).padStart(2, '0')} 提交请求并记录响应`],
    expected:
      polarity === 'positive'
        ? ['返回200，活动状态推进且数据库记录一致']
        : [expectedText ?? '返回400 VALIDATION_ERROR，且数据库无新增预约记录'],
    cleanup: '删除本用例创建的活动与预约',
    automation: `api-automation 映射 API-${String(apiRef).padStart(2, '0')}`,
    status: '未执行',
  };
}

/** 构造完全有效的文档：20 场景（id=UC-01～20）覆盖 UC-01～20、30 验收、40 API，每场景 1+2 极性。 */
function makeValidDoc(): AnyObj {
  const scenarios: AnyObj[] = [];
  let apiCounter = 1;
  for (let i = 1; i <= 20; i += 1) {
    const sIdx = String(i).padStart(2, '0');
    const cases: AnyObj[] = [];
    for (const polarity of ['positive', 'negative', 'negative'] as const) {
      cases.push(makeCase(`TC-${sIdx}-${cases.length + 1}`, polarity, apiCounter));
      apiCounter = (apiCounter % EXPECTED_API_COUNT) + 1;
    }
    scenarios.push({
      id: `UC-${sIdx}`,
      name: `场景${sIdx}：UC-${sIdx} 覆盖（自测夹具）`,
      requirementIds: [`UC-${sIdx}`],
      acceptanceIds: [],
      basicFlow: '登录→创建→提交→审批→确认→发布（自测夹具描述）',
      alternateFlows: ['备选流1：参数非法返回400并回滚'],
      cases,
    });
  }
  // 30 个验收编号轮转分到 20 个场景
  EXPECTED_ACCEPTANCE_IDS.forEach((aid, i) => {
    scenarios[i % scenarios.length].acceptanceIds.push(aid);
  });
  return {
    schemaVersion: 1,
    document: {
      title: '测试设计用例 v1.0（自测夹具）',
      version: '1.0',
      author: '测试经理',
      date: '2026-11-03',
      status: '评审中',
    },
    environment: ['Node 22+、Java 21、PostgreSQL 17、campus_test 独立 schema'],
    dataDictionary: [{ key: 'DEMO_DATE', value: '2030-10-20（固定演示日期）' }],
    scenarios,
  };
}

function assertFailsWith(result: ReturnType<typeof validate>, fragment: string, label: string) {
  assert.equal(result.ok, false, `${label}：校验应当失败`);
  assert.ok(
    result.errors.some((e) => e.includes(fragment)),
    `${label}：错误清单应含「${fragment}」，实际：\n${result.errors.join('\n')}`,
  );
}

// ---------------------------------------------------------------- 正例

test('有效文档：全部检查通过且统计正确', () => {
  const doc = makeValidDoc();
  const result = validate(doc);
  assert.equal(result.ok, true, `有效文档不应报错：\n${result.errors.join('\n')}`);
  assert.equal(result.stats.scenarioCount, 20);
  assert.equal(result.stats.caseCount, 60);
  assert.equal(result.stats.positiveCount, 20);
  assert.equal(result.stats.negativeCount, 40);
  assert.equal(result.stats.ucCovered, EXPECTED_UC_IDS.length);
  assert.equal(result.stats.acceptanceCovered, EXPECTED_ACCEPTANCE_IDS.length);
  assert.equal(result.stats.apiCovered, EXPECTED_API_COUNT);
  assert.match(formatReport(result), /通过/);
});

// ---------------------------------------------------------------- 反例（证明能真实失败）

test('反例：场景缺 negative 用例必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases = doc.scenarios[0].cases.filter((c: AnyObj) => c.polarity === 'positive');
  assertFailsWith(validate(doc), '缺 negative 用例', '缺负面');
});

test('反例：negative 仅 1 条不满足至少 2 条', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases = doc.scenarios[0].cases.filter((c: AnyObj, i: number) => i !== 2);
  assertFailsWith(validate(doc), '至少 2 条', '负面不足');
});

test('反例：场景缺 positive 用例必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases = doc.scenarios[0].cases.filter((c: AnyObj) => c.polarity === 'negative');
  assertFailsWith(validate(doc), '缺 positive 用例', '缺正面');
});

test('反例：inputs 为空数组必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases[0].inputs = [];
  assertFailsWith(validate(doc), 'inputs 必须为非空字符串数组', '空输入');
});

test('反例：inputs 含空字符串必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases[0].inputs = ['  '];
  assertFailsWith(validate(doc), 'inputs[0] 不能为空', '空输入条目');
});

test('反例：steps/expected/preconditions 为空也必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases[0].steps = [];
  doc.scenarios[0].cases[1].expected = [];
  doc.scenarios[0].cases[2].preconditions = [];
  const result = validate(doc);
  for (const fragment of ['steps 必须为非空字符串数组', 'expected 必须为非空字符串数组', 'preconditions 必须为非空字符串数组']) {
    assert.ok(result.errors.some((e) => e.includes(fragment)), `应报「${fragment}」`);
  }
});

test('反例：用例 ID 重复必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[1].cases[0].id = doc.scenarios[0].cases[0].id;
  assertFailsWith(validate(doc), '用例 ID 重复', '重复用例ID');
});

test('反例：场景 ID 重复必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[1].id = doc.scenarios[0].id;
  assertFailsWith(validate(doc), '场景 ID 重复', '重复场景ID');
});

test('反例：漏 UC-05 必须失败并列出编号', () => {
  const doc = makeValidDoc();
  for (const s of doc.scenarios) {
    s.requirementIds = s.requirementIds.filter((r: string) => r !== 'UC-05');
    if (s.requirementIds.length === 0) s.requirementIds = ['UC-01'];
  }
  assertFailsWith(validate(doc), 'UC-05', '漏UC');
});

test('反例：漏 D-AT-07 必须失败并列出编号', () => {
  const doc = makeValidDoc();
  for (const s of doc.scenarios) {
    s.acceptanceIds = s.acceptanceIds.filter((a: string) => a !== 'D-AT-07');
    if (s.acceptanceIds.length === 0) s.acceptanceIds = ['D-AT-01'];
  }
  assertFailsWith(validate(doc), 'D-AT-07', '漏验收编号');
});

test('反例：漏 API-13 引用必须失败并列出编号', () => {
  const doc = makeValidDoc();
  for (const s of doc.scenarios) {
    for (const c of s.cases) {
      c.steps = c.steps.map((t: string) => t.replace(/API-13/g, 'API-01'));
      c.automation = String(c.automation).replace(/API-13/g, 'API-01');
    }
  }
  assertFailsWith(validate(doc), 'API-13', '漏API引用');
});

test('反例：status 非「未执行」必须失败（写成已执行/通过均拒）', () => {
  for (const wrong of ['已执行', '通过', 'passed', '']) {
    const doc = makeValidDoc();
    doc.scenarios[0].cases[0].status = wrong;
    assertFailsWith(validate(doc), 'status 必须为「未执行」', `错误status=${JSON.stringify(wrong)}`);
  }
});

test('反例：polarity/priority 枚举错误必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases[0].polarity = 'happy';
  doc.scenarios[0].cases[1].priority = 'P9';
  const result = validate(doc);
  assertFailsWith(result, 'polarity 必须为 positive|negative', '错误polarity');
  assertFailsWith(result, 'priority 必须为 P0|P1|P2', '错误priority');
});

test('反例：schemaVersion 不为 1、document 字段缺失必须失败', () => {
  const doc = makeValidDoc();
  doc.schemaVersion = 2;
  doc.document.author = '';
  delete doc.document.status;
  const result = validate(doc);
  assertFailsWith(result, 'schemaVersion 必须为 1', '错误schemaVersion');
  assertFailsWith(result, 'document.author 必须为非空字符串', '空author');
  assertFailsWith(result, 'document.status 必须为非空字符串', '缺status');
});

test('反例：0 场景 / 场景 0 用例必须失败（0 条不假通过）', () => {
  const noScenario = makeValidDoc();
  noScenario.scenarios = [];
  assertFailsWith(validate(noScenario), 'scenarios 为 0 条', '0场景');

  const noCases = makeValidDoc();
  noCases.scenarios[0].cases = [];
  assertFailsWith(validate(noCases), 'cases 为 0 条', '0用例');
});

test('反例：environment/dataDictionary 为空必须失败', () => {
  const doc = makeValidDoc();
  doc.environment = [];
  doc.dataDictionary = [];
  const result = validate(doc);
  assertFailsWith(result, 'environment 为 0 条', '0环境');
  assertFailsWith(result, 'dataDictionary 为 0 条', '0数据字典');
});

test('反例：步骤/预期为 TODO 等占位内容必须失败（不只看非空）', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases[0].steps = ['TODO'];
  doc.scenarios[0].cases[1].expected = ['待补充'];
  assertFailsWith(validate(doc), '占位内容', '占位内容');
});

test('提示不阻塞：negative 只写成功预期→提示人工复核，不硬失败（语义正误难自动判定）', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].cases[1].expected = ['请求成功，返回200并创建记录'];
  const result = validate(doc);
  assert.equal(result.ok, true, `负面语义检查只提示不硬失败：\n${result.errors.join('\n')}`);
  assert.ok(
    result.warnings.some((w) => w.includes('人工复核')),
    `应有人工复核提示，实际：\n${result.warnings.join('\n')}`,
  );
});

test('提示不阻塞：拒绝/限制/降级/无副作用等负面表述不误伤（不逼文档虚构错误码）', () => {
  const doc = makeValidDoc();
  // 真实设计里合法的负面预期表述（取自成稿门禁实测被关键词误伤的语义）
  doc.scenarios[0].cases[1].expected = ['页面提示服务器退出结果待核实，不显示『已成功退出』类虚假成功；本地令牌清除并回到 /login 页'];
  doc.scenarios[0].cases[2].expected = ['可用量下限为 0（不出现负数）；日历不显示登记人与私有原因（BR-05）'];
  const result = validate(doc);
  assert.equal(result.ok, true, `合法负面表述不得判失败：\n${result.errors.join('\n')}`);
  assert.equal(
    result.warnings.filter((w) => w.includes('人工复核')).length,
    0,
    `合法负面表述不应触发人工复核提示：\n${result.warnings.join('\n')}`,
  );
});

test('反例：未知验收编号/API 超范围必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].acceptanceIds = ['D-AT-01', 'D-AT-25'];
  doc.scenarios[0].cases[0].steps = ['调用 API-41 越界接口'];
  const result = validate(doc);
  assertFailsWith(result, '未知验收编号', '未知验收');
  assertFailsWith(result, '未知接口编号 API-41', '未知API');
});

test('反例：需求编号格式/范围错误必须失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].requirementIds = ['UC-21', '用例五'];
  const result = validate(doc);
  assertFailsWith(result, '超出范围', 'UC超范围');
  assertFailsWith(result, '非法需求编号', '需求编号格式');
});

test('反例：单个大场景罗列全部 UC 也不能替代 20 个业务场景（场景编号覆盖）', () => {
  const doc = makeValidDoc();
  // 合并成 1 个场景：requirementIds 全量覆盖 UC，但场景 id 只有 UC-01
  doc.scenarios = [doc.scenarios[0]];
  doc.scenarios[0].requirementIds = EXPECTED_UC_IDS.slice();
  doc.scenarios[0].acceptanceIds = EXPECTED_ACCEPTANCE_IDS.slice();
  assertFailsWith(validate(doc), '场景编号覆盖不足', '单大场景');
});

test('反例：场景 id 不含 UC-05 时即使 requirementIds 有 UC-05 也失败', () => {
  const doc = makeValidDoc();
  doc.scenarios[4].id = 'S05';
  assertFailsWith(validate(doc), '缺少 UC-05', '缺UC场景');
});

test('提示不阻塞：FR/NFR/BR 只统计不硬要求全覆盖；未知备选流仅提示', () => {
  const doc = makeValidDoc();
  doc.scenarios[0].requirementIds = ['UC-01', 'FR-01', 'NFR-08'];
  doc.scenarios[0].cases[0].flow = '备选流5'; // alternateFlows 仅 1 条
  const result = validate(doc);
  assert.equal(result.ok, true, `提示项不得判失败：\n${result.errors.join('\n')}`);
  assert.ok(result.warnings.some((w) => w.includes('FR 1/25')), '应有 FR 引用统计提示');
  assert.ok(result.warnings.some((w) => w.includes('NFR 1/30')), '应有 NFR 引用统计提示');
  assert.ok(result.warnings.some((w) => w.includes('未知「备选流5」')), '应有未知备选流提示');
});

test('反例：JSON 非法与非对象根节点必须失败', () => {
  assert.equal(validateJsonText('{ not json').ok, false);
  assert.ok(validateJsonText('{ not json').errors[0].includes('JSON 解析失败'));
  assert.equal(validateJsonText('[1,2,3]').ok, false);
});

// ---------------------------------------------------------------- CLI 真实执行

const tmp = mkdtempSync(join(tmpdir(), 'check-test-design-selftest-'));

test('CLI：有效文件退出 0，无效文件退出 1，文件缺失退出 1', () => {
  const validPath = join(tmp, 'valid.json');
  writeFileSync(validPath, JSON.stringify(makeValidDoc()), 'utf8');
  const okRun = spawnSync(process.execPath, [CLI, '--file', validPath], { encoding: 'utf8' });
  assert.equal(okRun.status, 0, `有效文件应退出0：${okRun.stderr}`);

  const badDoc = makeValidDoc();
  badDoc.scenarios[0].cases[0].status = '已执行';
  const badPath = join(tmp, 'bad.json');
  writeFileSync(badPath, JSON.stringify(badDoc), 'utf8');
  const badRun = spawnSync(process.execPath, [CLI, `--file=${badPath}`], { encoding: 'utf8' });
  assert.equal(badRun.status, 1, '无效文件应退出1');
  assert.match(badRun.stderr, /status 必须为「未执行」/);

  const missingRun = spawnSync(process.execPath, [CLI, '--file', join(tmp, 'no-such-file.json')], { encoding: 'utf8' });
  assert.equal(missingRun.status, 1, '文件缺失应退出1');
  assert.match(missingRun.stderr, /找不到/);

  rmSync(tmp, { recursive: true, force: true });
});
