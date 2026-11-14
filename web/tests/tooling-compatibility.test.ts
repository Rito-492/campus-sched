import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { mkdtempSync, readFileSync, readdirSync, rmdirSync, unlinkSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

// Scoped overrides patch the formatter dependencies of @vue/test-utils.
// Exercise their public consumers, including the CLI's glob/editorconfig paths.
describe('patched test tooling compatibility', () => {
  it('formats nested Vue markup through wrapper.html()', () => {
    const wrapper = mount({ template: '<section><h1>活动审批</h1><p>待资源确认</p></section>' })
    try {
      expect(wrapper.html()).toBe('<section>\n  <h1>活动审批</h1>\n  <p>待资源确认</p>\n</section>')
    } finally {
      wrapper.unmount()
    }
  })

  it('expands formatter file globs and applies EditorConfig patterns', () => {
    const require = createRequire(import.meta.url)
    const testUtilsRequire = createRequire(require.resolve('@vue/test-utils'))
    const cli = testUtilsRequire.resolve('js-beautify/js/bin/js-beautify.js')
    const directory = mkdtempSync(join(tmpdir(), 'campus-ui-tools-'))
    try {
      writeFileSync(join(directory, '.editorconfig'), 'root = true\n\n[*.js]\nindent_style = space\nindent_size = 2\n')
      writeFileSync(join(directory, 'activity.js'), 'function activity(){return "活动";}')
      writeFileSync(join(directory, 'calendar.js'), 'function calendar(){return "日历";}')
      writeFileSync(join(directory, 'untouched.txt'), 'function untouched(){return 1;}')
      const result = spawnSync(process.execPath, [cli, '--files', '*.js', '--replace', '--editorconfig'], {
        cwd: directory,
        encoding: 'utf8',
        timeout: 10_000,
        windowsHide: true,
      })
      expect(result.error).toBeUndefined()
      expect(result.status, result.stderr).toBe(0)
      expect(readFileSync(join(directory, 'activity.js'), 'utf8')).toContain('\n  return "活动";\n')
      expect(readFileSync(join(directory, 'calendar.js'), 'utf8')).toContain('\n  return "日历";\n')
      expect(readFileSync(join(directory, 'untouched.txt'), 'utf8')).toBe('function untouched(){return 1;}')
    } finally {
      for (const file of readdirSync(directory)) unlinkSync(join(directory, file))
      rmdirSync(directory)
    }
  }, 15_000)
})
