<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, RefreshCw } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { getApiErrorMessage } from '@/services/http'
import { useSubmit } from '@/composables/useSubmit'
import { useNotificationStore } from '@/stores/notifications'
import * as tenants from '@/services/tenants'
import * as stores from '@/services/stores'
import * as users from '@/services/users'
import * as rbac from '@/services/rbac'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ domain: 'tenants' | 'stores' | 'users' | 'rbac' }>()
const notify = useNotificationStore()
const auth = useAuthStore()
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const error = ref('')
const showCreate = ref(false)
const form = reactive({ code: '', name: '', platformCode: '', platformAccount: '', username: '', password: '', roleIds: '' })
const meta = computed(() => ({
  tenants: { title: '租户管理', description: '创建和维护平台租户，查看当前启停状态。', columns: [['tenantCode', '租户编码'], ['tenantName', '租户名称'], ['status', '状态'], ['updatedAt', '更新时间']] },
  stores: { title: '店铺管理', description: '维护当前租户的跨境电商店铺资料。', columns: [['storeCode', '店铺编码'], ['storeName', '店铺名称'], ['platformCode', '平台'], ['status', '状态']] },
  users: { title: '用户管理', description: '维护租户成员及其角色绑定。', columns: [['username', '用户名'], ['displayName', '姓名'], ['status', '状态'], ['updatedAt', '更新时间']] },
  rbac: { title: '角色与权限', description: '查看当前租户角色及已绑定权限。', columns: [['roleCode', '角色编码'], ['roleName', '角色名称'], ['status', '状态'], ['permissionIds', '权限数量']] },
}[props.domain]))
const canCreate = computed(() => props.domain === 'tenants' ? auth.hasPermission('tenant:create') : props.domain === 'stores' ? auth.hasPermission('store:manage') : props.domain === 'users' ? auth.hasPermission('user:manage') : false)

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = props.domain === 'tenants' ? await tenants.listTenants() : props.domain === 'stores' ? await stores.listStores() : props.domain === 'users' ? await users.listUsers() : await rbac.listRoles()
    rows.value = (Array.isArray(data) ? data : data.items) as unknown as Record<string, unknown>[]
  } catch (cause) { error.value = getApiErrorMessage(cause, '列表加载失败。') } finally { loading.value = false }
}
const createSubmit = useSubmit()
function parseRoleIds(value: string) {
  const ids = value.split(',').map(item => item.trim()).filter(Boolean)
  if (!ids.length || ids.some(id => !/^\d+$/.test(id) || Number(id) < 1 || !Number.isSafeInteger(Number(id)))) return undefined
  return ids
}
async function createRecord() { await createSubmit.submit(async () => {
  if (props.domain === 'tenants') await tenants.createTenant({ tenantCode: form.code.trim(), tenantName: form.name.trim(), initialAdmin: { username: form.username.trim(), displayName: form.name.trim(), temporaryPassword: form.password } })
  if (props.domain === 'stores') await stores.createStore({ storeCode: form.code.trim(), storeName: form.name.trim(), platformCode: form.platformCode.trim(), platformAccount: form.platformAccount.trim() })
  if (props.domain === 'users') {
    const roleIds = parseRoleIds(form.roleIds)
    if (!form.name.trim() || !form.username.trim()) throw new Error('姓名和用户名不能为空。')
    if (form.password.length < 12) throw new Error('临时密码至少需要 12 个字符。')
    if (!roleIds) throw new Error('角色 ID 必须填写至少一个正整数，多个 ID 使用英文逗号分隔。')
    await users.createUser({ username: form.username.trim(), displayName: form.name.trim(), temporaryPassword: form.password, roleIds })
  }
  showCreate.value = false; Object.assign(form, { code: '', name: '', platformCode: '', platformAccount: '', username: '', password: '', roleIds: '' }); notify.push('创建成功', 'success'); await load()
}) }
function formatDate(value: unknown) {
  const date = typeof value === 'number' ? new Date(value) : typeof value === 'string' && /^\d+$/.test(value) ? new Date(Number(value)) : typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value) ? new Date(value) : undefined
  return date && !Number.isNaN(date.getTime()) ? date.toLocaleString('zh-CN') : '—'
}
function format(key: string, value: unknown) { if (key === 'updatedAt' || key === 'createdAt') return formatDate(value); if (Array.isArray(value)) return String(value.length); return typeof value === 'string' && value.trim() ? value : value == null ? '—' : String(value) }
onMounted(load)
</script>
<template>
  <section>
    <div class="page-heading"><div><span class="kicker">业务资料</span><h1>{{ meta.title }}</h1><p>{{ meta.description }}</p></div><div class="page-actions"><button class="btn btn--secondary" type="button" @click="load"><RefreshCw :size="16" />刷新</button><button v-if="canCreate" class="btn btn--primary" type="button" @click="showCreate = !showCreate"><Plus :size="16" />新建</button></div></div>
    <form v-if="showCreate" class="panel compact-form" @submit.prevent="createRecord">
      <label v-if="domain !== 'users'"><span>编码</span><input v-model="form.code" required /></label>
      <label><span>{{ domain === 'users' ? '姓名' : '名称' }}</span><input v-model="form.name" required /></label>
      <label v-if="domain === 'tenants' || domain === 'users'"><span>{{ domain === 'tenants' ? '管理员用户名' : '用户名' }}</span><input v-model="form.username" required /></label>
      <label v-if="domain === 'tenants' || domain === 'users'"><span>临时密码</span><input v-model="form.password" type="password" required autocomplete="new-password" /></label>
      <label v-if="domain === 'users'"><span>角色 ID（逗号分隔）</span><input v-model="form.roleIds" required /></label>
      <label v-if="domain === 'stores'"><span>平台编码</span><input v-model="form.platformCode" required /></label>
      <label v-if="domain === 'stores'"><span>平台账号</span><input v-model="form.platformAccount" required /></label>
      <button class="btn btn--primary" :disabled="createSubmit.submitting.value">{{ createSubmit.submitting.value ? '提交中…' : '确认创建' }}</button>
      <div v-if="createSubmit.errorMessage.value" class="alert alert--error" role="alert"><b v-if="createSubmit.errorCode.value">{{ createSubmit.errorCode.value }}：</b>{{ createSubmit.errorMessage.value }}</div>
    </form>
    <div class="panel table-panel"><DataState :loading="loading" :error="error" :empty="!rows.length"><div class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="[, label] in meta.columns" :key="label">{{ label }}</th></tr></thead><tbody><tr v-for="row in rows" :key="String(row.id)"><td v-for="[key] in meta.columns" :key="key">{{ format(key, row[key]) }}</td></tr></tbody></table></div></DataState></div>
  </section>
</template>
