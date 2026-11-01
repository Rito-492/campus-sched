import { describe, expect, it } from 'vitest'
import {
  validateResource,
  validateFault,
  mayManageResource,
} from '../src/domain/resource'
import { actor, resources } from './fixtures'
describe('资源管理边界', () => {
  it('四种资格组合均合法，不推导隐式身份权利', () => {
    for (const allowTeacher of [false, true])
      for (const allowStudent of [false, true])
        expect(
          validateResource({ ...resources[0], allowTeacher, allowStudent }),
        ).toEqual([])
  })
  it('设备/场地类型不互相借用容量和数量规则', () => {
    expect(validateResource({ ...resources[0], quantity: 2 })).not.toEqual([])
    expect(validateResource({ ...resources[1], capacity: 50 })).not.toEqual([])
    expect(
      validateResource({ ...resources[1], openFrom: '22:00', openTo: '08:00' }),
    ).not.toEqual([])
  })
  it('故障不能零长度、超库存或无原因', () => {
    expect(
      validateFault(
        {
          startAt: '2030-10-20T10:00:00Z',
          endAt: '2030-10-20T10:00:00Z',
          unavailableQuantity: 3,
          reason: ' ',
        },
        resources[1],
      ),
    ).toHaveLength(3)
  })
  it('账号管理或资源复核不能修改资源资格', () => {
    expect(
      mayManageResource(
        actor({
          roles: ['ADMIN'],
          permissions: ['USER_MANAGE', 'RESOURCE_CONFIRM'],
          managedResourceIds: ['venue-1'],
        }),
        'venue-1',
      ),
    ).toBe(false)
    expect(
      mayManageResource(
        actor({
          roles: ['ADMIN'],
          permissions: ['RESOURCE_MANAGE'],
          managedResourceIds: ['venue-1'],
        }),
        'venue-1',
      ),
    ).toBe(true)
  })
})
