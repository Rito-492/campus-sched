<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { Actor, AuthorizationInput, ResourceView } from '../types/api'
import {
  authorizationSummary,
  copyAuthorization,
  emptyAuthorization,
  permissionLabels,
  roleLabels,
  validateAuthorization,
  validateUserCreate,
  type OrganizationOption,
  type UserOperation,
} from '../domain/authorization'
import AuthorizationEditor from './AuthorizationEditor.vue'
import ApiFeedback from './ApiFeedback.vue'
const props = defineProps<{
  users: Actor[]
  organizations: OrganizationOption[]
  resources: ResourceView[]
  currentUserId: string
  ready: boolean
  loading?: boolean
  busy?: boolean
  uncertain?: boolean
  truncated?: boolean
  loadError?: unknown
  actionError?: unknown
  message?: string
  completed?: number
  preview?: boolean
}>()
const emit = defineEmits<{
  reload: []
  submit: [operation: UserOperation]
  retry: []
}>()
const search = ref(''),
  enabledFilter = ref('all'),
  roleFilter = ref('')
const filtered = computed(() =>
  props.users.filter(
    (user) =>
      (!search.value ||
        `${user.displayName} ${user.id}`
          .toLowerCase()
          .includes(search.value.toLowerCase())) &&
      (enabledFilter.value === 'all' ||
        user.enabled === (enabledFilter.value === 'enabled')) &&
      (!roleFilter.value ||
        user.roles.some((role) => role === roleFilter.value)),
  ),
)
const editing = ref(false),
  target = ref<Actor | null>(null),
  authorization = ref<AuthorizationInput>(emptyAuthorization())
const account = reactive({
  username: '',
  displayName: '',
  password: '',
  organizationId: '',
})
const errors = ref<string[]>([]),
  confirmed = ref(false)
const locked = computed(() => !!props.busy || !!props.uncertain)
const organizationName = (id: string) =>
  props.organizations.find((o) => o.id === id)?.name ?? `组织 ${id}`
function open(user?: Actor) {
  if (locked.value || !props.ready) return
  target.value = user ? { ...user, ...copyAuthorization(user) } : null
  authorization.value = user ? copyAuthorization(user) : emptyAuthorization()
  Object.assign(account, {
    username: '',
    displayName: '',
    password: '',
    organizationId: '',
  })
  errors.value = []
  confirmed.value = false
  editing.value = true
}
function close() {
  if (locked.value) return
  editing.value = false
  account.password = ''
  errors.value = []
}
watch(
  () => props.completed,
  () => {
    editing.value = false
    account.password = ''
    confirmed.value = false
    errors.value = []
  },
)
watch(
  authorization,
  () => {
    confirmed.value = false
  },
  { deep: true },
)
function save() {
  if (props.preview || locked.value || !props.ready) return
  if (target.value) {
    errors.value = validateAuthorization(authorization.value)
    if (!confirmed.value) errors.value.push('请确认将完整替换当前账号的授权')
    if (!errors.value.length)
      emit('submit', {
        kind: 'authorization',
        userId: target.value.id,
        input: copyAuthorization(authorization.value),
      })
  } else {
    const input = {
      ...account,
      username: account.username.trim(),
      displayName: account.displayName.trim(),
      ...copyAuthorization(authorization.value),
    }
    errors.value = validateUserCreate(input)
    if (!errors.value.length) emit('submit', { kind: 'create', input })
  }
}
</script>
<template>
  <section class="page-heading row">
    <div>
      <span class="eyebrow">管理 / 账号与授权</span>
      <h1>账号与授权</h1>
      <p class="muted">
        分别配置身份、角色和职责范围，让每一次授权都清楚可查。
      </p>
    </div>
    <el-button
      type="primary"
      :disabled="!ready || locked"
      data-testid="user-create-open"
      @click="open()"
      >＋ 新建账号</el-button
    >
  </section>
  <p
    v-if="message"
    class="feedback success"
    role="status"
    data-testid="user-save-result"
  >
    {{ message }}
  </p>
  <p v-if="uncertain" class="feedback warning" role="status">
    上次变更结果尚未确认，编辑已锁定。请用原请求重试，不要重新创建或修改授权。
  </p>
  <ApiFeedback
    v-if="!editing"
    :error="actionError"
    @retry="uncertain ? emit('retry') : emit('reload')"
  />
  <section class="panel">
    <div class="toolbar">
      <el-input
        v-model="search"
        placeholder="搜索显示名或账号ID"
        aria-label="搜索账号"
        class="user-search"
        clearable
        data-testid="users-search"
      /><el-select
        v-model="enabledFilter"
        aria-label="账号状态"
        class="filter-select"
        ><el-option value="all" label="全部启停状态" /><el-option
          value="enabled"
          label="已启用" /><el-option
          value="disabled"
          label="已停用" /></el-select
      ><el-select
        v-model="roleFilter"
        clearable
        placeholder="全部角色"
        aria-label="账号角色"
        class="filter-select"
        ><el-option
          v-for="(label, value) in roleLabels"
          :key="value"
          :value="value"
          :label="label" /></el-select
      ><el-button
        v-if="!preview"
        :disabled="loading || locked"
        @click="emit('reload')"
        >刷新</el-button
      >
    </div>
    <p v-if="truncated" class="feedback warning">
      服务端列表最多返回200项，当前结果被截断。本地筛选仅覆盖已加载账号；编辑时不会自动删除未返回的既有组织或资源授权。
    </p>
    <ApiFeedback
      :error="loadError"
      :loading="loading"
      @retry="emit('reload')"
    />
    <p v-if="ready && !loading && !filtered.length" class="empty-state">
      当前筛选下没有账号。
    </p>
    <div v-if="!loading" class="users-list" data-testid="users-list">
      <article
        v-for="user in filtered"
        :key="user.id"
        class="user-row"
        :data-testid="`user-${user.id}`"
      >
        <div class="user-main">
          <div class="toolbar">
            <h2>{{ user.displayName }}</h2>
            <span class="badge" :class="{ 'user-disabled': !user.enabled }">{{
              user.enabled ? '已启用' : '已停用'
            }}</span
            ><small v-if="user.id === currentUserId">当前账号</small>
          </div>
          <p>
            {{ organizationName(user.organizationId) }} ·
            {{ user.identity === 'TEACHER' ? '教师' : '学生' }}
          </p>
          <p class="caption user-id">账号ID：{{ user.id }}</p>
          <div class="user-tags">
            <span v-for="role in user.roles" :key="role" class="badge">{{
              roleLabels[role]
            }}</span
            ><span v-if="!user.roles.length" class="muted">未分配角色</span>
          </div>
        </div>
        <div class="user-scopes">
          <p>
            {{
              user.permissions.map((p) => permissionLabels[p]).join('、') ||
              '无管理员权限'
            }}
          </p>
          <p class="caption">
            审批组织 {{ user.approvalScopes.length }} 项 · 管理资源
            {{ user.managedResourceIds.length }} 项
          </p>
        </div>
        <div class="user-actions">
          <el-button
            :disabled="!ready || locked"
            :data-testid="`user-authorize-${user.id}`"
            @click="open(user)"
            >编辑授权</el-button
          ><el-popconfirm
            :title="
              user.enabled
                ? '停用后，该账号的旧会话立即失效。确定停用？'
                : '确定启用该账号？'
            "
            confirm-button-text="确认"
            cancel-button-text="取消"
            @confirm="
              emit('submit', {
                kind: 'enabled',
                userId: user.id,
                enabled: !user.enabled,
              })
            "
            ><template #reference
              ><el-button
                :disabled="preview || !ready || locked"
                :type="user.enabled ? 'danger' : 'primary'"
                plain
                :data-testid="`user-toggle-${user.id}`"
                >{{ user.enabled ? '停用' : '启用' }}</el-button
              ></template
            ></el-popconfirm
          >
        </div>
      </article>
    </div>
    <p class="caption">账号以显示名和唯一编号识别。用户名在新建时设置。</p>
  </section>
  <el-dialog
    class="user-editor-dialog"
    :model-value="editing"
    :title="target ? `编辑授权 · ${target.displayName}` : '新建账号'"
    width="min(780px, calc(100vw - 24px))"
    :close-on-click-modal="false"
    :close-on-press-escape="!locked"
    :show-close="!locked"
    @update:model-value="
      (value: boolean) => {
        if (!value) close()
      }
    "
  >
    <form data-testid="user-editor" @submit.prevent="save">
      <p v-if="preview" class="feedback warning">
        演示数据 / 未连接后端。可调整表单查看效果，保存与启停操作已关闭。
      </p>
      <ApiFeedback
        :error="actionError"
        @retry="uncertain ? emit('retry') : save()"
      />
      <p v-if="uncertain" class="feedback warning">
        结果尚未确认；重试使用原请求体和原幂等键，当前表单暂不可改动。
      </p>
      <div v-if="!target" class="form-grid">
        <div>
          <label for="user-username">用户名</label
          ><el-input
            id="user-username"
            v-model="account.username"
            :disabled="locked"
            autocomplete="off"
            data-testid="user-username"
          />
        </div>
        <div>
          <label for="user-display-name">显示名</label
          ><el-input
            id="user-display-name"
            v-model="account.displayName"
            :disabled="locked"
            data-testid="user-display-name"
          />
        </div>
        <div>
          <label for="user-password">初始密码</label
          ><el-input
            id="user-password"
            v-model="account.password"
            :disabled="locked"
            type="password"
            show-password
            autocomplete="new-password"
            data-testid="user-password"
          />
          <p class="caption">至少8位，最多72个UTF-8字节。</p>
        </div>
        <div>
          <label for="user-organization">所属组织</label
          ><el-select
            id="user-organization"
            v-model="account.organizationId"
            :disabled="locked"
            placeholder="选择组织"
            data-testid="user-organization"
            ><el-option
              v-for="org in organizations"
              :key="org.id"
              :value="org.id"
              :label="org.name"
          /></el-select>
        </div>
      </div>
      <p v-else class="muted">
        {{ organizationName(target.organizationId) }} ·
        {{ target.enabled ? '已启用' : '已停用' }}。此表单仅调整身份与授权。
      </p>
      <AuthorizationEditor
        v-model="authorization"
        :organizations="organizations"
        :resources="resources"
        :disabled="locked"
      />
      <template v-if="target"
        ><section class="authorization-summary">
          <h3>保存后的完整授权</h3>
          <p data-testid="authorization-summary">
            {{ authorizationSummary(authorization) }}
          </p>
          <p class="caption">
            本次保存将完整替换当前授权。身份及资源资格按最新设置参与后续校验，已生效预约不会因此自动删除。
          </p>
          <p v-if="target.id === currentUserId" class="risk-text">
            正在编辑本人账号。移除账号管理权限后，你将失去此页面的操作权限。
          </p>
          <label class="replace-confirm"
            ><input
              v-model="confirmed"
              type="checkbox"
              :disabled="locked"
              data-testid="authorization-confirm"
            />我已核对上述身份、角色、权限及范围</label
          >
        </section></template
      >
      <ul v-if="errors.length" class="feedback error" role="alert">
        <li v-for="error in errors" :key="error">{{ error }}</li>
      </ul>
      <div class="form-actions">
        <el-button :disabled="locked" @click="close">取消</el-button
        ><el-button
          type="primary"
          native-type="submit"
          :loading="busy"
          :disabled="preview || !ready || uncertain"
          data-testid="user-save"
          >{{ target ? '保存完整授权' : '创建账号' }}</el-button
        >
      </div>
    </form>
  </el-dialog>
</template>
<style scoped>
:global(.user-editor-dialog .el-dialog__body) {
  max-height: calc(100dvh - 220px);
  overflow-y: auto;
}
.user-search {
  width: 250px;
}
.user-row {
  display: flex;
  gap: 24px;
  align-items: center;
  border-top: 1px solid #e2e8f0;
  padding: 24px 0;
}
.user-main {
  flex: 1;
  min-width: 0;
}
.user-main .toolbar {
  gap: 8px;
  margin-bottom: 8px;
}
.user-main h2,
.user-main p {
  margin: 0;
}
.user-id {
  overflow-wrap: anywhere;
}
.user-tags {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.user-scopes {
  max-width: 260px;
}
.user-scopes p {
  margin: 0 0 8px;
}
.user-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.user-actions .el-button {
  margin-left: 0;
}
.user-disabled {
  color: #5e6c84;
  background: #f1f3f6;
  border-color: #dce2e9;
}
.authorization-summary {
  padding: 16px;
  background: #f3faf7;
  border: 1px solid #c8dfd7;
  border-radius: 8px;
}
.replace-confirm {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 40px;
}
.replace-confirm input {
  accent-color: #176b68;
  width: 18px;
  height: 18px;
}
@media (max-width: 767px) {
  .user-row {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .user-search {
    width: 100%;
  }
  .user-scopes {
    max-width: none;
  }
  .user-actions {
    justify-content: flex-end;
  }
}
</style>
