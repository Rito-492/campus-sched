import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import DraftEditor from '../src/components/DraftEditor.vue'
import { api } from '../src/api'
import { ApiError } from '../src/api/client'
import { actor, activity, resources } from './fixtures'
vi.mock('../src/api', () => ({
  api: { staff: vi.fn(), editActivity: vi.fn() },
}))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
  vi.resetAllMocks()
})
describe('草稿编辑与版本冲突', () => {
  it('等待409重载后用新lockVersion保存，保留用户输入', async () => {
    vi.mocked(api.staff).mockResolvedValue({ data: [], requestId: 'r' })
    const current = ref(activity({ status: 'DRAFT', effectiveVersionNo: null }))
    let finishRefresh!: () => void
    const refresh = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finishRefresh = () => {
            current.value = { ...current.value, versionNo: 4, lockVersion: 9 }
            resolve()
          }
        }),
    )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/',
          component: defineComponent({
            setup: () => () =>
              h(DraftEditor, {
                activity: current.value,
                actor: actor({ id: current.value.ownerId }),
                resources,
                refresh,
              }),
          }),
        },
      ],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(RouterView, {
      attachTo: document.body,
      global: { plugins: [router, ElementPlus] },
    })
    wrappers.push(wrapper)
    await flushPromises()
    vi.mocked(api.editActivity).mockReturnValue({
      key: '00000000-0000-4000-8000-000000000001',
      run: vi
        .fn()
        .mockRejectedValue(new ApiError(409, 'STALE_VERSION', '版本已变化')),
    })
    await wrapper
      .get('[data-testid="draft-title"]')
      .setValue('保留我编辑的标题')
    await wrapper.get('[data-testid="draft-save"]').trigger('click')
    await flushPromises()
    expect(refresh).toHaveBeenCalledOnce()
    expect(
      wrapper.get('[data-testid="draft-save"]').attributes('disabled'),
    ).toBeDefined()
    finishRefresh()
    await flushPromises()
    expect(
      (wrapper.get('[data-testid="draft-title"]').element as HTMLInputElement)
        .value,
    ).toBe('保留我编辑的标题')
    vi.mocked(api.editActivity).mockReturnValue({
      key: '00000000-0000-4000-8000-000000000002',
      run: vi.fn().mockResolvedValue({ data: current.value, requestId: 'r' }),
    })
    await wrapper.get('[data-testid="draft-save"]').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.editActivity).mock.calls[1][1]).toMatchObject({
      expectedLockVersion: 9,
      input: { title: '保留我编辑的标题' },
    })
  })
})
