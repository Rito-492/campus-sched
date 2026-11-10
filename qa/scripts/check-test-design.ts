/**
 * 测试设计用例数据质量门禁（QA-05 任务5：test-data/test-design-cases-v1.0.json）。
 *
 * 校验内容（任务书要求，缺失或 0 条一律失败，不假通过）：
 *   1. 结构：schemaVersion=1、document、environment、dataDictionary、scenarios；
 *   2. 必填：场景/用例每字段存在且具体（preconditions/inputs/steps/expected 非空字符串数组）；
 *   3. 唯一 ID：场景 id 与用例 id 均不得重复（用例 id 全局唯一）；
 *   4. 需求覆盖：全部 UC-01～UC-20 在 scenarios[].requirementIds 中出现（02 契约口径 UC-01～20）；
 *   5. 极性配比：每个场景至少 1 条 positive、2 条 negative；
 *   6. 用例状态：status 必须逐条为「未执行」（未真实执行不得写通过）；
 *   7. 验收覆盖：D-AT-01～24 与 N-AT-01～06 共 30 个验收编号在 scenarios[].acceptanceIds 中出现
 *      （agents/common/03-验收与演示数据.md §2/§6）；
 *   8. 设计引用覆盖：API-01～API-40 共 40 个接口编号出现在用例 inputs/steps/automation 文本中
 *      （02 契约 §3 接口清单）。
 *
 * 除“非空”外另有内容级检查（避免只看非空就宣称语义正确）：
 *   - inputs/steps/expected/preconditions/title/flow 不得为 TODO/待定/占位等占位内容（硬性失败）；
 *   - negative 用例的 expected 未见失败/拒绝/限制/降级/无副作用等负面结果信号时仅提示人工复核
 *     （warning，不硬失败；语义正误难自动判定，不逼文档虚构错误码）。
 * 语义深度（如步骤与预期的因果一致性）不在自动可判范围，由评审人工核对，见报告说明。
 *
 * 用法：node scripts/check-test-design.ts [--file 路径]
 *      （默认 test-data/test-design-cases-v1.0.json；文件缺失必须失败退出 1）
 * 导出：validate(doc)、validateJsonText(text)、formatReport(result) 供复用/自测调用。
 */
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------- 期望集合（任务包口径）

/** UC-01～UC-20（docs/requirements/use-cases.md、01-基线 §1）。 */
export const EXPECTED_UC_IDS: string[] = Array.from({ length: 20 }, (_, i) => `UC-${String(i + 1).padStart(2, '0')}`);

/** D-AT-01～24 与 N-AT-01～06（03-验收与演示数据.md §2/§6，共 30 项）。 */
export const EXPECTED_ACCEPTANCE_IDS: string[] = [
  ...Array.from({ length: 24 }, (_, i) => `D-AT-${String(i + 1).padStart(2, '0')}`),
  ...Array.from({ length: 6 }, (_, i) => `N-AT-${String(i + 1).padStart(2, '0')}`),
];

/** API-01～API-40（02-数据与接口契约.md §3）。 */
export const EXPECTED_API_COUNT = 40;

const POLARITIES = new Set(['positive', 'negative']);
const PRIORITIES = new Set(['P0', 'P1', 'P2']);
const CASE_STATUS = '未执行';

/** 允许的需求编号前缀与范围（01-基线 §1：FR-01～25、UC-01～20、BR-01～32、NFR-01～30）。 */
const REQUIREMENT_RANGES: Record<string, number> = { UC: 20, FR: 25, BR: 32, NFR: 30 };

/** 内容占位符（出现即视为未真正设计，不是具体条件/输入/步骤/预期）。 */
const PLACEHOLDER = /^(?:todo|tbd|n\/?a|x+|\?+|待定|待补(?:充)?|占位(?:符)?|略)$/i;

/** negative 用例预期的负面结果信号（失败/拒绝/错误码、限制/降级或无副作用表述）。
 *  语义正误难以完全自动判定：未命中仅提示人工复核（warning），不硬失败、不逼文档虚构错误码。 */
const NEGATIVE_ERROR_SIGNAL = /(4\d{2}|错误|失败|拒绝|驳回|不允许|不得|禁止|异常|冲突|超限|不足|无效|回滚|INVALID|ERROR|FAIL|REJECT|CONFLICT|FORBIDDEN|UNAUTHENTICATED|VALIDATION|NOT[_ ]?FOUND)/i;
const NEGATIVE_NO_EFFECT_SIGNAL =
  /无[^。；;，,]*(新增|产生|写入|变化|变更|记录|部分|占用|预约|复活|泄露|溢出|伪造|冒充|虚构)|不变|保持|仍为|仍[是为]|NULL|不[得能会]?(显示|出现|展示|渲染|落|吞|生成|新增|写入|推进|泄露|返回|变化|复活|占用|预约|记录|伪造|冒充|虚构|刷新|生效)|非\s*0|仅在|只在|降级|退回|冻结|保留|限制|超时|不可用|清除/i;

// ---------------------------------------------------------------- 类型

export interface DocumentMeta {
  title: string;
  version: string;
  author: string;
  date: string;
  status: string;
}

export interface DataDictionaryEntry {
  key: string;
  value: string;
}

export interface CaseSpec {
  id: string;
  title: string;
  polarity: 'positive' | 'negative';
  priority: 'P0' | 'P1' | 'P2';
  flow: string;
  preconditions: string[];
  inputs: string[];
  steps: string[];
  expected: string[];
  cleanup: string;
  automation: string;
  status: string;
}

export interface ScenarioSpec {
  id: string;
  name: string;
  requirementIds: string[];
  acceptanceIds: string[];
  basicFlow: string;
  alternateFlows: string[];
  cases: CaseSpec[];
}

export interface TestDesignDoc {
  schemaVersion: number;
  document: DocumentMeta;
  environment: string[];
  dataDictionary: DataDictionaryEntry[];
  scenarios: ScenarioSpec[];
}

export interface ValidationResult {
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

// ---------------------------------------------------------------- 基础判定

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((v) => typeof v === 'string');
}

/** 归一化编号：大写去非字母数字，数字后缀补零（UC-5 / uc05 → UC05）。 */
export function canonicalId(raw: string): string {
  const compact = raw.toUpperCase().replace(/[^A-Z0-9]/g, '');
  const m = /^([A-Z]+)0*(\d+)$/.exec(compact);
  return m ? `${m[1]}${m[2].padStart(2, '0')}` : compact;
}

const EXPECTED_UC_SET = new Set(EXPECTED_UC_IDS.map(canonicalId));
const EXPECTED_ACCEPTANCE_SET = new Set(EXPECTED_ACCEPTANCE_IDS.map(canonicalId));

/** 文本中引用的 API-xx 编号（大小写、空格/下划线/连字符容错）。 */
const API_REF = /\bAPI[\s\-_]?(\d{1,2})\b/gi;

function apiRefsIn(text: string): number[] {
  const found: number[] = [];
  for (const m of text.matchAll(API_REF)) {
    found.push(Number(m[1]));
  }
  return found;
}

// ---------------------------------------------------------------- 校验主体

/**
 * 校验测试设计用例文档（已解析对象）。
 * 返回全部问题清单与统计；ok=false 时不得当作通过。
 */
export function validate(doc: unknown): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];
  const stats = {
    scenarioCount: 0,
    caseCount: 0,
    positiveCount: 0,
    negativeCount: 0,
    ucCovered: 0,
    acceptanceCovered: 0,
    apiCovered: 0,
  };

  if (!isRecord(doc)) {
    errors.push('根节点必须是 JSON 对象');
    return { ok: false, errors, warnings, stats };
  }

  // ---- 结构：schemaVersion / document / environment / dataDictionary
  if (doc.schemaVersion !== 1) {
    errors.push(`schemaVersion 必须为 1（实际：${JSON.stringify(doc.schemaVersion)}）`);
  }

  const meta = doc.document;
  if (!isRecord(meta)) {
    errors.push('document 缺失或不是对象');
  } else {
    for (const field of ['title', 'version', 'author', 'date', 'status'] as const) {
      if (!isNonEmptyString(meta[field])) {
        errors.push(`document.${field} 必须为非空字符串`);
      }
    }
  }

  if (!isStringArray(doc.environment)) {
    errors.push('environment 必须为字符串数组');
  } else if (doc.environment.length === 0) {
    errors.push('environment 为 0 条：必须写明执行环境');
  } else {
    doc.environment.forEach((item, i) => {
      if (!isNonEmptyString(item)) errors.push(`environment[${i}] 必须为非空字符串`);
    });
  }

  if (!Array.isArray(doc.dataDictionary)) {
    errors.push('dataDictionary 必须为数组');
  } else if (doc.dataDictionary.length === 0) {
    errors.push('dataDictionary 为 0 条：必须定义测试数据字典');
  } else {
    doc.dataDictionary.forEach((item, i) => {
      if (!isRecord(item) || !isNonEmptyString(item.key) || !isNonEmptyString(item.value)) {
        errors.push(`dataDictionary[${i}] 必须含非空 key 与非空 value`);
      }
    });
  }

  // ---- 场景与用例
  const scenarios = doc.scenarios;
  if (!Array.isArray(scenarios)) {
    errors.push('scenarios 必须为数组');
    return { ok: false, errors, warnings, stats };
  }
  if (scenarios.length === 0) {
    errors.push('scenarios 为 0 条：无场景的测试设计必须失败');
    return { ok: false, errors, warnings, stats };
  }

  const seenScenarioIds = new Map<string, number>();
  const seenCaseIds = new Map<string, string>();
  const ucSeen = new Set<string>();
  const ucScenarioIds = new Set<string>();
  const acceptanceSeen = new Set<string>();
  const apiSeen = new Set<number>();
  const requirementRefs: Record<string, Set<string>> = { FR: new Set(), BR: new Set(), NFR: new Set() };

  scenarios.forEach((scenario, si) => {
    const where = `scenarios[${si}]`;
    if (!isRecord(scenario)) {
      errors.push(`${where} 必须是对象`);
      return;
    }
    stats.scenarioCount += 1;

    // 场景必填
    if (!isNonEmptyString(scenario.id)) errors.push(`${where}.id 必须为非空字符串`);
    if (!isNonEmptyString(scenario.name)) errors.push(`${where}.name 必须为非空字符串`);
    if (!isNonEmptyString(scenario.basicFlow)) errors.push(`${where}.basicFlow 必须为非空字符串`);
    if (!isStringArray(scenario.alternateFlows)) {
      errors.push(`${where}.alternateFlows 必须为字符串数组`);
    } else {
      scenario.alternateFlows.forEach((flow, i) => {
        if (!isNonEmptyString(flow)) errors.push(`${where}.alternateFlows[${i}] 必须为非空字符串`);
      });
    }

    // 唯一 ID（场景）；本轮约定场景 id=UC-01～UC-20，与需求用例一一对应
    if (isNonEmptyString(scenario.id)) {
      const key = canonicalId(scenario.id);
      if (seenScenarioIds.has(key)) {
        errors.push(`${where}.id 场景 ID 重复：${scenario.id}（与 scenarios[${seenScenarioIds.get(key)}] 冲突）`);
      } else {
        seenScenarioIds.set(key, si);
      }
      if (EXPECTED_UC_SET.has(key)) {
        ucScenarioIds.add(key);
      }
    }

    // 需求编号：格式/范围 + 收集覆盖
    if (!isStringArray(scenario.requirementIds) || scenario.requirementIds.length === 0) {
      errors.push(`${where}.requirementIds 必须为非空字符串数组`);
    } else {
      scenario.requirementIds.forEach((rid, i) => {
        if (!isNonEmptyString(rid)) {
          errors.push(`${where}.requirementIds[${i}] 必须为非空字符串`);
          return;
        }
        const m = /^(UC|FR|BR|NFR)-?(\d{1,2})$/i.exec(rid.trim());
        if (!m) {
          errors.push(`${where}.requirementIds[${i}] 非法需求编号：${rid}（应形如 UC-05 / FR-12 / BR-07 / NFR-08）`);
          return;
        }
        const max = REQUIREMENT_RANGES[m[1].toUpperCase()];
        const num = Number(m[2]);
        if (num < 1 || num > max) {
          errors.push(`${where}.requirementIds[${i}] 超出范围：${rid}（${m[1].toUpperCase()} 仅 1~${max}）`);
          return;
        }
        if (m[1].toUpperCase() === 'UC') {
          ucSeen.add(canonicalId(`${m[1]}-${m[2].padStart(2, '0')}`));
        } else {
          requirementRefs[m[1].toUpperCase()].add(canonicalId(`${m[1]}-${m[2].padStart(2, '0')}`));
        }
      });
    }

    // 验收编号：必须是 30 个已知编号之一 + 收集覆盖
    if (!isStringArray(scenario.acceptanceIds) || scenario.acceptanceIds.length === 0) {
      errors.push(`${where}.acceptanceIds 必须为非空字符串数组`);
    } else {
      scenario.acceptanceIds.forEach((aid, i) => {
        if (!isNonEmptyString(aid)) {
          errors.push(`${where}.acceptanceIds[${i}] 必须为非空字符串`);
          return;
        }
        const canon = canonicalId(aid);
        if (!EXPECTED_ACCEPTANCE_SET.has(canon)) {
          errors.push(`${where}.acceptanceIds[${i}] 未知验收编号：${aid}（仅 D-AT-01～24 / N-AT-01～06）`);
          return;
        }
        acceptanceSeen.add(canon);
      });
    }

    // 用例
    const cases = scenario.cases;
    if (!Array.isArray(cases)) {
      errors.push(`${where}.cases 必须为数组`);
      return;
    }
    if (cases.length === 0) {
      errors.push(`${where}.cases 为 0 条：场景必须有用例`);
      return;
    }

    let positive = 0;
    let negative = 0;

    cases.forEach((kase, ci) => {
      const at = `${where}.cases[${ci}]`;
      if (!isRecord(kase)) {
        errors.push(`${at} 必须是对象`);
        return;
      }
      stats.caseCount += 1;

      // 标识与枚举
      if (!isNonEmptyString(kase.id)) {
        errors.push(`${at}.id 必须为非空字符串`);
      } else {
        const key = canonicalId(kase.id);
        const prev = seenCaseIds.get(key);
        if (prev) {
          errors.push(`${at}.id 用例 ID 重复：${kase.id}（与 ${prev} 冲突）`);
        } else {
          seenCaseIds.set(key, `${at}（${kase.id}）`);
        }
      }
      if (!isNonEmptyString(kase.title)) errors.push(`${at}.title 必须为非空字符串`);
      if (typeof kase.polarity !== 'string' || !POLARITIES.has(kase.polarity)) {
        errors.push(`${at}.polarity 必须为 positive|negative（实际：${JSON.stringify(kase.polarity)}）`);
      } else if (kase.polarity === 'positive') {
        positive += 1;
        stats.positiveCount += 1;
      } else {
        negative += 1;
        stats.negativeCount += 1;
      }
      if (typeof kase.priority !== 'string' || !PRIORITIES.has(kase.priority)) {
        errors.push(`${at}.priority 必须为 P0|P1|P2（实际：${JSON.stringify(kase.priority)}）`);
      }
      if (!isNonEmptyString(kase.flow)) errors.push(`${at}.flow 必须为非空字符串`);
      // 备选流引用一致性（非阻塞提示）：flow 引用备选流N 不得超出 alternateFlows 数量
      if (isNonEmptyString(kase.flow) && isStringArray(scenario.alternateFlows)) {
        const flowRef = /^备选流\s*(\d+)$/.exec(kase.flow.trim());
        if (flowRef && Number(flowRef[1]) > scenario.alternateFlows.length) {
          warnings.push(`${at}.flow 引用了未知「${kase.flow}」（${where}.alternateFlows 仅 ${scenario.alternateFlows.length} 条）`);
        }
      }

      // 具体条件 / 输入 / 步骤 / 预期：逐条非空、非占位
      const listFields = ['preconditions', 'inputs', 'steps', 'expected'] as const;
      for (const field of listFields) {
        const value = kase[field];
        if (!isStringArray(value) || value.length === 0) {
          errors.push(`${at}.${field} 必须为非空字符串数组（每条用例必须写明${field === 'preconditions' ? '具体条件' : field === 'inputs' ? '输入' : field === 'steps' ? '步骤' : '预期'}）`);
          continue;
        }
        value.forEach((item, i) => {
          if (!isNonEmptyString(item)) {
            errors.push(`${at}.${field}[${i}] 不能为空`);
          } else if (PLACEHOLDER.test(item.trim())) {
            errors.push(`${at}.${field}[${i}] 是占位内容而非具体设计：${item}`);
          }
        });
      }

      if (!isNonEmptyString(kase.cleanup)) errors.push(`${at}.cleanup 必须为非空字符串（无清理也写「无」）`);
      if (!isNonEmptyString(kase.automation)) errors.push(`${at}.automation 必须为非空字符串`);

      // 状态：必须逐条未执行
      if (kase.status !== CASE_STATUS) {
        errors.push(`${at}.status 必须为「${CASE_STATUS}」（实际：${JSON.stringify(kase.status)}；未真实执行不得写其他状态）`);
      }

      // negative 用例预期的负面结果信号：语义正误难自动判定，未命中仅提示人工复核（非阻塞）；
      // 占位内容仍硬性拒绝；不得逼文档为过检虚构错误码
      if (kase.polarity === 'negative' && isStringArray(kase.expected) && kase.expected.length > 0) {
        const text = kase.expected.join('；');
        if (!NEGATIVE_ERROR_SIGNAL.test(text) && !NEGATIVE_NO_EFFECT_SIGNAL.test(text)) {
          warnings.push(`${at}.expected 未见失败/拒绝/限制/降级/无副作用等负面结果信号，请人工复核极性与预期是否匹配：${text}`);
        }
      }

      // API 设计引用（inputs/steps/automation 文本）：收集覆盖并拦截未知编号
      const inputText = isStringArray(kase.inputs) ? kase.inputs.join('\n') : '';
      const stepText = isStringArray(kase.steps) ? kase.steps.join('\n') : '';
      const autoText = typeof kase.automation === 'string' ? kase.automation : '';
      const scanText = `${inputText}\n${stepText}\n${autoText}`;
      for (const n of apiRefsIn(scanText)) {
        if (n < 1 || n > EXPECTED_API_COUNT) {
          errors.push(`${at} 引用了未知接口编号 API-${n}（仅 API-01～API-${EXPECTED_API_COUNT}）`);
        } else {
          apiSeen.add(n);
        }
      }
    });

    // 极性配比：每场景至少 1 positive + 2 negative
    if (positive < 1) {
      errors.push(`${where} 缺 positive 用例（至少 1 条，实际 ${positive} 条）`);
    }
    if (negative < 2) {
      errors.push(`${where} 缺 negative 用例（至少 2 条，实际 ${negative} 条）`);
    }
  });

  // ---- 场景编号覆盖：本轮约定场景 id=UC-01～UC-20，与需求用例一一对应
  //（只在 requirementIds 里罗列全部 UC 的单个大场景不算覆盖）
  const missingUcScenarios = EXPECTED_UC_IDS.filter((id) => !ucScenarioIds.has(canonicalId(id)));
  if (missingUcScenarios.length > 0) {
    errors.push(
      `场景编号覆盖不足：约定场景 id=UC-01～UC-20 一一对应，缺少 ${missingUcScenarios.join('、')}` +
        '（要求每个 UC 有独立业务场景；仅在单个大场景 requirementIds 里罗列全部 UC 不算覆盖）',
    );
  }

  // ---- 覆盖：UC 20 / 验收 30 / API 40（缺失或 0 条即失败）
  const missingUc = EXPECTED_UC_IDS.filter((id) => !ucSeen.has(canonicalId(id)));
  stats.ucCovered = EXPECTED_UC_IDS.length - missingUc.length;
  if (missingUc.length > 0) {
    errors.push(`需求覆盖不足：UC ${stats.ucCovered}/${EXPECTED_UC_IDS.length}，缺少 ${missingUc.join('、')}`);
  }

  const missingAcceptance = EXPECTED_ACCEPTANCE_IDS.filter((id) => !acceptanceSeen.has(canonicalId(id)));
  stats.acceptanceCovered = EXPECTED_ACCEPTANCE_IDS.length - missingAcceptance.length;
  if (missingAcceptance.length > 0) {
    errors.push(`验收覆盖不足：${stats.acceptanceCovered}/${EXPECTED_ACCEPTANCE_IDS.length}，缺少 ${missingAcceptance.join('、')}`);
  }

  const missingApi: number[] = [];
  for (let n = 1; n <= EXPECTED_API_COUNT; n += 1) {
    if (!apiSeen.has(n)) missingApi.push(n);
  }
  stats.apiCovered = EXPECTED_API_COUNT - missingApi.length;
  if (missingApi.length > 0) {
    errors.push(
      `API 设计引用覆盖不足：${stats.apiCovered}/${EXPECTED_API_COUNT}（扫描 inputs/steps/automation），` +
        `缺少 ${missingApi.map((n) => `API-${String(n).padStart(2, '0')}`).join('、')}`,
    );
  }

  // ---- FR/BR/NFR：只统计引用情况，不硬性要求全覆盖（提示）
  for (const [prefix, max] of [['FR', 25], ['BR', 32], ['NFR', 30]] as const) {
    const seen = requirementRefs[prefix];
    const missing: string[] = [];
    for (let n = 1; n <= max; n += 1) {
      const id = `${prefix}-${String(n).padStart(2, '0')}`;
      if (!seen.has(canonicalId(id))) missing.push(id);
    }
    if (seen.size > 0 || missing.length < max) {
      warnings.push(`需求引用统计（仅提示，不要求全覆盖）：${prefix} ${seen.size}/${max}，未引用 ${missing.join('、') || '无'}`);
    }
  }

  warnings.push(
    '自动校验只覆盖结构/必填/唯一性/覆盖量与少量内容信号；步骤与预期的因果语义仍需人工评审，不能以本检查通过宣称设计语义正确。',
  );

  return { ok: errors.length === 0, errors, warnings, stats };
}

/** 校验 JSON 文本（解析失败按校验失败处理，不抛异常）。 */
export function validateJsonText(text: string): ValidationResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    return {
      ok: false,
      errors: [`JSON 解析失败：${message}`],
      warnings: [],
      stats: { scenarioCount: 0, caseCount: 0, positiveCount: 0, negativeCount: 0, ucCovered: 0, acceptanceCovered: 0, apiCovered: 0 },
    };
  }
  return validate(parsed);
}

/** 人类可读报告。 */
export function formatReport(result: ValidationResult, filePath?: string): string {
  const lines: string[] = [];
  lines.push(`[check-test-design] 文件：${filePath ?? '(内存对象)'}`);
  lines.push(
    `[check-test-design] 场景 ${result.stats.scenarioCount} 个 / 用例 ${result.stats.caseCount} 条` +
      `（positive ${result.stats.positiveCount}、negative ${result.stats.negativeCount}）`,
  );
  lines.push(
    `[check-test-design] 覆盖：UC ${result.stats.ucCovered}/${EXPECTED_UC_IDS.length}、` +
      `验收 ${result.stats.acceptanceCovered}/${EXPECTED_ACCEPTANCE_IDS.length}、` +
      `API ${result.stats.apiCovered}/${EXPECTED_API_COUNT}`,
  );
  if (result.ok) {
    lines.push('[check-test-design] 通过：结构、必填、唯一ID、极性配比、未执行状态与覆盖检查全部通过');
  } else {
    lines.push(`[check-test-design] 失败：共 ${result.errors.length} 项问题`);
    result.errors.forEach((e, i) => lines.push(`  ${i + 1}. ${e}`));
  }
  for (const w of result.warnings) {
    lines.push(`[check-test-design] 提示：${w}`);
  }
  return lines.join('\n');
}

// ---------------------------------------------------------------- CLI

const scriptDir = dirname(fileURLToPath(import.meta.url));

/** 默认输入：仓库根 test-data/test-design-cases-v1.0.json。 */
export function defaultInputPath(): string {
  return resolve(scriptDir, '..', '..', 'test-data', 'test-design-cases-v1.0.json');
}

/** 解析 CLI 参数（--file <path> 或 --file=<path>）；返回输入路径或错误信息。 */
function parseArgs(argv: string[]): { file?: string; error?: string } {
  let file: string | undefined;
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--file') {
      const next = argv[i + 1];
      if (!next || next.startsWith('--')) return { error: '--file 缺少路径参数' };
      file = next;
      i += 1;
    } else if (arg.startsWith('--file=')) {
      file = arg.slice('--file='.length);
      if (!file) return { error: '--file 缺少路径参数' };
    } else if (arg === '--help' || arg === '-h') {
      return { error: '用法：node scripts/check-test-design.ts [--file <测试设计用例JSON路径>]' };
    } else {
      return { error: `未知参数：${arg}` };
    }
  }
  return { file };
}

/**
 * CLI 入口：返回进程退出码（0 通过；其余失败）。
 * 文件缺失、JSON 非法、任何校验问题均返回 1，绝不假通过。
 */
export function runCli(argv: string[]): number {
  const parsed = parseArgs(argv);
  if (parsed.error) {
    console.error(`[check-test-design] 失败：${parsed.error}`);
    return 1;
  }
  const filePath = parsed.file ? resolve(parsed.file) : defaultInputPath();
  if (!existsSync(filePath)) {
    console.error(
      `[check-test-design] 失败：找不到 ${filePath}` +
        '（test-design-cases-v1.0.json 由测试设计文档编写者提供；缺失时质量门禁必须失败）',
    );
    return 1;
  }
  const result = validateJsonText(readFileSync(filePath, 'utf8'));
  const report = formatReport(result, filePath);
  if (result.ok) {
    console.log(report);
    return 0;
  }
  console.error(report);
  return 1;
}

// 直接执行时进入 CLI（被 import 时不执行，供自测调用 validate）。
const invokedDirectly = process.argv[1] !== undefined && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (invokedDirectly) {
  process.exit(runCli(process.argv.slice(2)));
}
