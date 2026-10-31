import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ApiFeedback from '../src/components/ApiFeedback.vue'
import { ApiError } from '../src/api/client'

describe('服务端资源资格错误', () => {
  it('用已加载资源名解释资格失败，保留未返回资源ID和追踪编号', () => {
    const wrapper = mount(ApiFeedback, {
      props: {
        error: new ApiError(
          403,
          'RESOURCE_INELIGIBLE',
          '资格不足',
          'request-123',
          { resourceIds: ['venue', 'unlisted'], identity: 'STUDENT' },
        ),
        resources: [{ id: 'venue', name: '教师研讨室' }],
      },
      global: { stubs: { 'el-button': true, 'el-skeleton': true } },
    })
    expect(wrapper.get('[data-testid="eligibility-error"]').text()).toContain(
      '教师研讨室、unlisted',
    )
    expect(wrapper.text()).toContain('request-123')
  })
  it('防御未知details，不把其他403错误解释成资源资格', () => {
    const mountError = (code: string, details: unknown) =>
      mount(ApiFeedback, {
        props: { error: new ApiError(403, code, '请求被拒绝', '', details) },
        global: { stubs: { 'el-button': true, 'el-skeleton': true } },
      })
    expect(
      mountError('RESOURCE_INELIGIBLE', { resourceIds: [null, 12] }).text(),
    ).toContain('未提供受限资源清单')
    expect(
      mountError('FORBIDDEN', null)
        .find('[data-testid="eligibility-error"]')
        .exists(),
    ).toBe(false)
  })
})
