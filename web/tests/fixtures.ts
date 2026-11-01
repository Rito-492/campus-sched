import type { Actor, ActivityView, ResourceView } from '../src/types/api'
export function actor(overrides: Partial<Actor> = {}): Actor {
  return {
    id: 'user-1',
    displayName: '测试申请人',
    organizationId: 'org-1',
    enabled: true,
    identity: 'TEACHER',
    roles: ['APPLICANT'],
    permissions: [],
    approvalScopes: [],
    managedResourceIds: [],
    ...overrides,
  }
}
export function activity(overrides: Partial<ActivityView> = {}): ActivityView {
  return {
    id: 'activity-1',
    organizationId: 'org-1',
    ownerId: 'owner-1',
    status: 'OPEN',
    versionNo: 3,
    effectiveVersionNo: 2,
    lockVersion: 7,
    input: {
      title: '测试活动',
      description: '',
      expectedAttendees: 30,
      registrationCapacity: 20,
      staffUserIds: [],
      plan: {
        startAt: '2030-10-20T10:00:00+08:00',
        endAt: '2030-10-20T11:00:00+08:00',
        setupMinutes: 30,
        teardownMinutes: 30,
        venueId: 'venue-1',
        equipment: [{ resourceId: 'equipment-1', quantity: 2 }],
      },
    },
    registrationCount: 12,
    resourceRisk: 'NORMAL',
    activeChangeId: null,
    myRegistrationStatus: null,
    createdAt: '2030-10-01T00:00:00Z',
    ...overrides,
  }
}
export const resources: ResourceView[] = [
  {
    id: 'venue-1',
    kind: 'VENUE',
    name: '测试场地',
    category: 'VENUE',
    capacity: 80,
    quantity: 1,
    openFrom: '08:00',
    openTo: '22:00',
    allowTeacher: true,
    allowStudent: true,
    createdAt: '2030-10-01T00:00:00Z',
  },
  {
    id: 'equipment-1',
    kind: 'EQUIPMENT',
    name: '测试设备',
    category: 'PROJECTOR',
    capacity: null,
    quantity: 2,
    openFrom: '08:00',
    openTo: '22:00',
    allowTeacher: true,
    allowStudent: false,
    createdAt: '2030-10-01T00:00:00Z',
  },
]
