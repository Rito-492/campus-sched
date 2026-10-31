<script setup lang="ts">
import { computed } from 'vue'
import type {
  AuthorizationInput,
  Permission,
  ResourceView,
  Role,
} from '../types/api'
import {
  copyAuthorization,
  permissionLabels,
  roleLabels,
  withRoles,
  type OrganizationOption,
} from '../domain/authorization'
const props = defineProps<{
  modelValue: AuthorizationInput
  organizations: OrganizationOption[]
  resources: ResourceView[]
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: AuthorizationInput] }>()
const set = <K extends keyof AuthorizationInput>(
  field: K,
  value: AuthorizationInput[K],
) =>
  emit('update:modelValue', {
    ...copyAuthorization(props.modelValue),
    [field]: value,
  })
const roles = Object.keys(roleLabels) as Role[]
const permissions = Object.keys(permissionLabels) as Permission[]
const categories = computed(() =>
  [
    ...new Set([
      ...props.resources.map((r) => r.category),
      ...props.modelValue.approvalScopes.flatMap((s) => s.resourceCategories),
    ]),
  ].sort(),
)
const resourceOptions = computed(() => [
  ...props.resources.map((r) => ({ id: r.id, name: r.name })),
  ...props.modelValue.managedResourceIds
    .filter((id) => !props.resources.some((r) => r.id === id))
    .map((id) => ({ id, name: `已授权资源（当前列表未返回）：${id}` })),
])
const orgOptions = computed(() => [
  ...props.organizations,
  ...props.modelValue.approvalScopes
    .filter(
      (s) =>
        s.organizationId &&
        !props.organizations.some((o) => o.id === s.organizationId),
    )
    .map((s) => ({
      id: s.organizationId,
      name: `已授权组织（当前列表未返回）：${s.organizationId}`,
    })),
])
function updateScope(
  index: number,
  field: 'organizationId' | 'resourceCategories',
  value: string | string[],
) {
  const draft = copyAuthorization(props.modelValue)
  if (field === 'organizationId')
    draft.approvalScopes[index].organizationId = value as string
  else draft.approvalScopes[index].resourceCategories = value as string[]
  emit('update:modelValue', draft)
}
function removeScope(index: number) {
  const scopes = copyAuthorization(props.modelValue).approvalScopes
  scopes.splice(index, 1)
  set('approvalScopes', scopes)
}
</script>
<template>
  <section class="authorization-editor">
    <label for="authorization-identity">独立身份</label>
    <select
      id="authorization-identity"
      :value="modelValue.identity"
      :disabled="disabled"
      data-testid="authorization-identity"
      @change="
        set(
          'identity',
          ($event.target as HTMLSelectElement)
            .value as AuthorizationInput['identity'],
        )
      "
    >
      <option value="TEACHER">教师</option>
      <option value="STUDENT">学生</option>
    </select>
    <p class="caption">身份用于资源申请资格；教师身份不会自动获得审批权。</p>
    <fieldset :disabled="disabled" class="authorization-group">
      <legend>角色（可兼任）</legend>
      <label v-for="role in roles" :key="role" class="authorization-check"
        ><input
          type="checkbox"
          :checked="modelValue.roles.includes(role)"
          :data-testid="`role-${role}`"
          @change="
            emit(
              'update:modelValue',
              withRoles(
                modelValue,
                ($event.target as HTMLInputElement).checked
                  ? [...modelValue.roles, role]
                  : modelValue.roles.filter((r) => r !== role),
              ),
            )
          "
        />{{ roleLabels[role] }}</label
      >
    </fieldset>
    <p class="caption">
      移除管理员会清空管理权限及资源范围；移除批准人会清空审批范围。重新勾选不会自动恢复授权。
    </p>
    <fieldset
      v-if="modelValue.roles.includes('APPROVER')"
      :disabled="disabled"
      class="authorization-group"
    >
      <legend>批准范围：组织与资源类别</legend>
      <div
        v-for="(scope, index) in modelValue.approvalScopes"
        :key="index"
        class="scope-row"
      >
        <div>
          <label :for="`scope-org-${index}`">组织</label
          ><el-select
            :id="`scope-org-${index}`"
            :model-value="scope.organizationId"
            :disabled="disabled"
            placeholder="选择组织"
            @update:model-value="updateScope(index, 'organizationId', $event)"
            ><el-option
              v-for="org in orgOptions"
              :key="org.id"
              :label="org.name"
              :value="org.id"
          /></el-select>
        </div>
        <div>
          <label :for="`scope-categories-${index}`">允许审批的资源类别</label
          ><el-select
            :id="`scope-categories-${index}`"
            :model-value="scope.resourceCategories"
            multiple
            :disabled="disabled"
            placeholder="明确选择类别"
            @update:model-value="
              updateScope(index, 'resourceCategories', $event)
            "
            ><el-option
              v-for="category in categories"
              :key="category"
              :label="category"
              :value="category"
          /></el-select>
        </div>
        <el-button :disabled="disabled" @click="removeScope(index)"
          >移除此范围</el-button
        >
      </div>
      <el-button
        :disabled="disabled || !organizations.length"
        data-testid="scope-add"
        @click="
          set('approvalScopes', [
            ...modelValue.approvalScopes,
            { organizationId: '', resourceCategories: [] },
          ])
        "
        >添加组织范围</el-button
      >
      <p v-if="!modelValue.approvalScopes.length" class="caption">
        未分配范围时，没有可审批事项。一次审批必须覆盖活动组织和全部资源类别；所有者本人回避。
      </p>
    </fieldset>
    <fieldset
      v-if="modelValue.roles.includes('ADMIN')"
      :disabled="disabled"
      class="authorization-group"
    >
      <legend>管理员细分权限</legend>
      <label
        v-for="permission in permissions"
        :key="permission"
        class="authorization-check"
        ><input
          type="checkbox"
          :checked="modelValue.permissions.includes(permission)"
          :data-testid="`permission-${permission}`"
          @change="
            set(
              'permissions',
              ($event.target as HTMLInputElement).checked
                ? [...modelValue.permissions, permission]
                : modelValue.permissions.filter((p) => p !== permission),
            )
          "
        />{{ permissionLabels[permission] }}</label
      >
      <label for="managed-resources">明确管理的资源</label
      ><el-select
        id="managed-resources"
        :model-value="modelValue.managedResourceIds"
        :disabled="disabled"
        multiple
        filterable
        placeholder="无默认全资源范围"
        data-testid="managed-resources"
        @update:model-value="set('managedResourceIds', $event)"
        ><el-option
          v-for="resource in resourceOptions"
          :key="resource.id"
          :label="resource.name"
          :value="resource.id"
      /></el-select>
      <p class="caption">
        账号管理权限不包含资源管理或复核；资源职责仅在上述资源范围内生效。
      </p>
    </fieldset>
  </section>
</template>
<style scoped>
.authorization-group {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  margin: 20px 0;
}
.authorization-group legend {
  padding: 0 8px;
  font-weight: 600;
}
.authorization-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 8px 20px 8px 0;
  min-height: 40px;
}
.authorization-check input {
  accent-color: #176b68;
  width: 18px;
  height: 18px;
}
.authorization-editor > select {
  min-height: 40px;
  border: 1px solid #c8d0dc;
  border-radius: 8px;
  padding: 8px;
  width: 100%;
  font: inherit;
  background: white;
  color: #172b4d;
}
.authorization-editor > p {
  margin-top: 8px;
}
.scope-row {
  display: grid;
  grid-template-columns: 1fr 1.4fr auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 20px;
}
.scope-row label {
  margin-top: 0;
}
.authorization-group > p {
  margin: 12px 0 0;
}
@media (max-width: 767px) {
  .scope-row {
    grid-template-columns: 1fr;
  }
  .authorization-group {
    padding: 12px;
  }
}
</style>
