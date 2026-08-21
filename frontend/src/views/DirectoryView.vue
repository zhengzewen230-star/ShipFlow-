<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, RefreshCw, Pencil, Power } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import ActionError from '@/components/ActionError.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useSubmit } from '@/composables/useSubmit'
import { useNotificationStore } from '@/stores/notifications'
import * as tenants from '@/services/tenants'
import * as stores from '@/services/stores'
import * as users from '@/services/users'
import * as rbac from '@/services/rbac'
import { useAuthStore } from '@/stores/auth'
import { defaultStoreFilter, parseStoreQuery, toStoreQuery, type StoreFilterState } from './storeQuery'
import { storeListBackQuery } from './storeDetail'

const props = defineProps<{ domain: 'tenants' | 'stores' | 'users' | 'rbac' }>()
const notify = useNotificationStore()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const rows = ref<Record<string, unknown>[]>([])
const storeRows = ref<stores.Store[]>([])
const storeTotal = ref(0)
const storeTotalPages = ref(0)
const storeFilter = reactive<StoreFilterState>({ ...defaultStoreFilter })
const availableRoles = ref<rbac.Role[]>([])
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const showCreate = ref(false)
const showEdit = ref(false)
const editingStore = ref<stores.Store>()
const form = reactive({ code: '', name: '', platformCode: '', platformAccount: '', username: '', password: '', roleId: '' })
const editForm = reactive({ name: '', platformCode: '', platformAccount: '', version: 0 })
const meta = computed(() => ({
  tenants: { title: '租户管理', description: '创建和维护平台租户，查看当前启停状态。', columns: [['tenantCode', '租户编码'], ['tenantName', '租户名称'], ['status', '状态'], ['updatedAt', '更新时间']] },
  stores: { title: '店铺管理', description: '维护当前租户的跨境电商店铺资料。', columns: [['storeCode', '店铺编码'], ['storeName', '店铺名称'], ['platformCode', '平台'], ['status', '状态']] },
  users: { title: '用户管理', description: '维护租户成员及其身份角色。', columns: [['username', '用户名'], ['displayName', '姓名'], ['roleIds', '身份角色'], ['status', '状态'], ['updatedAt', '更新时间']] },
  rbac: { title: '角色与权限', description: '查看当前租户角色及已绑定权限。', columns: [['roleCode', '角色编码'], ['roleName', '角色名称'], ['status', '状态'], ['permissionIds', '权限数量']] },
}[props.domain]))
const canCreate = computed(() => props.domain === 'tenants' ? auth.hasPermission('tenant:create') : props.domain === 'stores' ? auth.hasPermission('store:manage') : props.domain === 'users' ? auth.hasPermission('user:manage') : false)
const canManageStores = computed(() => props.domain === 'stores' && auth.hasPermission('store:manage'))

async function load() {
  if (loading.value) return
  loading.value = true; error.value = ''; errorTraceId.value = undefined
  try {
    if (props.domain === 'users') {
      const [userPage, roles] = await Promise.all([users.listUsers(), rbac.listRoles('ACTIVE')])
      rows.value = userPage.items as unknown as Record<string, unknown>[]
      availableRoles.value = roles.filter(role => role.roleScope === 'TENANT' && role.status === 'ACTIVE' && role.roleCode !== 'TEST_NO_PERMISSION')
    } else if (props.domain === 'stores') {
      const data = await stores.listStores({
        storeCode: storeFilter.storeCode || undefined, storeName: storeFilter.storeName || undefined,
        platformCode: storeFilter.platformCode || undefined, status: storeFilter.status || undefined,
        page: storeFilter.page, pageSize: storeFilter.pageSize, sortBy: storeFilter.sortBy, sortDirection: storeFilter.sortDirection,
      })
      storeRows.value = data.items
      storeTotal.value = data.total
      storeTotalPages.value = data.totalPages
      rows.value = []
      availableRoles.value = []
    } else {
      const data = props.domain === 'tenants' ? await tenants.listTenants() : await rbac.listRoles()
      rows.value = (Array.isArray(data) ? data : data.items) as unknown as Record<string, unknown>[]
      availableRoles.value = []
    }
  } catch (cause) { const apiError = toApiError(cause); error.value = getApiErrorMessage(apiError, '列表加载失败。'); errorTraceId.value = apiError.traceId } finally { loading.value = false }
}
function readStoreQuery() { Object.assign(storeFilter, parseStoreQuery(route.query)) }
async function applyStoreFilterQuery() {
  storeFilter.page = 1
  await router.push({ query: toStoreQuery(storeFilter) })
}
async function resetStoreFilters() {
  Object.assign(storeFilter, defaultStoreFilter)
  await router.push({ query: {} })
}
async function changeStorePage(page: number) {
  if (page < 1 || page > storeTotalPages.value || page === storeFilter.page) return
  await router.replace({ query: toStoreQuery({ ...storeFilter, page }) })
}
async function changeStoreSort(sortBy: StoreFilterState['sortBy']) {
  const sortDirection = storeFilter.sortBy === sortBy && storeFilter.sortDirection === 'ASC' ? 'DESC' : 'ASC'
  await router.replace({ query: toStoreQuery({ ...storeFilter, sortBy, sortDirection, page: 1 }) })
}
function storeStatusLabel(status: stores.Store['status']) { return status === 'ACTIVE' ? '启用' : '停用' }
const createSubmit = useSubmit()
const updateSubmit = useSubmit()
const statusSubmit = useSubmit()
async function copyCreateTrace() {
  if (!createSubmit.errorTraceId.value) return
  try { await navigator.clipboard.writeText(createSubmit.errorTraceId.value) } catch { /* 页面保留编号供人工记录 */ }
}
function openStoreEdit(store: stores.Store) {
  editingStore.value = store
  Object.assign(editForm, { name: store.storeName, platformCode: store.platformCode, platformAccount: '', version: store.version })
  updateSubmit.errorMessage.value = ''
  updateSubmit.errorCode.value = undefined
  updateSubmit.errorTraceId.value = undefined
  showEdit.value = true
}
function closeStoreEdit() {
  if (!updateSubmit.submitting.value) { showEdit.value = false; editingStore.value = undefined }
}
async function updateStoreRecord() {
  const store = editingStore.value
  if (!store || updateSubmit.submitting.value) return
  if (!editForm.name.trim() || !editForm.platformCode.trim() || !editForm.platformAccount.trim()) {
    updateSubmit.errorMessage.value = '店铺名称、平台编码和平台账号不能为空。'
    updateSubmit.errorCode.value = 'COMMON-1001'
    return
  }
  await updateSubmit.submit(async () => {
    const result = await stores.updateStore(store.id, { storeName: editForm.name.trim(), platformCode: editForm.platformCode.trim(), platformAccount: editForm.platformAccount.trim(), version: editForm.version })
    showEdit.value = false
    editingStore.value = undefined
    notify.push('店铺资料已更新', 'success')
    await load()
    return result
  })
}
async function toggleStoreStatus(store: stores.Store) {
  if (statusSubmit.submitting.value) return
  const nextStatus: stores.ActiveStatus = store.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = nextStatus === 'ACTIVE' ? '启用' : '停用'
  if (typeof window !== 'undefined' && !window.confirm(`确认${action}店铺“${store.storeName}”吗？`)) return
  await statusSubmit.submit(async () => {
    const result = await stores.changeStoreStatus(store.id, { status: nextStatus, version: store.version })
    notify.push(`店铺已${action}`, 'success')
    await load()
    return result
  })
}
function selectedRoleId() {
  const id = Number(form.roleId)
  return Number.isSafeInteger(id) && id > 0 && availableRoles.value.some(role => Number(role.id) === id) ? form.roleId : undefined
}
const tenantRoleLabels: Record<string, string> = {
  MERCHANT_ADMIN: '租户管理员',
  MERCHANT_OPERATOR: '商户业务人员',
  FINANCE_OPERATOR: '财务人员',
  WAREHOUSE_OPERATOR: '仓库操作员',
  CUSTOMER_SERVICE_OPERATOR: '客服/异常专员'
}
function roleLabel(role: rbac.Role) { return tenantRoleLabels[role.roleCode] ?? role.roleName }
async function createRecord() { await createSubmit.submit(async () => {
  if (props.domain === 'tenants') await tenants.createTenant({ tenantCode: form.code.trim(), tenantName: form.name.trim(), initialAdmin: { username: form.username.trim(), displayName: form.name.trim(), temporaryPassword: form.password } })
  if (props.domain === 'stores') await stores.createStore({ storeCode: form.code.trim(), storeName: form.name.trim(), platformCode: form.platformCode.trim(), platformAccount: form.platformAccount.trim() })
  if (props.domain === 'users') {
    const roleId = selectedRoleId()
    if (!form.name.trim() || !form.username.trim()) throw new Error('姓名和用户名不能为空。')
    if (form.password.length < 12) throw new Error('临时密码至少需要 12 个字符。')
    if (!roleId) throw new Error('请选择有效的身份角色。')
    await users.createUser({ username: form.username.trim(), displayName: form.name.trim(), temporaryPassword: form.password, roleIds: [roleId] })
  }
  showCreate.value = false; Object.assign(form, { code: '', name: '', platformCode: '', platformAccount: '', username: '', password: '', roleId: '' }); notify.push('创建成功', 'success'); await load()
}) }
function formatDate(value: unknown) {
  const date = typeof value === 'number' ? new Date(value) : typeof value === 'string' && /^\d+$/.test(value) ? new Date(Number(value)) : typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value) ? new Date(value) : undefined
  return date && !Number.isNaN(date.getTime()) ? date.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }) : '—'
}
function format(key: string, value: unknown) {
  if (key === 'updatedAt' || key === 'createdAt') return formatDate(value)
  if (key === 'roleIds') {
    const labels = Array.isArray(value) ? value.map(id => availableRoles.value.find(role => String(role.id) === String(id))).filter((role): role is rbac.Role => Boolean(role)).map(roleLabel) : []
    return labels.length ? labels.join('、') : '—'
  }
  if (Array.isArray(value)) return String(value.length)
  return typeof value === 'string' && value.trim() ? value : value == null ? '—' : String(value)
}
onMounted(() => { if (props.domain === 'stores') readStoreQuery(); void load() })
watch(() => route.query, () => { if (props.domain === 'stores') { readStoreQuery(); void load() } }, { deep: true })
</script>
<template>
  <section>
    <div class="page-heading"><div><span class="kicker">业务资料</span><h1>{{ meta.title }}</h1><p>{{ meta.description }}</p></div><div class="page-actions"><button class="btn btn--secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" />{{ loading ? '刷新中…' : '刷新' }}</button><button v-if="canCreate" class="btn btn--primary" type="button" @click="showCreate = !showCreate"><Plus :size="16" />新建</button></div></div>
    <form v-if="showCreate" class="panel compact-form" @submit.prevent="createRecord">
      <label v-if="domain !== 'users'"><span>编码</span><input v-model="form.code" required /></label>
      <label><span>{{ domain === 'users' ? '姓名' : '名称' }}</span><input v-model="form.name" required /></label>
      <label v-if="domain === 'tenants' || domain === 'users'"><span>{{ domain === 'tenants' ? '管理员用户名' : '用户名' }}</span><input v-model="form.username" required /></label>
      <label v-if="domain === 'tenants' || domain === 'users'"><span>临时密码</span><input v-model="form.password" type="password" required autocomplete="new-password" /></label>
      <label v-if="domain === 'users'"><span>身份角色</span><select v-model="form.roleId" required><option value="" disabled>请选择身份角色</option><option v-for="role in availableRoles" :key="role.id" :value="String(role.id)">{{ roleLabel(role) }}</option></select></label>
      <label v-if="domain === 'stores'"><span>平台编码</span><input v-model="form.platformCode" required /></label>
      <label v-if="domain === 'stores'"><span>平台账号</span><input v-model="form.platformAccount" required /></label>
      <button class="btn btn--primary" :disabled="createSubmit.submitting.value">{{ createSubmit.submitting.value ? '提交中…' : '确认创建' }}</button>
    <div v-if="createSubmit.errorMessage.value" class="alert alert--error" role="alert"><b v-if="createSubmit.errorCode.value">{{ createSubmit.errorCode.value }}：</b>{{ createSubmit.errorMessage.value }} <span v-if="createSubmit.errorTraceId.value">追踪编号：{{ createSubmit.errorTraceId.value }} <button class="text-button" type="button" @click="copyCreateTrace">复制</button></span></div>
    </form>
    <ActionError v-if="domain === 'stores' && statusSubmit.errorMessage.value" :message="statusSubmit.errorMessage.value" :code="statusSubmit.errorCode.value" :trace-id="statusSubmit.errorTraceId.value" />
    <form v-if="domain === 'stores' && showEdit" class="panel compact-form" @submit.prevent="updateStoreRecord">
      <h2>编辑店铺资料</h2><p class="muted">平台账号不会回显，请重新输入需要保存的账号。</p>
      <label><span>店铺名称</span><input v-model="editForm.name" maxlength="128" required /></label>
      <label><span>平台编码</span><input v-model="editForm.platformCode" maxlength="64" required /></label>
      <label><span>平台账号</span><input v-model="editForm.platformAccount" maxlength="128" required autocomplete="off" /></label>
      <label><span>版本号</span><input :value="editForm.version" readonly /></label>
      <div class="page-actions"><button class="btn btn--primary" type="submit" :disabled="updateSubmit.submitting.value">{{ updateSubmit.submitting.value ? '保存中…' : '保存修改' }}</button><button class="btn btn--secondary" type="button" :disabled="updateSubmit.submitting.value" @click="closeStoreEdit">取消</button></div>
      <ActionError v-if="updateSubmit.errorMessage.value" :message="updateSubmit.errorMessage.value" :code="updateSubmit.errorCode.value" :trace-id="updateSubmit.errorTraceId.value" />
    </form>
    <form v-if="domain === 'stores'" class="panel filter-panel" @submit.prevent="applyStoreFilterQuery">
      <label><span>店铺编码</span><input v-model="storeFilter.storeCode" maxlength="64" placeholder="按编码搜索" /></label>
      <label><span>店铺名称</span><input v-model="storeFilter.storeName" maxlength="128" placeholder="按名称搜索" /></label>
      <label><span>平台</span><input v-model="storeFilter.platformCode" maxlength="64" placeholder="如 AMAZON" /></label>
      <label><span>状态</span><select v-model="storeFilter.status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
      <button class="btn btn--primary" type="submit">查询</button><button class="btn btn--secondary" type="button" @click="resetStoreFilters">重置</button>
    </form>
    <div v-if="domain === 'stores'" class="alert alert--warning" role="status">店铺国家/地区、默认发货地址和默认物流渠道以详情接口真实返回为准；当前未配置的资源会显示“当前未配置”，后端未提供的能力会显示“功能暂不可用”。</div>
    <div class="panel table-panel"><DataState :loading="loading" :error="error" :trace-id="errorTraceId" :retry="load" :empty="domain === 'stores' ? !storeRows.length : !rows.length"><div v-if="domain === 'stores'" class="data-table-wrap"><table class="data-table"><thead><tr><th><button class="table-sort" type="button" @click="changeStoreSort('storeCode')">店铺编码</button></th><th><button class="table-sort" type="button" @click="changeStoreSort('storeName')">店铺名称</button></th><th><button class="table-sort" type="button" @click="changeStoreSort('platformCode')">平台</button></th><th>状态</th><th><button class="table-sort" type="button" @click="changeStoreSort('updatedAt')">最近更新时间</button></th><th>操作</th></tr></thead><tbody><tr v-for="row in storeRows" :key="String(row.id)"><td>{{ row.storeCode }}</td><td>{{ row.storeName }}</td><td>{{ row.platformCode }}</td><td>{{ storeStatusLabel(row.status) }}</td><td>{{ formatDate(row.updatedAt) }}</td><td><RouterLink class="text-button" :to="{ name: 'app-store-detail', params: { storeId: String(row.id) }, query: storeListBackQuery(route.query) }">查看详情</RouterLink><template v-if="canManageStores"><button class="text-button" type="button" @click="openStoreEdit(row)"><Pencil :size="14" />编辑</button><button class="text-button" type="button" :disabled="statusSubmit.submitting.value" @click="toggleStoreStatus(row)"><Power :size="14" />{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</button></template></td></tr></tbody></table></div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="[, label] in meta.columns" :key="label">{{ label }}</th></tr></thead><tbody><tr v-for="row in rows" :key="String(row.id)"><td v-for="[key] in meta.columns" :key="key">{{ format(key, row[key]) }}</td></tr></tbody></table></div></DataState></div>
    <div v-if="domain === 'stores'" class="table-pagination" aria-label="店铺分页"><span>共 {{ storeTotal }} 条，第 {{ storeFilter.page }} / {{ Math.max(storeTotalPages, 1) }} 页</span><button class="btn btn--secondary" type="button" :disabled="storeFilter.page <= 1 || loading" @click="changeStorePage(storeFilter.page - 1)">上一页</button><button class="btn btn--secondary" type="button" :disabled="storeFilter.page >= storeTotalPages || loading" @click="changeStorePage(storeFilter.page + 1)">下一页</button></div>
    <section v-if="domain === 'stores'" class="panel table-panel" aria-label="店铺资源摘要">
      <table class="data-table"><thead><tr><th>国家/地区</th><th>默认发货地址</th><th>默认物流渠道</th></tr></thead>
        <tbody><tr v-for="row in storeRows" :key="`resources-${String(row.id)}`"><td>{{ row.countryRegion || '尚未配置' }}</td><td>{{ row.defaultShippingAddress || '尚未配置' }}</td><td>{{ row.defaultLogisticsChannel || '尚未配置' }}</td></tr></tbody>
      </table>
    </section>
  </section>
</template>
