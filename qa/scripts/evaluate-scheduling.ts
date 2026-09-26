/**
 * 策略评测结果汇总（QA-04，契约 03-验收与演示数据.md §4）。
 *
 * 输入：server/target/scheduling-evaluation.json（由 SchedulingBenchmarkIT 生成，
 *       格式见 docs/testing/test-data-plan.md §9）
 * 输出：artifacts/evaluation/results.csv、artifacts/evaluation/summary.md
 *
 * 契约要点：
 * - CSV 列固定为 scenarioId、strategy、feasible、timeShiftMinutes、venueChanged、
 *   replacedEquipmentLines、evaluatedCount、elapsedMs（03 §4）；
 * - 无可行方案时三个代价字段写 null，不伪造 0 代价（02 契约 §8）；
 * - 缺少原始结果时失败退出，绝不自动造数（03 §4）；
 * - 摘要记录机器、版本、数据及计时范围，报告中位数及范围（03 §4）。
 *
 * 用法：node scripts/evaluate-scheduling.ts [输入JSON路径]（默认仓库根下 server/target/...）
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------- 类型

interface EvalRun {
  scenarioId: string;
  strategy: string;
  runIndex: number;
  feasible: boolean;
  timeShiftMinutes: number | null;
  venueChanged: boolean | null;
  replacedEquipmentLines: number | null;
  evaluatedCount: number;
  elapsedMs: number;
}

interface EvalInput {
  schemaVersion: number;
  generatedAt?: string;
  environment?: Record<string, string>;
  timingScope?: string;
  runs: EvalRun[];
}

interface ScenarioExpected {
  feasible: boolean;
  firstFitChoice: { timeShiftMinutes: number; venueChanged: boolean; replacedEquipmentLines: number } | null;
  rankedChoice: { timeShiftMinutes: number; venueChanged: boolean; replacedEquipmentLines: number } | null;
}

interface ScenarioFile {
  evaluation: { strategies: string[]; maxCandidates: number; warmupRuns: number; measuredRuns: number };
  scenarios: { scenarioId: string; expected: ScenarioExpected }[];
}

// ---------------------------------------------------------------- 路径

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '..', '..');
const inputPath = process.argv[2] ?? join(repoRoot, 'server', 'target', 'scheduling-evaluation.json');
const scenarioPath = join(repoRoot, 'test-data', 'scenarios', 'scheduling.json');
const outDir = join(repoRoot, 'artifacts', 'evaluation');

function fail(message: string): never {
  console.error(`[evaluate-scheduling] 失败：${message}`);
  process.exit(1);
}

// ---------------------------------------------------------------- 读取

if (!existsSync(inputPath)) {
  fail(`缺少原始结果 ${inputPath}。请先运行 SchedulingBenchmarkIT（03 §4）；本脚本不会生成占位数据。`);
}
if (!existsSync(scenarioPath)) {
  fail(`缺少场景定义 ${scenarioPath}`);
}

let input: EvalInput;
try {
  input = JSON.parse(readFileSync(inputPath, 'utf8')) as EvalInput;
} catch (e) {
  fail(`原始结果不是合法 JSON：${(e as Error).message}`);
}
const scenarioFile = JSON.parse(readFileSync(scenarioPath, 'utf8')) as ScenarioFile;

if (input.schemaVersion !== 1) fail(`不支持的原始结果 schemaVersion：${input.schemaVersion}`);
if (!Array.isArray(input.runs) || input.runs.length === 0) fail('原始结果不含任何测量行（runs 为空）');

const strategies = scenarioFile.evaluation.strategies;
const measuredRuns = scenarioFile.evaluation.measuredRuns;
const maxCandidates = scenarioFile.evaluation.maxCandidates;
const knownScenarios = new Map(scenarioFile.scenarios.map((s) => [s.scenarioId, s]));

// ---------------------------------------------------------------- 校验

const errors: string[] = [];
const groups = new Map<string, EvalRun[]>();

for (const [i, run] of input.runs.entries()) {
  const where = `runs[${i}](${run.scenarioId}/${run.strategy}#${run.runIndex})`;
  if (!knownScenarios.has(run.scenarioId)) errors.push(`${where}：场景不在 scheduling.json 中`);
  if (!strategies.includes(run.strategy)) errors.push(`${where}：策略 ${run.strategy} 不在约定列表 ${strategies.join('/')}`);
  if (!Number.isInteger(run.runIndex) || run.runIndex < 1) errors.push(`${where}：runIndex 必须为正整数`);
  if (!Number.isFinite(run.elapsedMs) || run.elapsedMs <= 0) errors.push(`${where}：elapsedMs 必须为正数`);
  if (!Number.isInteger(run.evaluatedCount) || run.evaluatedCount < 0 || run.evaluatedCount > maxCandidates) {
    errors.push(`${where}：evaluatedCount 必须在 0..${maxCandidates}（01 基线 §7 搜索预算）`);
  }

  const costs = [run.timeShiftMinutes, run.venueChanged, run.replacedEquipmentLines];
  if (run.feasible) {
    if (costs.some((c) => c === null || c === undefined)) {
      errors.push(`${where}：可行结果必须给出三个代价字段`);
    }
  } else if (costs.some((c) => c !== null)) {
    errors.push(`${where}：无可行方案时三个代价字段必须为 null（02 契约 §8，不伪造 0 代价）`);
  }

  const key = `${run.scenarioId}\u0000${run.strategy}`;
  const list = groups.get(key) ?? [];
  list.push(run);
  groups.set(key, list);
}

// 每个 场景×策略 必须恰好 measuredRuns 行，且确定性结果一致
const keyOf = (scenarioId: string, strategy: string) => `${scenarioId}\u0000${strategy}`;
for (const s of scenarioFile.scenarios) {
  for (const strategy of strategies) {
    const rows = groups.get(keyOf(s.scenarioId, strategy)) ?? [];
    if (rows.length !== measuredRuns) {
      errors.push(`${s.scenarioId}/${strategy}：测量行 ${rows.length} 行 ≠ 约定 ${measuredRuns} 行（缺少原始结果不得补齐）`);
      continue;
    }
    const runIdx = new Set(rows.map((r) => r.runIndex));
    if (runIdx.size !== rows.length) errors.push(`${s.scenarioId}/${strategy}：runIndex 重复`);
    const sig = (r: EvalRun) =>
      JSON.stringify([r.feasible, r.timeShiftMinutes, r.venueChanged, r.replacedEquipmentLines, r.evaluatedCount]);
    const first = sig(rows[0]);
    if (rows.some((r) => sig(r) !== first)) {
      errors.push(`${s.scenarioId}/${strategy}：多次测量结果不一致（策略应为确定性）`);
    }
  }
}

if (errors.length > 0) {
  for (const e of errors) console.error(`[evaluate-scheduling] ${e}`);
  fail(`共 ${errors.length} 处校验失败，不生成任何产物`);
}

// ---------------------------------------------------------------- CSV

const csvHeader =
  'scenarioId,strategy,feasible,timeShiftMinutes,venueChanged,replacedEquipmentLines,evaluatedCount,elapsedMs';
const cell = (v: number | boolean | null) => (v === null ? 'null' : String(v));
const sorted = [...input.runs].sort(
  (a, b) =>
    a.scenarioId.localeCompare(b.scenarioId) ||
    a.strategy.localeCompare(b.strategy) ||
    a.runIndex - b.runIndex,
);
const csvLines = sorted.map((r) =>
  [
    r.scenarioId,
    r.strategy,
    cell(r.feasible),
    cell(r.timeShiftMinutes),
    cell(r.venueChanged),
    cell(r.replacedEquipmentLines),
    String(r.evaluatedCount),
    String(r.elapsedMs),
  ].join(','),
);

// ---------------------------------------------------------------- 汇总

const median = (values: number[]): number => {
  const s = [...values].sort((a, b) => a - b);
  const mid = s.length >> 1;
  return s.length % 2 === 1 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
};
const fmt = (v: number | null | undefined) => (v === null || v === undefined ? 'null' : v.toFixed(3).replace(/\.?0+$/, ''));

const summaryRows: string[] = [];
const expectationRows: string[] = [];
const unsolved: string[] = [];

for (const s of scenarioFile.scenarios) {
  const perStrategy: Record<string, EvalRun[]> = {};
  for (const strategy of strategies) {
    perStrategy[strategy] = groups.get(keyOf(s.scenarioId, strategy)) ?? [];
  }
  for (const strategy of strategies) {
    const rows = perStrategy[strategy];
    const sample = rows[0];
    const times = rows.map((r) => r.elapsedMs);
    summaryRows.push(
      `| ${s.scenarioId} | ${strategy} | ${rows.length} | ${sample.feasible} | ${cell(sample.timeShiftMinutes)} | ${cell(sample.venueChanged)} | ${cell(sample.replacedEquipmentLines)} | ${sample.evaluatedCount} | ${fmt(median(times))} | ${fmt(Math.min(...times))} | ${fmt(Math.max(...times))} |`,
    );
    if (!sample.feasible) unsolved.push(`${s.scenarioId}/${strategy}`);
  }

  // 期望对照（scheduling.json 的 expected 是断言目标，test-design TC-22-1）
  const cmp = (strategy: string, expected: ScenarioExpected['firstFitChoice']) => {
    const sample = (perStrategy[strategy] ?? [])[0];
    const actual =
      sample && sample.feasible
        ? {
            timeShiftMinutes: sample.timeShiftMinutes,
            venueChanged: sample.venueChanged,
            replacedEquipmentLines: sample.replacedEquipmentLines,
          }
        : null;
    const same =
      JSON.stringify(actual) === JSON.stringify(expected ?? null) &&
      (sample ? sample.feasible === s.expected.feasible : false);
    const text = (v: ScenarioExpected['firstFitChoice'] | typeof actual) =>
      v === null ? 'null' : `(${v.timeShiftMinutes},${v.venueChanged ? 1 : 0},${v.replacedEquipmentLines})`;
    expectationRows.push(
      `| ${s.scenarioId} | ${strategy} | ${text(expected)} | ${text(actual)} | ${same ? '一致' : '**不一致**'} |`,
    );
    return same;
  };
  cmp('FIRST_FIT', s.expected.firstFitChoice);
  cmp('RANKED', s.expected.rankedChoice);
}

const env = input.environment ?? {};
const pick = (k: string) => env[k] ?? '未提供（由 SchedulingBenchmarkIT 写入）';
const summary = `# 策略评测摘要

> 由 qa/scripts/evaluate-scheduling.ts 从原始测量行生成；原始行见 results.csv。
> 缺少原始结果时脚本失败退出，不生成任何产物（03 §4）。本文件不含人工填写数值。

- 生成时间：${input.generatedAt ?? new Date().toISOString()}
- 机器：${pick('machine')} / ${pick('os')}
- JDK：${pick('jdk')}；被测构建：${pick('commit')}
- 场景数据：test-data/scenarios/scheduling.json（DEMO_DATE=${pick('demoDate')}，Clock=${pick('clock')}）
- 计时范围：${input.timingScope ?? '候选生成、资源读取、约束检查及选择/排序（不含 HTTP 与文件输出，02 契约 §8）'}
- 测量：每场景每策略预热 ${scenarioFile.evaluation.warmupRuns} 次（已丢弃）、测量 ${measuredRuns} 次；中位数与范围如下

## 各场景 × 策略

| scenarioId | strategy | n | feasible | timeShift | venueChanged | replacedLines | evaluatedCount | 中位数ms | min | max |
|---|---|---|---|---|---|---|---|---|---|---|
${summaryRows.join('\n')}

## 期望对照（断言目标 vs 实测）

| scenarioId | strategy | 期望代价 | 实测代价 | 结论 |
|---|---|---|---|---|
${expectationRows.join('\n')}

## 无解记录

${unsolved.length === 0 ? '（本次无不可行结果）' : unsolved.map((u) => `- ${u}：0 个结果只表示约定搜索范围未找到方案（01 基线 §7）`).join('\n')}
`;

// ---------------------------------------------------------------- 写出

if (expectationRows.some((r) => r.includes('不一致'))) {
  // 仍然先落盘证据，再失败退出——如实保留不一致现场
  mkdirSync(outDir, { recursive: true });
  writeFileSync(join(outDir, 'results.csv'), [csvHeader, ...csvLines].join('\n') + '\n');
  writeFileSync(join(outDir, 'summary.md'), summary);
  fail('期望对照存在不一致，已保留证据于 artifacts/evaluation/，请交 DS/PM 核查（不修改契约迎合实现）');
}

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, 'results.csv'), [csvHeader, ...csvLines].join('\n') + '\n');
writeFileSync(join(outDir, 'summary.md'), summary);
console.log(`[evaluate-scheduling] 完成：${csvLines.length} 行 → artifacts/evaluation/results.csv + summary.md`);
