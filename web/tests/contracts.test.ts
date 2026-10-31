import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it, vi } from 'vitest'
import { parse } from 'yaml'
import { api } from '../src/api'
import { actor, activity, resources } from './fixtures'
import type { Proposal } from '../src/types/api'
const contract = parse(
  readFileSync(resolve('../contracts/openapi.yaml'), 'utf8'),
)
describe('公共契约一致性', () => {
  it('前端DTO逐字同步common/02，Actor字段由同一契约生成', () => {
    const md = readFileSync(
      resolve('../agents/common/02-数据与接口契约.md'),
      'utf8',
    )
    const block = md
      .match(/```ts\r?\n([\s\S]*?)```/)![1]
      .replace(/\r\n/g, '\n')
      .replace(/^(type|interface) /gm, 'export $1 ')
    expect(
      readFileSync(resolve('src/types/api.ts'), 'utf8')
        .split('\n')
        .slice(1)
        .join('\n'),
    ).toBe(block)
  })
  it('40项方法、路径及持久化幂等头匹配OpenAPI，预览不带命令头', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({ data: [], requestId: 'test', truncated: false }),
          ),
        ),
      )
    vi.stubGlobal('fetch', fetcher)
    try {
      const a = activity(),
        version = { expectedLockVersion: a.lockVersion },
        proposal: Proposal = {
          baseVersionNo: a.versionNo,
          targetPlan: a.input.plan,
        },
        decision = { decision: 'APPROVE' as const, comment: '' },
        fault = {
          startAt: a.input.plan.startAt,
          endAt: a.input.plan.endAt,
          unavailableQuantity: 1,
          reason: 'test',
        }
      const calls = [
        () => api.login('test', 'test'),
        () => api.me(),
        () => api.logout(),
        () => api.users(),
        () =>
          api
            .createUser({
              ...actor(),
              username: 'test',
              password: 'test-password',
            })
            .run(),
        () => api.enableUser('id', false).run(),
        () => api.resources(),
        () => api.createResource(resources[0]).run(),
        () => api.calendar(a.input.plan.startAt, a.input.plan.endAt),
        () => api.createFault('id', fault).run(),
        () => api.faults('id', fault.startAt, fault.endAt),
        () => api.createActivity(a.input).run(),
        () => api.activities('mine'),
        () => api.activity('id'),
        () => api.editActivity('id', { ...version, input: a.input }).run(),
        () => api.submit('id', version).run(),
        () => api.withdraw('id', { ...version, reason: 'test' }).run(),
        () => api.approve('id', { ...version, ...decision }).run(),
        () => api.reserve('id', version).run(),
        () => api.publish('id', version).run(),
        () => api.cancel('id', { ...version, reason: 'test' }).run(),
        () =>
          api
            .registration('id', { action: 'JOIN', expectedVersionNo: 2 })
            .run(),
        () => api.registrations('id'),
        () => api.preview('id', proposal),
        () => api.alternatives('id', proposal),
        () => api.changePreview('id', proposal),
        () =>
          api
            .createChange('id', { ...proposal, ...version, reason: 'test' })
            .run(),
        () => api.changes('id'),
        () => api.change('id'),
        () => api.confirmChange('id', decision).run(),
        () => api.approveChange('id', decision).run(),
        () => api.reserveChange('id', decision).run(),
        () => api.withdrawChange('id', 'test').run(),
        () => api.notifications(),
        () => api.readNotification('id').run(),
        () => api.audit('id'),
        () => api.health(),
        () => api.organizations(),
        () => api.authorization('id', actor()).run(),
        () =>
          api
            .eligibility('id', { allowTeacher: false, allowStudent: true })
            .run(),
      ]
      expect(calls).toHaveLength(40)
      for (const [index, call] of calls.entries()) {
        await call()
        const [url, options] = fetcher.mock.calls[index] as [
          string,
          RequestInit,
        ]
        const path = url
          .replace('/api/v1', '')
          .split('?')[0]
          .replace('/id', '/{id}')
        const operation =
          contract.paths[path]?.[(options.method ?? 'GET').toLowerCase()]
        expect(operation?.operationId, `${options.method} ${path}`).toBe(
          `API-${String(index + 1).padStart(2, '0')}`,
        )
        const requiredKey = (operation.parameters ?? []).some(
          (p: { name: string }) => p.name === 'Idempotency-Key',
        )
        expect(
          !!(options.headers as Record<string, string>)['Idempotency-Key'],
        ).toBe(requiredKey)
      }
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
