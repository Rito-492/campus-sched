import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { gzipSync } from 'node:zlib'

const { values } = parseArgs({
  options: {
    output: { type: 'string' },
    check: { type: 'boolean', default: false },
  },
})
const root = new URL('../dist/', import.meta.url)
const manifest = JSON.parse(
  readFileSync(new URL('.vite/manifest.json', root), 'utf8'),
)
const sizes = new Map()
function measure(file) {
  if (!sizes.has(file)) {
    const content = readFileSync(new URL(file, root))
    sizes.set(file, {
      file,
      bytes: content.length,
      gzipBytes: gzipSync(content).length,
    })
  }
  return sizes.get(file)
}
function collect(keys) {
  const visited = new Set(),
    files = new Set()
  function visit(key) {
    if (visited.has(key)) return
    visited.add(key)
    const chunk = manifest[key]
    if (!chunk) throw new Error(`构建清单缺少模块：${key}`)
    files.add(chunk.file)
    for (const css of chunk.css ?? []) files.add(css)
    for (const imported of chunk.imports ?? []) visit(imported)
  }
  keys.forEach(visit)
  return [...files].sort()
}
function summarize(files) {
  const assets = files.map(measure)
  const sum = (extension, field) =>
    assets
      .filter((a) => a.file.endsWith(extension))
      .reduce((total, a) => total + a[field], 0)
  return {
    files,
    jsBytes: sum('.js', 'bytes'),
    jsGzipBytes: sum('.js', 'gzipBytes'),
    cssBytes: sum('.css', 'bytes'),
    cssGzipBytes: sum('.css', 'gzipBytes'),
  }
}
const assets = readdirSync(new URL('assets/', root))
  .filter((file) => /\.(js|css)$/.test(file))
  .map((file) => `assets/${file}`)
const report = {
  metric:
    '构建产物字节数；gzip逐文件计算；页面包括入口及其静态依赖，不包括其他路由动态依赖',
  entry: summarize(collect(['index.html'])),
  login: summarize(collect(['index.html', 'src/pages/LoginPage.vue'])),
  total: summarize(assets),
  largestJavaScript: assets
    .filter((file) => file.endsWith('.js'))
    .map(measure)
    .sort((a, b) => b.bytes - a.bytes)[0],
}
if (values.output)
  writeFileSync(resolve(values.output), JSON.stringify(report, null, 2) + '\n')
console.log(
  `最大JS块 ${(report.largestJavaScript.bytes / 1000).toFixed(2)} kB；登录页静态JS合计 ${(report.login.jsBytes / 1000).toFixed(2)} kB（gzip ${(report.login.jsGzipBytes / 1000).toFixed(2)} kB）；CSS ${(report.login.cssBytes / 1000).toFixed(2)} kB。`,
)
if (
  values.check &&
  (report.largestJavaScript.bytes > 500_000 || report.login.jsBytes > 400_000)
)
  throw new Error(
    '前端体积预算超限：单JS块须≤500kB，登录页全部静态JS须≤400kB。',
  )
