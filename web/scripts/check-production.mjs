import { readdirSync, readFileSync } from 'node:fs'
const root = new URL('../dist/', import.meta.url)
function scan(dir) {
  for (const item of readdirSync(dir, { withFileTypes: true })) {
    const path = new URL(item.name + (item.isDirectory() ? '/' : ''), dir)
    if (item.isDirectory()) scan(path)
    else if (/\.(js|html)$/.test(item.name) && /UI_FIXTURE_ONLY|\/__prototype/.test(readFileSync(path, 'utf8'))) throw new Error('生产产物包含开发原型')
  }
}
scan(root)
console.log('生产产物检查通过：无开发原型入口或演示数据标识。')
