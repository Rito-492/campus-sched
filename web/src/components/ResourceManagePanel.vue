<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type {
  Actor,
  ResourceView,
  ResourceCreate,
  ResourceEligibility,
  FaultInput,
  FaultView,
} from '../types/api'
import {
  mayManageResource,
  validateResource,
  validateFault,
} from '../domain/resource'
import { hasPermission } from '../domain/access'
import { formatTime, shanghaiInput, shanghaiLocal } from '../domain/format'
const props = withDefaults(
  defineProps<{
    actor: Actor | null
    resources: ResourceView[]
    faults: FaultView[]
    disabled?: boolean
    preview?: boolean
    revision?: number
    initialDate?: string
    mapLink?: (path: string) => string
  }>(),
  { revision: 0 },
)
const emit = defineEmits<{
  create: [data: ResourceCreate]
  eligibility: [id: string, data: ResourceEligibility]
  fault: [id: string, data: FaultInput]
  query: [id: string, from: string, to: string]
}>()
const selectedId = ref(''),
  filter = ref(''),
  createOpen = ref(false),
  date = ref(
    props.initialDate ?? shanghaiLocal(new Date().toISOString()).slice(0, 10),
  )
const selected = computed(() =>
  props.resources.find((r) => r.id === selectedId.value),
)
const allowed = computed(
  () => !!selected.value && mayManageResource(props.actor, selected.value.id),
)
const eligibility = ref<ResourceEligibility>({
  allowTeacher: false,
  allowStudent: false,
})
const blank = (): ResourceCreate => ({
  kind: 'VENUE',
  name: '',
  category: 'VENUE',
  capacity: 30,
  quantity: 1,
  openFrom: '08:00',
  openTo: '22:00',
  allowTeacher: false,
  allowStudent: false,
})
const resource = ref(blank()),
  start = ref(''),
  end = ref(''),
  quantity = ref(1),
  reason = ref(''),
  errors = ref<string[]>([])
const visible = computed(() =>
  props.resources.filter((r) =>
    `${r.name} ${r.category}`
      .toLowerCase()
      .includes(filter.value.toLowerCase()),
  ),
)
watch(
  () => resource.value.kind,
  (kind) => {
    resource.value.category = kind === 'VENUE' ? 'VENUE' : ''
    resource.value.capacity = kind === 'VENUE' ? 30 : null
    resource.value.quantity = 1
  },
)
watch([selectedId, () => props.revision], () => {
  const r = selected.value
  if (r)
    eligibility.value = {
      allowTeacher: r.allowTeacher,
      allowStudent: r.allowStudent,
    }
  errors.value = []
})
watch(
  () => props.resources,
  (rs) => {
    if (!rs.some((r) => r.id === selectedId.value))
      selectedId.value =
        rs.find((r) => mayManageResource(props.actor, r.id))?.id ??
        rs[0]?.id ??
        ''
  },
  { immediate: true },
)
watch(
  () => props.revision,
  () => {
    createOpen.value = false
    resource.value = blank()
    start.value = ''
    end.value = ''
    reason.value = ''
    errors.value = []
  },
)
watch(
  [selectedId, date, allowed],
  () => {
    if (!allowed.value || !date.value) return
    const from = `${date.value}T00:00:00+08:00`
    emit(
      'query',
      selectedId.value,
      from,
      new Date(Date.parse(from) + 86400000).toISOString(),
    )
  },
  { immediate: true },
)
function create() {
  errors.value = validateResource(resource.value)
  if (!errors.value.length && !props.preview && !props.disabled)
    emit('create', { ...resource.value })
}
function saveEligibility() {
  if (selected.value && allowed.value && !props.preview && !props.disabled)
    emit('eligibility', selected.value.id, { ...eligibility.value })
}
function fault() {
  if (!selected.value || !allowed.value || props.preview || props.disabled)
    return
  const value: FaultInput = {
    startAt: shanghaiInput(start.value),
    endAt: shanghaiInput(end.value),
    unavailableQuantity: quantity.value,
    reason: reason.value,
  }
  errors.value = validateFault(value, selected.value)
  if (!errors.value.length) emit('fault', selected.value.id, value)
}
</script>
<template>
  <section class="panel" data-testid="resource-management">
    <div class="toolbar">
      <el-input
        v-model="filter"
        aria-label="查找资源"
        placeholder="查找资源名称或类别"
        class="filter-select"
      /><el-button
        v-if="hasPermission(actor, 'RESOURCE_MANAGE')"
        :disabled="disabled"
        data-testid="resource-new"
        @click="createOpen = !createOpen"
        >{{ createOpen ? '收起新增' : '新增资源' }}</el-button
      >
    </div>
    <p v-if="preview" class="feedback warning">
      演示数据 / 未连接后端。可查看表单，保存和故障登记不可用。
    </p>
    <p class="caption">
      仅可修改明确分配给你的资源；新建成功的资源会加入你的管理范围。
    </p>
    <el-form
      v-if="createOpen"
      label-position="top"
      :disabled="disabled"
      class="resource-create"
    >
      <h2>新增资源</h2>
      <div class="form-grid">
        <el-form-item label="资源类型"
          ><el-select v-model="resource.kind" aria-label="资源类型"
            ><el-option label="场地" value="VENUE" /><el-option
              label="设备"
              value="EQUIPMENT" /></el-select
        ></el-form-item>
        <el-form-item label="资源名称"
          ><el-input v-model="resource.name" data-testid="resource-name"
        /></el-form-item>
        <el-form-item label="类别"
          ><el-input
            v-model="resource.category"
            :disabled="resource.kind === 'VENUE'"
        /></el-form-item>
        <el-form-item v-if="resource.kind === 'VENUE'" label="容纳人数"
          ><el-input-number
            :model-value="resource.capacity ?? 1"
            :min="1"
            @update:model-value="resource.capacity = Number($event)"
        /></el-form-item>
        <el-form-item v-else label="库存数量"
          ><el-input-number v-model="resource.quantity" :min="1"
        /></el-form-item>
        <el-form-item label="开放开始"
          ><input
            v-model="resource.openFrom"
            type="time"
            aria-label="开放开始"
            :disabled="disabled"
        /></el-form-item>
        <el-form-item label="开放结束"
          ><input
            v-model="resource.openTo"
            type="time"
            aria-label="开放结束"
            :disabled="disabled"
        /></el-form-item>
      </div>
      <div class="toolbar">
        <el-checkbox v-model="resource.allowTeacher">允许教师申请</el-checkbox
        ><el-checkbox v-model="resource.allowStudent">允许学生申请</el-checkbox>
      </div>
      <p class="caption">
        可以同时关闭两项申请资格；教师身份不会自动获得审批权限。
      </p>
      <el-button
        type="primary"
        :disabled="preview"
        data-testid="resource-create"
        @click="create"
        >创建资源</el-button
      >
    </el-form>
    <div class="resource-workspace">
      <div class="resource-picker" role="group" aria-label="选择管理资源">
        <p v-if="!visible.length" class="empty-state">没有匹配资源。</p>
        <button
          v-for="r in visible"
          :key="r.id"
          class="resource-choice"
          :class="{ selected: selectedId === r.id }"
          :disabled="disabled"
          :aria-pressed="selectedId === r.id"
          :data-testid="`resource-select-${r.id}`"
          @click="selectedId = r.id"
        >
          <strong>{{ r.name }}</strong
          ><span>{{
            r.kind === 'VENUE'
              ? `场地 · ${r.capacity}人`
              : `设备 · 库存${r.quantity}`
          }}</span
          ><small>{{
            mayManageResource(actor, r.id) ? '可管理' : '只读 · 不在管理范围'
          }}</small>
        </button>
      </div>
      <div v-if="selected" class="resource-content">
        <h2>{{ selected.name }}</h2>
        <p class="muted">
          {{ selected.category }} · 开放 {{ selected.openFrom }}—{{
            selected.openTo
          }}
        </p>
        <h3>申请资格</h3>
        <p>
          已生效的预约继续保留；尚未确认的申请及改期目标按最新资格重新检查。
        </p>
        <div class="toolbar">
          <el-checkbox
            v-model="eligibility.allowTeacher"
            :disabled="disabled || !allowed"
            data-testid="eligibility-teacher"
            >允许教师申请</el-checkbox
          ><el-checkbox
            v-model="eligibility.allowStudent"
            :disabled="disabled || !allowed"
            data-testid="eligibility-student"
            >允许学生申请</el-checkbox
          >
        </div>
        <el-popconfirm
          title="按此设置更新申请资格？已生效预约会保留。"
          @confirm="saveEligibility"
          ><template #reference
            ><el-button
              :disabled="disabled || preview || !allowed"
              data-testid="eligibility-save"
              >保存申请资格</el-button
            ></template
          ></el-popconfirm
        >
        <template v-if="allowed">
          <h3>登记故障</h3>
          <p class="caption">
            故障减少可用库存，原预约不会被删除；受影响活动将标记资源风险。
          </p>
          <el-form label-position="top" :disabled="disabled">
            <div class="form-grid">
              <el-form-item label="故障开始（北京时间）"
                ><input
                  v-model="start"
                  type="datetime-local"
                  :disabled="disabled"
                  aria-label="故障开始" /></el-form-item
              ><el-form-item label="故障结束（北京时间）"
                ><input
                  v-model="end"
                  type="datetime-local"
                  :disabled="disabled"
                  aria-label="故障结束"
              /></el-form-item>
            </div>
            <el-form-item label="不可用数量"
              ><el-input-number
                v-model="quantity"
                :min="1"
                :max="selected.quantity"
            /></el-form-item>
            <el-form-item label="故障原因"
              ><el-input
                v-model="reason"
                type="textarea"
                maxlength="1000"
                data-testid="fault-reason"
            /></el-form-item>
            <el-popconfirm
              title="确认登记故障？此操作将影响相关活动的资源风险。"
              @confirm="fault"
              ><template #reference
                ><el-button
                  :disabled="preview || !reason.trim()"
                  data-testid="fault-create"
                  >登记故障</el-button
                ></template
              ></el-popconfirm
            >
          </el-form>
          <h3>故障记录与受影响活动</h3>
          <label for="fault-date">查询日期</label
          ><input
            id="fault-date"
            v-model="date"
            type="date"
            :disabled="disabled"
          />
          <p
            v-if="!faults.some((f) => f.resourceId === selected?.id)"
            class="muted"
          >
            当前没有已加载的故障记录，请留意查询状态。
          </p>
          <article
            v-for="f in faults.filter((f) => f.resourceId === selected?.id)"
            :key="f.id"
            class="notification"
            data-testid="fault-item"
          >
            <h4>{{ f.reason }}</h4>
            <p>
              {{ formatTime(f.startAt) }} — {{ formatTime(f.endAt) }} · 不可用
              {{ f.unavailableQuantity }}
            </p>
            <p v-if="!f.affectedActivities.length">
              没有当前权限可见的受影响活动。
            </p>
            <p v-for="a in f.affectedActivities" :key="a.activityId">
              <RouterLink
                :to="
                  mapLink
                    ? mapLink(`/activities/${a.activityId}`)
                    : `/activities/${a.activityId}`
                "
                >{{ a.title }} · 版本 {{ a.versionNo }}</RouterLink
              >
              ·
              {{
                a.resourceRisk === 'AT_RISK'
                  ? '资源有风险'
                  : '本次查询未标记风险'
              }}
            </p>
          </article>
        </template>
        <p v-else class="feedback warning">
          当前账号不在此资源管理范围，不能修改资格、登记或查询故障。
        </p>
      </div>
    </div>
    <ul v-if="errors.length" class="feedback error" role="alert">
      <li v-for="e in errors" :key="e">{{ e }}</li>
    </ul>
  </section>
</template>
