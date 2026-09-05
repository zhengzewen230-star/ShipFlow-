<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, RefreshCw, Pencil, Power } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import ActionError from '@/components/ActionError.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ListPagination from '@/components/ListPagination.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useSubmit } from '@/composables/useSubmit'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useNotificationStore } from '@/stores/notifications'
import * as tenants from '@/services/tenants'
import * as stores from '@/services/stores'
import * as users from '@/services/users'
import * as rbac from '@/services/rbac'
import { useAuthStore } from '@/stores/auth'
import { defaultStoreFilter, parseStoreQuery, toStoreQuery, type StoreFilterState } from './storeQuery'
import { storeListBackQuery } from './storeDetail'
import { displayValue } from '@/utils/display'

const props = defineProps<{ domain: 'tenants' | 'stores' | 'users' | 'rbac' }>()
const notify = useNotificationStore()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { confirm } = useConfirmAction()
const rows = ref<Record<string, unknown>[]>([])
type StorePresentationRow = stores.Store & { rawPlatformCode: string }
const storeRows = ref<StorePresentationRow[]>([])
const storeTotal = ref(0)
const storeTotalPages = ref(0)
const directoryPage = reactive({ page: 1, pageSize: 20, total: 0, totalPages: 0 })
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
      const [userPage, roles] = await Promise.all([users.listUsers({ page: directoryPage.page, pageSize: directoryPage.pageSize }), rbac.listRoles('ACTIVE')])
      rows.value = userPage.items as unknown as Record<string, unknown>[]
      Object.assign(directoryPage, { page: userPage.page, pageSize: userPage.pageSize, total: userPage.total, totalPages: userPage.totalPages })
      availableRoles.value = roles.filter(role => role.roleScope === 'TENANT' && role.status === 'ACTIVE' && role.roleCode !== 'TEST_NO_PERMISSION')
    } else if (props.domain === 'stores') {
      const data = await stores.listStores({
        storeCode: storeFilter.storeCode || undefined, storeName: storeFilter.storeName || undefined,
        platformCode: storeFilter.platformCode || undefined, status: storeFilter.status || undefined,
        page: storeFilter.page, pageSize: storeFilter.pageSize, sortBy: storeFilter.sortBy, sortDirection: storeFilter.sortDirection,
      })
      storeRows.value = data.items.map(store => ({
        ...store,
        rawPlatformCode: store.platformCode,
        platformCode: displayValue('platformCode', store.platformCode),
      }))
      storeTotal.value = data.total
      storeTotalPages.value = data.totalPages
      rows.value = []
      availableRoles.value = []
    } else if (props.domain === 'tenants') {
      const data = await tenants.listTenants({ page: directoryPage.page, pageSize: directoryPage.pageSize })
      rows.value = data.items as unknown as Record<string, unknown>[]
      Object.assign(directoryPage, { page: data.page, pageSize: data.pageSize, total: data.total, totalPages: data.totalPages })
      availableRoles.value = []
    } else {
      const roles = await rbac.listRoles()
      const start = (directoryPage.page - 1) * directoryPage.pageSize
      rows.value = roles.slice(start, start + directoryPage.pageSize) as unknown as Record<string, unknown>[]
      directoryPage.total = roles.length
      directoryPage.totalPages = Math.max(1, Math.ceil(roles.length / directoryPage.pageSize))
      availableRoles.value = []
    }
  } catch (cause) { const apiError = toApiError(cause); error.value = getApiErrorMessage(apiError, '列表加载失败。'); errorTraceId.value = apiError.traceId } finally { loading.value = false }
}
function readStoreQuery() { Object.assign(storeFilter, parseStoreQuery(route.query)) }
function readDirectoryQuery() {
  const rawPage = Array.isArray(route.query.page) ? route.query.page[0] : route.query.page
  const rawPageSize = Array.isArray(route.query.pageSize) ? route.query.pageSize[0] : route.query.pageSize
  const page = Number(rawPage)
  const pageSize = Number(rawPageSize)
  directoryPage.page = Number.isInteger(page) && page > 0 ? page : 1
  directoryPage.pageSize = [20, 50, 100].includes(pageSize) ? pageSize : 20
}
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
async function changeStorePageSize(pageSize: number) {
  await router.replace({ query: toStoreQuery({ ...storeFilter, pageSize, page: 1 }) })
}
async function changeDirectoryPage(page: number) {
  if (page < 1 || page > directoryPage.totalPages || page === directoryPage.page) return
  await router.replace({ query: { ...route.query, page: String(page), pageSize: String(directoryPage.pageSize) } })
}
async function changeDirectoryPageSize(pageSize: number) {
  await router.replace({ query: { ...route.query, page: '1', pageSize: String(pageSize) } })
}
async function changeStoreSort(sortBy: StoreFilterState['sortBy']) {
  const sortDirection = storeFilter.sortBy === sortBy && storeFilter.sortDirection === 'ASC' ? 'DESC' : 'ASC'
  await router.replace({ query: toStoreQuery({ ...storeFilter, sortBy, sortDirection, page: 1 }) })
}
function storeStatusLabel(status: stores.Store['status']) { return status === 'ACTIVE' ? '启用' : '停用' }
const createSubmit = useSubmit()
const updateSubmit = useSubmit()
const statusSubmit = useSubmit()
function openStoreEdit(store: StorePresentationRow) {
  editingStore.value = store
  Object.assign(editForm, { name: store.storeName, platformCode: store.rawPlatformCode, platformAccount: '', version: store.version })
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
  if (!await confirm({ title: `${action}店铺`, description: `确认${action}店铺“${store.storeName}”吗？该操作会写入审计日志。`, confirmLabel: `确认${action}`, danger: nextStatus === 'DISABLED' })) return
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
  return displayValue(key, value)
}
onMounted(() => { if (props.domain === 'stores') readStoreQuery(); else readDirectoryQuery(); void load() })
watch(() => route.query, () => {
  if (props.domain === 'stores') readStoreQuery()
  else readDirectoryQuery()
  void load()
}, { deep: true })
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
    <ActionError :message="createSubmit.errorMessage.value" :code="createSubmit.errorCode.value" :trace-id="createSubmit.errorTraceId.value" />
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
    <div class="panel table-panel"><DataState :loading="loading" :error="error" :trace-id="errorTraceId" :retry="load" :empty="domain === 'stores' ? !storeRows.length : !rows.length"><div v-if="domain === 'stores'" class="data-table-wrap"><table class="data-table"><thead><tr><th><button class="table-sort" type="button" @click="changeStoreSort('storeCode')">店铺编码</button></th><th><button class="table-sort" type="button" @click="changeStoreSort('storeName')">店铺名称</button></th><th><button class="table-sort" type="button" @click="changeStoreSort('platformCode')">平台</button></th><th>状态</th><th><button class="table-sort" type="button" @click="changeStoreSort('updatedAt')">最近更新时间</button></th><th>操作</th></tr></thead><tbody><tr v-for="row in storeRows" :key="String(row.id)"><td>{{ row.storeCode }}</td><td>{{ row.storeName }}</td><td>{{ row.platformCode }}</td><td><StatusBadge :status="row.status" :label="storeStatusLabel(row.status)" /></td><td>{{ formatDate(row.updatedAt) }}</td><td><RouterLink class="text-button" :to="{ name: 'app-store-detail', params: { storeId: String(row.id) }, query: storeListBackQuery(route.query) }">查看详情</RouterLink><template v-if="canManageStores"><button class="text-button" type="button" @click="openStoreEdit(row)"><Pencil :size="14" />编辑</button><button class="text-button" type="button" :disabled="statusSubmit.submitting.value" @click="toggleStoreStatus(row)"><Power :size="14" />{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</button></template></td></tr></tbody></table></div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="[, label] in meta.columns" :key="label">{{ label }}</th></tr></thead><tbody><tr v-for="row in rows" :key="String(row.id)"><td v-for="[key] in meta.columns" :key="key"><StatusBadge v-if="key === 'status'" :status="String(row[key] ?? '')" /><template v-else>{{ format(key, row[key]) }}</template></td></tr></tbody></table></div></DataState></div>
    <ListPagination v-if="domain === 'stores'" :page="storeFilter.page" :page-size="storeFilter.pageSize" :total="storeTotal" :total-pages="storeTotalPages" :loading="loading" @update:page="changeStorePage" @update:page-size="changeStorePageSize" />
    <ListPagination v-else :page="directoryPage.page" :page-size="directoryPage.pageSize" :total="directoryPage.total" :total-pages="directoryPage.totalPages" :loading="loading" @update:page="changeDirectoryPage" @update:page-size="changeDirectoryPageSize" />
    <section v-if="domain === 'stores'" class="panel table-panel" aria-label="店铺资源摘要">
      <table class="data-table"><thead><tr><th>国家/地区</th><th>默认发货地址</th><th>默认物流渠道</th></tr></thead>
        <tbody><tr v-for="row in storeRows" :key="`resources-${String(row.id)}`"><td>{{ row.countryRegion || '尚未配置' }}</td><td>{{ row.defaultShippingAddress || '尚未配置' }}</td><td>{{ row.defaultLogisticsChannel || '尚未配置' }}</td></tr></tbody>
      </table>
    </section>
  </section>
</template>
