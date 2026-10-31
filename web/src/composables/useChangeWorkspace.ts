import { computed, ref, shallowRef, watch } from 'vue'
import { api } from '../api'
import { ApiError } from '../api/client'
import type {
  Actor,
  ActivityView,
  ResourceView,
  Plan,
  ChangePreview,
  ChangeView,
} from '../types/api'
import { mayChange, planKey } from '../domain/change'
import { validatePlan } from '../domain/validation'
import { useMutation } from './useMutation'
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value))
export function useChangeWorkspace(
  id: string,
  actor: () => Actor | null,
  confirmed: (change: ChangeView) => void | Promise<void>,
) {
  const activity = shallowRef<ActivityView | null>(null),
    resources = shallowRef<ResourceView[]>([])
  const target = ref<Plan | null>(null),
    reason = ref(''),
    result = shallowRef<ChangePreview | null>(null)
  const loading = ref(false),
    previewing = ref(false),
    error = shallowRef<unknown>(null),
    previewError = shallowRef<unknown>(null),
    errors = ref<string[]>([]),
    truncated = ref(false)
  let generation = 0
  const proof = ref('')
  const fingerprint = () =>
    activity.value && target.value
      ? `${activity.value.versionNo}/${activity.value.lockVersion}/${planKey(target.value)}`
      : ''
  function invalidate() {
    generation++
    proof.value = ''
    result.value = null
  }
  watch(target, invalidate, { deep: true, flush: 'sync' })
  watch(actor, invalidate)
  const mutation = useMutation<ChangeView>({
    confirmed,
    conflict: () => load(true),
  })
  const allowed = computed(
    () => !!activity.value && mayChange(actor(), activity.value),
  )
  const locked = computed(
    () => loading.value || previewing.value || mutation.locked.value,
  )
  const ready = computed(
    () =>
      allowed.value &&
      !locked.value &&
      !error.value &&
      !!result.value?.check.feasible &&
      proof.value === fingerprint() &&
      !!proof.value &&
      !!reason.value.trim() &&
      reason.value.length <= 1000,
  )
  async function load(rethrow = false) {
    invalidate()
    loading.value = true
    error.value = null
    try {
      const [a, r] = await Promise.all([api.activity(id), api.resources()])
      activity.value = a.data
      resources.value = r.data
      truncated.value = !!r.truncated
      if (!target.value) target.value = copy(a.data.input.plan)
    } catch (e) {
      error.value = e
      if (rethrow) throw e
    } finally {
      loading.value = false
    }
  }
  async function preview() {
    if (
      locked.value ||
      !target.value ||
      !activity.value ||
      !mayChange(actor(), activity.value)
    )
      return
    errors.value = validatePlan(target.value)
    if (errors.value.length) return
    invalidate()
    const turn = generation,
      expected = fingerprint()
    const proposal = {
      baseVersionNo: activity.value.versionNo,
      targetPlan: copy(target.value),
    }
    previewing.value = true
    previewError.value = null
    try {
      const r = await api.changePreview(id, proposal)
      if (turn !== generation || expected !== fingerprint()) return
      if (planKey(r.data.impact.newPlan) !== planKey(proposal.targetPlan))
        throw new Error('预览返回的目标与当前输入不一致，请重新检查方案。')
      result.value = r.data
      proof.value = expected
    } catch (e) {
      if (turn === generation) {
        previewError.value = e
        if (e instanceof ApiError && e.status === 409) await load()
      }
    } finally {
      previewing.value = false
    }
  }
  async function select(index: number) {
    if (locked.value) return
    const candidate = result.value?.alternatives.items[index]
    if (!candidate) return
    target.value = copy(candidate.plan)
    // 选择只是填入目标；必须对最终目标再次执行API-26，不能沿用旧影响快照。
    await preview()
  }
  async function create() {
    if (!ready.value || !activity.value || !target.value) return
    if (!mayChange(actor(), activity.value)) {
      errors.value = ['活动当前不允许发起改期，请刷新状态']
      return
    }
    errors.value = validatePlan(target.value)
    if (errors.value.length) return
    await mutation.execute(
      api.createChange(id, {
        baseVersionNo: activity.value.versionNo,
        expectedLockVersion: activity.value.lockVersion,
        targetPlan: target.value,
        reason: reason.value,
      }),
    )
  }
  return {
    activity,
    resources,
    target,
    reason,
    result,
    loading,
    previewing,
    error,
    previewError,
    errors,
    truncated,
    mutation,
    allowed,
    locked,
    ready,
    load,
    preview,
    select,
    create,
    invalidate,
  }
}
