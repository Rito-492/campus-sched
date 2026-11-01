import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
const source = readFileSync(new URL('../../agents/common/02-数据与接口契约.md', import.meta.url), 'utf8')
const block = source.match(/```ts\r?\n([\s\S]*?)```/)?.[1]
if (!block) throw new Error('缺少权威 DTO 定义，禁止猜测字段')
const expected = '// 由 agents/common/02 的 TypeScript 契约生成；npm run types:sync 更新。\n' + block.replace(/\r\n/g, '\n').replace(/^(type|interface) /gm, 'export $1 ')
const file = new URL('../src/types/api.ts', import.meta.url)
if (process.argv.includes('--check')) {
  if (readFileSync(file, 'utf8') !== expected) throw new Error('前端 DTO 与公共契约不同步')
} else {
  mkdirSync(new URL('../src/types/', import.meta.url), { recursive: true })
  writeFileSync(file, expected)
}
