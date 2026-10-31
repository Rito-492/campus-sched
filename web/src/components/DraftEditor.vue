<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api } from '../api'
import type {
  ActivityInput,
  ActivityView,
  ResourceView,
  Actor,
  StaffView,
  Plan,
} from '../types/api'
import { validateActivity } from '../domain/validation'
import { useMutation } from '../composables/useMutation'
import PlanEditor from './PlanEditor.vue'
import ApiFeedback from './ApiFeedback.vue'
import MutationFeedback from './MutationFeedback.vue'
const props = defineProps<{
  activity: ActivityView
  resources: ResourceView[]
  actor: Actor
  disabled?: boolean
  refresh: () => Promise<void>
  initialPlan?: Plan
}>()
const emit = defineEmits<{
  saved: [activity: ActivityView]
  locked: [value: boolean]
}>()
const input = ref<ActivityInput>(
  JSON.parse(JSON.stringify(props.activity.input)),
)
if (props.initialPlan)
  input.value.plan = JSON.parse(JSON.stringify(props.initialPlan))
const staff = ref<StaffView[]>([]),
  error = ref<unknown>(null),
  truncated = ref(false),
  errors = ref<string[]>([])
const mutation = useMutation<ActivityView>({
  confirmed: (data) => emit('saved', data),
  conflict: () => props.refresh(),
})
async function refresh() {
  try {
    await props.refresh()
    mutation.refreshError.value = null
  } catch (e) {
    mutation.refreshError.value = e
  }
}
watch(mutation.locked, (value) => emit('locked', value))
async function loadStaff() {
  try {
    const r = await api.staff()
    staff.value = r.data
    truncated.value = !!r.truncated
    error.value = null
  } catch (e) {
    error.value = e
  }
}
function save() {
  if (props.disabled || mutation.locked.value) return
  errors.value = validateActivity(input.value)
  if (props.activity.status !== 'DRAFT')
    errors.value.push('活动已不在草稿状态，请查看最新详情。')
  if (!errors.value.length)
    void mutation.execute(
      api.editActivity(props.activity.id, {
        input: input.value,
        expectedLockVersion: props.activity.lockVersion,
      }),
    )
}
onBeforeRouteLeave(
  (to) =>
    to.path === '/login' ||
    !mutation.uncertain.value ||
    window.confirm('草稿保存结果尚未确认。离开后请先查询最新状态。确定离开？'),
)
onMounted(loadStaff)
</script>
<template>
  <section class="panel" data-testid="draft-editor">
    <h2>编辑活动草稿</h2>
    <p class="muted">
      发生版本冲突时保留你的输入，刷新版本后核对再保存。当前版本
      {{ activity.versionNo }}。
    </p>
    <MutationFeedback
      :mutation="mutation"
      :resources="resources"
      @refresh="refresh"
    />
    <ApiFeedback :error="error" :truncated="truncated" @retry="loadStaff" />
    <el-form label-position="top" :disabled="disabled || mutation.locked.value">
      <el-form-item label="活动名称"
        ><el-input
          v-model="input.title"
          maxlength="100"
          data-testid="draft-title"
      /></el-form-item>
      <el-form-item label="活动简介"
        ><el-input
          v-model="input.description"
          type="textarea"
          maxlength="2000"
          :rows="3"
      /></el-form-item>
      <div class="form-grid">
        <el-form-item label="预计人数"
          ><el-input-number
            v-model="input.expectedAttendees"
            :min="1" /></el-form-item
        ><el-form-item label="报名容量"
          ><el-input-number
            v-model="input.registrationCapacity"
            :min="1"
            :max="input.expectedAttendees"
        /></el-form-item>
      </div>
      <el-form-item label="工作人员"
        ><el-select v-model="input.staffUserIds" multiple aria-label="工作人员"
          ><el-option
            v-for="s in staff.filter((s) => s.enabled && s.id !== actor.id)"
            :key="s.id"
            :value="s.id"
            :label="s.displayName" /><el-option
            v-for="id in input.staffUserIds.filter(
              (id) => !staff.some((s) => s.id === id),
            )"
            :key="id"
            :value="id"
            :label="`${id}（未返回，保留原选择）`" /></el-select
      ></el-form-item>
    </el-form>
    <PlanEditor
      v-model="input.plan"
      :resources="resources"
      :identity="actor.identity"
      :disabled="disabled || mutation.locked.value"
      prefix="draft"
    />
    <ul v-if="errors.length" class="feedback error" role="alert">
      <li v-for="e in errors" :key="e">{{ e }}</li>
    </ul>
    <div class="form-actions">
      <el-button
        type="primary"
        :disabled="
          disabled || mutation.locked.value || activity.status !== 'DRAFT'
        "
        data-testid="draft-save"
        @click="save"
        >保存修改</el-button
      >
    </div>
  </section>
</template>
