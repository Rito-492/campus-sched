<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { api } from '../api'
import { ApiError } from '../api/client'
import type {
  ActivityInput,
  ActivityView,
  ResourceView,
  StaffView,
} from '../types/api'
import { useSession } from '../stores/session'
import { shanghaiInput } from '../domain/format'
import { validateActivity } from '../domain/validation'
import ApiFeedback from '../components/ApiFeedback.vue'
const router = useRouter(),
  session = useSession(),
  step = ref(0),
  start = ref(''),
  end = ref('')
const input = reactive<ActivityInput>({
  title: '',
  description: '',
  expectedAttendees: 30,
  registrationCapacity: 20,
  staffUserIds: [],
  plan: {
    startAt: '',
    endAt: '',
    setupMinutes: 0,
    teardownMinutes: 0,
    venueId: '',
    equipment: [],
  },
})
const resources = ref<ResourceView[]>([]),
  staff = ref<StaffView[]>([]),
  loading = ref(false),
  truncated = ref(false),
  busy = ref(false),
  error = ref<unknown>(null),
  errors = ref<string[]>([])
let pending: ReturnType<typeof api.createActivity> | null = null
const uncertain = ref(false),
  completed = ref(false)
const venues = computed(() => resources.value.filter((r) => r.kind === 'VENUE'))
const equipment = computed(() =>
  resources.value.filter((r) => r.kind === 'EQUIPMENT'),
)
const eligible = (r: ResourceView) =>
  session.actor?.identity === 'TEACHER' ? r.allowTeacher : r.allowStudent
async function load() {
  loading.value = true
  error.value = null
  try {
    const [r, s] = await Promise.all([api.resources(), api.staff()])
    resources.value = r.data
    staff.value = s.data
    truncated.value = !!r.truncated || !!s.truncated
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}
async function save(retry = false) {
  if (busy.value || (uncertain.value && !retry)) return
  if (!retry) {
    input.plan.startAt = shanghaiInput(start.value)
    input.plan.endAt = shanghaiInput(end.value)
    errors.value = validateActivity(input)
    const selected = [
      input.plan.venueId,
      ...input.plan.equipment.map((e) => e.resourceId),
    ]
    for (const id of selected) {
      const r = resources.value.find((r) => r.id === id)
      if (!r || !eligible(r))
        errors.value.push(`${r?.name ?? '所选资源'}不允许当前身份申请`)
    }
    if (errors.value.length) return
    pending = api.createActivity(input)
  }
  if (!pending) return
  busy.value = true
  error.value = null
  try {
    const result: ActivityView = (await pending.run()).data
    completed.value = true
    await router.push(`/activities/${result.id}`)
  } catch (e) {
    error.value = e
    uncertain.value =
      !(e instanceof ApiError) || e.status === 0 || e.status >= 500
  } finally {
    busy.value = false
  }
}
onBeforeRouteLeave(
  () =>
    completed.value ||
    !uncertain.value ||
    window.confirm(
      '保存结果尚未确认。离开后请先查询活动列表，避免重复创建。确定离开？',
    ),
)
onMounted(load)
</script>
<template>
  <section class="page-heading">
    <span class="eyebrow">活动工作台 / 新建申请</span>
    <h1>让一个想法成为活动</h1>
    <p class="muted">
      先保存草稿，再检查资源并提交审批。保存草稿不会占用资源。
    </p>
  </section>
  <section class="panel">
    <el-steps :active="step" simple
      ><el-step title="基本信息" /><el-step title="时间资源" /><el-step
        title="工作人员" /><el-step title="检查保存"
    /></el-steps>
    <ApiFeedback
      :loading="loading"
      :error="error"
      :truncated="truncated"
      @retry="pending ? save(true) : load()"
    />
    <p v-if="uncertain" class="feedback warning">
      上次保存结果尚未确认，表单已锁定。请使用原请求重试；不要另建一份申请。
    </p>
    <form class="activity-form" @submit.prevent="save()">
      <fieldset :disabled="busy || uncertain">
        <div v-show="step === 0">
          <label for="title">活动名称</label
          ><el-input
            id="title"
            v-model="input.title"
            maxlength="100"
            show-word-limit
            data-testid="activity-title"
          /><label for="description">活动简介</label
          ><el-input
            id="description"
            v-model="input.description"
            type="textarea"
            :rows="4"
            maxlength="2000"
          />
          <div class="form-grid">
            <div>
              <label for="attendees">预计参加人数</label
              ><el-input-number
                id="attendees"
                v-model="input.expectedAttendees"
                :min="1"
              />
            </div>
            <div>
              <label for="capacity">报名容量</label
              ><el-input-number
                id="capacity"
                v-model="input.registrationCapacity"
                :min="1"
                :max="input.expectedAttendees"
              />
            </div>
          </div>
        </div>
        <div v-show="step === 1">
          <p class="muted">以下时间均为北京时间；活动30分钟～6小时。</p>
          <div class="form-grid">
            <div>
              <label for="start">开始时间</label
              ><input
                id="start"
                v-model="start"
                type="datetime-local"
                data-testid="activity-start"
              />
            </div>
            <div>
              <label for="end">结束时间</label
              ><input
                id="end"
                v-model="end"
                type="datetime-local"
                data-testid="activity-end"
              />
            </div>
            <div>
              <label for="setup">布场（分钟）</label
              ><el-input-number
                id="setup"
                v-model="input.plan.setupMinutes"
                :min="0"
                :max="120"
              />
            </div>
            <div>
              <label for="teardown">撤场（分钟）</label
              ><el-input-number
                id="teardown"
                v-model="input.plan.teardownMinutes"
                :min="0"
                :max="120"
              />
            </div>
          </div>
          <label for="venue">场地</label
          ><el-select
            id="venue"
            v-model="input.plan.venueId"
            placeholder="选择场地"
            data-testid="activity-venue"
            ><el-option
              v-for="r in venues"
              :key="r.id"
              :value="r.id"
              :label="`${r.name} · 容量${r.capacity}${eligible(r) ? '' : ' · 当前身份不可申请'}`"
              :disabled="!eligible(r)"
          /></el-select>
          <h3>设备需求</h3>
          <div
            v-for="(need, i) in input.plan.equipment"
            :key="i"
            class="toolbar"
          >
            <el-select v-model="need.resourceId" aria-label="设备"
              ><el-option
                v-for="r in equipment"
                :key="r.id"
                :value="r.id"
                :label="`${r.name}${eligible(r) ? '' : ' · 当前身份不可申请'}`"
                :disabled="!eligible(r)" /></el-select
            ><el-input-number
              v-model="need.quantity"
              :min="1"
              aria-label="设备数量"
            /><el-button @click="input.plan.equipment.splice(i, 1)"
              >移除</el-button
            >
          </div>
          <el-button
            @click="input.plan.equipment.push({ resourceId: '', quantity: 1 })"
            >添加设备</el-button
          >
        </div>
        <div v-show="step === 2">
          <h2>选择工作人员</h2>
          <p class="muted">
            工作人员在改期时确认本人安排；报名学生只接收通知。
          </p>
          <el-checkbox-group v-model="input.staffUserIds"
            ><el-checkbox
              v-for="person in staff.filter((s) => s.id !== session.actor?.id)"
              :key="person.id"
              :value="person.id"
              >{{ person.displayName }}</el-checkbox
            ></el-checkbox-group
          >
          <p v-if="!staff.length">暂无可选工作人员，请确认账号服务已连接。</p>
        </div>
        <div v-show="step === 3">
          <h2>确认草稿</h2>
          <dl class="summary">
            <dt>活动</dt>
            <dd>{{ input.title || '未填写' }}</dd>
            <dt>时间</dt>
            <dd>{{ start || '未填写' }} 至 {{ end || '未填写' }}</dd>
            <dt>人数</dt>
            <dd>
              预计 {{ input.expectedAttendees }} 人，报名容量
              {{ input.registrationCapacity }} 人
            </dd>
            <dt>工作人员</dt>
            <dd>{{ input.staffUserIds.length }} 人</dd>
          </dl>
          <p>保存成功后进入详情页，使用服务端生成的活动编号检查资源并提交。</p>
        </div>
        <ul v-if="errors.length" class="feedback error" role="alert">
          <li v-for="message in errors" :key="message">{{ message }}</li>
        </ul>
        <div class="form-actions">
          <el-button v-if="step > 0" @click="step--">上一步</el-button
          ><el-button v-if="step < 3" type="primary" @click="step++"
            >下一步</el-button
          ><el-button
            v-else
            type="primary"
            native-type="submit"
            :loading="busy"
            :disabled="loading || resources.length === 0"
            data-testid="activity-save"
            >保存草稿</el-button
          >
        </div>
      </fieldset>
    </form>
  </section>
</template>
