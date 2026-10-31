<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WorkspaceLayout from '../components/WorkspaceLayout.vue'
import ActivityList from '../components/ActivityList.vue'
import UsersPanel from '../components/UsersPanel.vue'
import ResourceCalendar from '../components/ResourceCalendar.vue'
import ResourceManagePanel from '../components/ResourceManagePanel.vue'
import ChangePreviewPanel from './ChangePreviewPanel.vue'
import { navigation } from '../domain/access'
import { formatTime, statusLabels } from '../domain/format'
import type {
  Actor,
  ActivityView,
  CalendarEntry,
  ResourceView,
} from '../types/api'

// UI_FIXTURE_ONLY：只提供给开发原型路由；不导入API客户端，不写入会话。
const route = useRoute(),
  router = useRouter()
type Persona = 'applicant' | 'student' | 'approver' | 'resource' | 'accounts'
const initialPersona = [
  'applicant',
  'student',
  'approver',
  'resource',
  'accounts',
].includes(String(route.query.persona))
  ? (route.query.persona as Persona)
  : 'applicant'
const persona = ref<Persona>(initialPersona)
const profiles: Record<typeof persona.value, Actor> = {
  applicant: {
    id: 'preview-applicant',
    displayName: '示例申请人',
    organizationId: 'preview-org',
    enabled: true,
    identity: 'TEACHER',
    roles: ['APPLICANT'],
    permissions: [],
    approvalScopes: [],
    managedResourceIds: [],
  },
  student: {
    id: 'preview-student',
    displayName: '示例学生',
    organizationId: 'preview-org',
    enabled: true,
    identity: 'STUDENT',
    roles: ['APPLICANT'],
    permissions: [],
    approvalScopes: [],
    managedResourceIds: [],
  },
  approver: {
    id: 'preview-approver',
    displayName: '示例批准人',
    organizationId: 'preview-org',
    enabled: true,
    identity: 'TEACHER',
    roles: ['APPROVER'],
    permissions: [],
    approvalScopes: [
      {
        organizationId: 'preview-org',
        resourceCategories: ['VENUE', 'PROJECTOR'],
      },
    ],
    managedResourceIds: [],
  },
  resource: {
    id: 'preview-resource',
    displayName: '示例资源管理员',
    organizationId: 'preview-org',
    enabled: true,
    identity: 'TEACHER',
    roles: ['ADMIN'],
    permissions: ['RESOURCE_MANAGE', 'RESOURCE_CONFIRM'],
    approvalScopes: [],
    managedResourceIds: [
      'preview-venue-a',
      'preview-venue-b',
      'preview-equipment',
    ],
  },
  accounts: {
    id: 'preview-accounts',
    displayName: '示例账号管理员',
    organizationId: 'preview-org',
    enabled: true,
    identity: 'TEACHER',
    roles: ['ADMIN'],
    permissions: ['USER_MANAGE'],
    approvalScopes: [],
    managedResourceIds: [],
  },
}
const actor = computed(() => profiles[persona.value])
const page = computed(() =>
  typeof route.query.page === 'string' ? route.query.page : '/activities',
)
const link = (path: string) =>
  `/__prototype/workspace?page=${encodeURIComponent(path)}&persona=${persona.value}`
watch(persona, () => {
  status.value = ''
  scope.value = 'mine'
  void router.replace(link('/activities'))
})
const resources: ResourceView[] = [
  {
    id: 'preview-venue-a',
    name: '学术报告厅 A',
    kind: 'VENUE',
    category: 'VENUE',
    capacity: 80,
    quantity: 1,
    openFrom: '08:00',
    openTo: '22:00',
    allowTeacher: true,
    allowStudent: true,
    createdAt: '2030-10-01T00:00:00+08:00',
  },
  {
    id: 'preview-venue-b',
    name: '多功能厅 B',
    kind: 'VENUE',
    category: 'VENUE',
    capacity: 120,
    quantity: 1,
    openFrom: '08:00',
    openTo: '22:00',
    allowTeacher: true,
    allowStudent: true,
    createdAt: '2030-10-01T00:00:00+08:00',
  },
  {
    id: 'preview-equipment',
    name: '移动投影设备组 · 多场活动共享器材',
    kind: 'EQUIPMENT',
    category: 'PROJECTOR',
    capacity: null,
    quantity: 4,
    openFrom: '08:00',
    openTo: '22:00',
    allowTeacher: true,
    allowStudent: false,
    createdAt: '2030-10-01T00:00:00+08:00',
  },
]
const samples: ActivityView[] = [
  {
    id: 'preview-1',
    organizationId: 'preview-org',
    ownerId: 'preview-applicant',
    status: 'OPEN',
    versionNo: 1,
    effectiveVersionNo: 1,
    lockVersion: 4,
    registrationCount: 12,
    resourceRisk: 'NORMAL',
    activeChangeId: null,
    myRegistrationStatus: null,
    createdAt: '2030-10-01T00:00:00+08:00',
    input: {
      title: '校园技术分享会',
      description: '从一个有趣的项目出发，交流开发经验，认识志同道合的伙伴。',
      expectedAttendees: 60,
      registrationCapacity: 20,
      staffUserIds: [],
      plan: {
        startAt: '2030-10-20T10:00:00+08:00',
        endAt: '2030-10-20T11:00:00+08:00',
        setupMinutes: 30,
        teardownMinutes: 30,
        venueId: resources[0].id,
        equipment: [],
      },
    },
  },
  {
    id: 'preview-2',
    organizationId: 'preview-org',
    ownerId: 'preview-applicant',
    status: 'PENDING_APPROVER',
    versionNo: 1,
    effectiveVersionNo: null,
    lockVersion: 1,
    registrationCount: 0,
    resourceRisk: 'NORMAL',
    activeChangeId: null,
    myRegistrationStatus: null,
    createdAt: '2030-10-02T00:00:00+08:00',
    input: {
      title: '秋日摄影与校园漫步',
      description: '用镜头记录校园，交流摄影构图与光线运用。',
      expectedAttendees: 40,
      registrationCapacity: 30,
      staffUserIds: [],
      plan: {
        startAt: '2030-10-22T14:00:00+08:00',
        endAt: '2030-10-22T16:00:00+08:00',
        setupMinutes: 15,
        teardownMinutes: 15,
        venueId: resources[1].id,
        equipment: [],
      },
    },
  },
  {
    id: 'preview-3',
    organizationId: 'preview-org',
    ownerId: 'preview-applicant',
    status: 'SCHEDULED',
    versionNo: 2,
    effectiveVersionNo: 2,
    lockVersion: 5,
    registrationCount: 0,
    resourceRisk: 'AT_RISK',
    activeChangeId: null,
    myRegistrationStatus: null,
    createdAt: '2030-10-03T00:00:00+08:00',
    input: {
      title: '社团交流与新成员见面会',
      description: '分享社团计划，安排新学期的协作活动。',
      expectedAttendees: 50,
      registrationCapacity: 30,
      staffUserIds: [],
      plan: {
        startAt: '2030-10-20T14:30:00+08:00',
        endAt: '2030-10-20T16:30:00+08:00',
        setupMinutes: 0,
        teardownMinutes: 0,
        venueId: resources[0].id,
        equipment: [],
      },
    },
  },
  {
    id: 'preview-4',
    organizationId: 'preview-org',
    ownerId: 'preview-applicant',
    status: 'DRAFT',
    versionNo: 1,
    effectiveVersionNo: null,
    lockVersion: 0,
    registrationCount: 0,
    resourceRisk: 'NORMAL',
    activeChangeId: null,
    myRegistrationStatus: null,
    createdAt: '2030-10-04T00:00:00+08:00',
    input: {
      title: '设计思维工作坊',
      description: '从需求出发，一起完成一次设计练习。',
      expectedAttendees: 30,
      registrationCapacity: 20,
      staffUserIds: [],
      plan: {
        startAt: '2030-10-24T09:00:00+08:00',
        endAt: '2030-10-24T11:00:00+08:00',
        setupMinutes: 30,
        teardownMinutes: 30,
        venueId: resources[1].id,
        equipment: [],
      },
    },
  },
]
const scope = ref('mine'),
  status = ref('')
const items = computed(() =>
  samples.filter(
    (item) =>
      (!status.value || item.status === status.value) &&
      (page.value !== '/reviews' || item.status === 'PENDING_APPROVER') &&
      (scope.value !== 'public' || item.status === 'OPEN'),
  ),
)
const detail = computed(() =>
  samples.find((item) => page.value === `/activities/${item.id}`),
)
const entries: CalendarEntry[] = [samples[0], samples[2]].map((item) => ({
  resourceId: item.input.plan.venueId,
  startsAt: item.input.plan.startAt,
  endsAt: item.input.plan.endAt,
  quantity: 1,
  activityId: null,
  label: item.input.title,
  risk: item.resourceRisk,
  segment: 'EVENT',
}))
entries.push(
  {
    resourceId: 'preview-equipment',
    startsAt: '2030-10-20T10:00:00+08:00',
    endsAt: '2030-10-20T12:00:00+08:00',
    quantity: 2,
    activityId: null,
    label: '已占用',
    risk: 'NORMAL',
    segment: 'EVENT',
  },
  {
    resourceId: 'preview-equipment',
    startsAt: '2030-10-20T11:00:00+08:00',
    endsAt: '2030-10-20T13:00:00+08:00',
    quantity: 1,
    activityId: null,
    label: '已占用',
    risk: 'NORMAL',
    segment: 'EVENT',
  },
  {
    resourceId: 'preview-equipment',
    startsAt: '2030-10-20T11:00:00+08:00',
    endsAt: '2030-10-20T14:00:00+08:00',
    quantity: 2,
    activityId: null,
    label: '故障',
    risk: 'AT_RISK',
    segment: 'FAULT',
  },
)
const title = computed(
  () =>
    navigation.find((item) => item.path === page.value)?.label ??
    (page.value === '/activities/new' ? '创建活动' : '活动详情'),
)
</script>

<template>
  <WorkspaceLayout
    :actor="actor"
    :active-path="page"
    :map-link="link"
    account-action="离开预览"
    @account-action="router.push('/login')"
  >
    <section
      class="feedback warning preview-notice"
      data-testid="workspace-preview-banner"
    >
      <div>
        <strong>登录后界面预览 · 演示数据 / 未连接后端</strong>
        <p>展示真实工作台布局与示例内容，不代表已登录或已完成业务操作。</p>
      </div>
      <div class="preview-role">
        <label for="preview-role">查看身份与权限</label
        ><select id="preview-role" v-model="persona" data-testid="preview-role">
          <option value="applicant">教师 · 申请人</option>
          <option value="student">学生 · 申请人</option>
          <option value="approver">批准人</option>
          <option value="resource">资源管理员</option>
          <option value="accounts">账号管理员</option>
        </select>
      </div>
    </section>
    <template v-if="page === '/activities' || page === '/reviews'">
      <section class="page-heading row">
        <div>
          <span class="eyebrow"
            >协同工作 / {{ page === '/reviews' ? '待办' : '活动' }}</span
          >
          <h1>{{ title }}</h1>
          <p class="muted">查看活动安排、参与进度与资源状态。</p>
        </div>
        <RouterLink
          v-if="actor.roles.includes('APPLICANT')"
          class="primary-link"
          :to="link('/activities/new')"
          >＋ 创建活动</RouterLink
        >
      </section>
      <section class="panel">
        <div class="toolbar">
          <el-radio-group
            v-if="page === '/activities'"
            v-model="scope"
            aria-label="活动范围"
            ><el-radio-button value="mine">我的活动</el-radio-button
            ><el-radio-button value="public"
              >公开活动</el-radio-button
            ></el-radio-group
          ><el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            aria-label="活动状态"
            class="filter-select"
            ><el-option
              v-for="(label, value) in statusLabels"
              :key="value"
              :value="value"
              :label="label" /></el-select
          ><span class="caption">示例安排 · 2030年10月</span>
        </div>
        <ActivityList :items="items" :map-link="link" />
        <p v-if="!items.length" class="empty-state">当前筛选没有示例活动。</p>
      </section>
    </template>
    <template v-else-if="detail">
      <section class="page-heading">
        <RouterLink :to="link('/activities')">← 活动工作台</RouterLink>
        <h1>{{ detail.input.title }}</h1>
        <p class="muted">活动详情外观预览 · 所有业务操作已关闭</p>
      </section>
      <p v-if="detail.resourceRisk === 'AT_RISK'" class="feedback warning">
        ⚠ 当前资源受故障影响，请联系负责人调整资源。
      </p>
      <div class="detail-grid">
        <section class="panel">
          <div class="row">
            <h2>当前安排</h2>
            <span class="badge">{{ statusLabels[detail.status] }}</span>
          </div>
          <p>{{ detail.input.description }}</p>
          <dl class="summary">
            <dt>活动时间</dt>
            <dd>
              {{ formatTime(detail.input.plan.startAt) }} —
              {{ formatTime(detail.input.plan.endAt) }}
            </dd>
            <dt>场地</dt>
            <dd>
              {{
                resources.find((r) => r.id === detail?.input.plan.venueId)?.name
              }}
            </dd>
            <dt>布场 / 撤场</dt>
            <dd>
              {{ detail.input.plan.setupMinutes }} /
              {{ detail.input.plan.teardownMinutes }} 分钟
            </dd>
            <dt>有效版本</dt>
            <dd>{{ detail.effectiveVersionNo ?? '尚未生效' }}</dd>
          </dl>
        </section>
        <section class="panel">
          <h2>报名参与</h2>
          <p class="big-number">
            {{ detail.registrationCount }}
            <small>/ {{ detail.input.registrationCapacity }} 人</small>
          </p>
          <el-button type="primary" disabled>预览模式不可报名</el-button>
        </section>
      </div>
      <section class="panel">
        <h2>办理活动</h2>
        <p class="muted">草稿 → 待批准 → 待资源确认 → 已预约 → 开放报名</p>
        <div class="toolbar">
          <RouterLink :to="link('/activities/preview-3/change')"
            >查看改期工作台外观 →</RouterLink
          ><RouterLink :to="link('/changes/preview-change')"
            >查看改期进度外观 →</RouterLink
          >
        </div>
      </section>
    </template>
    <template v-else-if="page === '/resources/calendar'"
      ><section class="page-heading">
        <span class="eyebrow">协同工作 / 资源</span>
        <h1>资源日历</h1>
        <p class="muted">按场地查看活动占用，点击色块查看明细。</p>
      </section>
      <section class="panel">
        <ResourceCalendar
          :resources="resources"
          :entries="entries"
          :map-link="link"
          date="2030-10-20"
        /></section
    ></template>
    <template v-else-if="page === '/notifications'"
      ><section class="page-heading">
        <span class="eyebrow">协同工作 / 消息</span>
        <h1>通知中心</h1>
        <p class="muted">已读只表示看过消息，不代替人员确认或审批。</p>
      </section>
      <section class="panel">
        <article class="notification">
          <span class="badge">示例 · 未读</span>
          <h2>校园技术分享会已开放报名</h2>
          <p>活动将于10月20日10:00在学术报告厅 A 开始，请提前安排时间。</p>
          <RouterLink :to="link('/activities/preview-1')"
            >查看活动 →</RouterLink
          >
        </article>
        <article class="notification">
          <span class="badge">示例 · 资源提醒</span>
          <h2>社团交流会的资源状态发生变化</h2>
          <p>请查看当前资源风险，再决定是否需要调整安排。</p>
          <RouterLink :to="link('/activities/preview-3')"
            >查看活动 →</RouterLink
          >
        </article>
      </section></template
    >
    <template
      v-else-if="
        page === '/resources/manage' &&
        actor.permissions.includes('RESOURCE_MANAGE')
      "
    >
      <section class="page-heading">
        <h1>资源管理</h1>
        <p class="muted">资源、申请资格与故障记录外观预览。</p>
      </section>
      <ResourceManagePanel
        :actor="actor"
        :resources="resources"
        :faults="[
          {
            id: 'preview-fault',
            resourceId: 'preview-venue-a',
            startAt: '2030-10-20T14:00:00+08:00',
            endAt: '2030-10-20T18:00:00+08:00',
            unavailableQuantity: 1,
            reason: '场地设施检修（示例）',
            affectedActivities: [
              {
                activityId: 'preview-3',
                title: samples[2].input.title,
                versionNo: 2,
                resourceRisk: 'AT_RISK',
              },
            ],
          },
        ]"
        :map-link="link"
        initial-date="2030-10-20"
        preview
      />
    </template>
    <ChangePreviewPanel
      v-else-if="
        page === '/activities/preview-3/change' ||
        page === '/changes/preview-change'
      "
      :key="page"
      :actor="actor"
      :activity="samples[2]"
      :resources="resources"
      :detail="page === '/changes/preview-change'"
    />
    <UsersPanel
      v-else-if="
        page === '/admin/users' && actor.permissions.includes('USER_MANAGE')
      "
      :users="Object.values(profiles)"
      :organizations="[{ id: 'preview-org', name: '软件学院' }]"
      :resources="resources"
      :current-user-id="actor.id"
      :ready="true"
      preview
    />
    <template v-else
      ><section class="page-heading">
        <h1>{{ title }}</h1>
      </section>
      <section class="panel empty-state">
        <h2>
          {{
            page === '/activities/new'
              ? '创建活动需要连接真实服务'
              : '此操作页面正在接入'
          }}
        </h2>
        <p>当前预览不保存数据，也不执行账号或资源变更。</p>
        <RouterLink :to="link('/activities')">返回工作台预览</RouterLink>
      </section></template
    >
  </WorkspaceLayout>
</template>

<style scoped>
.preview-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 0;
}
.preview-notice p {
  margin-bottom: 0;
}
.preview-role {
  flex: none;
}
.preview-role label {
  margin: 0 0 6px;
  font-size: 12px;
}
.preview-role select {
  width: 180px;
  min-height: 40px;
  border: 1px solid #d6bd84;
  border-radius: 8px;
  padding: 8px;
  background: white;
  font: inherit;
  color: #172b4d;
}
@media (max-width: 767px) {
  .preview-notice {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .preview-role select {
    width: 100%;
  }
}
</style>
