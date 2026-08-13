<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { getApiErrorMessage } from '@/services/http'
import { useSubmit } from '@/composables/useSubmit'
import { useAuthStore } from '@/stores/auth'
import * as quotes from '@/services/quotes'
import * as orders from '@/services/orders'
import * as warehouse from '@/services/warehouse'
import * as tracking from '@/services/tracking'
import * as exceptions from '@/services/exceptions'
import * as billing from '@/services/billing'
import * as audit from '@/services/audit'

const props = defineProps<{ domain: 'quotes' | 'orders' | 'warehouse' | 'tracking' | 'exceptions' | 'billing' | 'audit' }>()
const auth = useAuthStore(); const loading = ref(false); const error = ref(''); const rows = ref<Record<string, unknown>[]>([]); const selected = ref<Record<string, unknown>>(); const lookupId = ref(''); const tenantId = ref(''); const submit = useSubmit()
const action = reactive({ version: 0, trackingNo: '', actualWeight: 0, actualLength: 0, actualWidth: 0, actualHeight: 0 })
const info = computed(() => ({ quotes: ['报价管理', '查看报价历史、详情并校验当前有效性。'], orders: ['订单管理', 'OpenAPI 暂未提供订单列表，只能按订单 ID 查询详情并执行状态操作。'], warehouse: ['仓库作业', '按订单处理入库、复称、费用确认与出库。'], tracking: ['轨迹追踪', '按订单查询物流轨迹与当前状态。'], exceptions: ['异常与索赔', '查看异常单及索赔处理结果。'], billing: ['账单与对账', '查看账单批次、明细与对账记录。'], audit: ['审计日志', '查询当前权限范围内的脱敏操作记录。'] }[props.domain]))
const columns = computed(() => Object.keys(rows.value[0] ?? {}).filter(k => !['detail', 'feeDetail', 'claim'].includes(k)).slice(0, 7))
function selectResult(value: unknown) {
  selected.value = value as Record<string, unknown>
  if (typeof selected.value.version === 'number') action.version = selected.value.version
}
async function loadList() { loading.value = true; error.value = ''; selected.value = undefined; try { let data: unknown
  if (props.domain === 'quotes') data = await quotes.listQuotes()
  else if (props.domain === 'exceptions') data = await exceptions.listExceptionCases()
  else if (props.domain === 'billing') data = auth.hasPermission('finance:bill-import') ? await billing.listBillImportBatches() : await billing.listReconciliations()
  else if (props.domain === 'audit') data = auth.scope === 'PLATFORM' ? (tenantId.value ? await audit.listPlatformTenantAuditLogs(tenantId.value) : { items: [] }) : await audit.listTenantAuditLogs()
  else if (props.domain === 'orders') data = await orders.listShipmentOrders()
  else return
  rows.value = ((data as { items: Record<string, unknown>[] }).items ?? [])
} catch (cause) { error.value = getApiErrorMessage(cause, '数据加载失败。') } finally { loading.value = false } }
async function lookup() { if (!lookupId.value.trim()) return; loading.value = true; error.value = ''; rows.value = []; try { let data: unknown
  if (props.domain === 'orders' || props.domain === 'warehouse') data = await orders.getOrder(lookupId.value.trim())
  else if (props.domain === 'tracking') data = { status: await tracking.getShipmentOrderTrackingStatus(lookupId.value.trim()), events: await tracking.listShipmentOrderTracking(lookupId.value.trim()) }
  else if (props.domain === 'quotes') data = await quotes.getQuote(lookupId.value.trim())
  else if (props.domain === 'exceptions') data = await exceptions.getExceptionCase(lookupId.value.trim())
  else if (props.domain === 'billing') data = auth.hasPermission('finance:bill-import') ? await billing.getBillImportBatch(lookupId.value.trim()) : await billing.getReconciliation(lookupId.value.trim())
  else data = auth.scope === 'PLATFORM' ? await audit.getPlatformTenantAuditLog(tenantId.value, lookupId.value.trim()) : await audit.getTenantAuditLog(lookupId.value.trim())
  selectResult(data)
} catch (cause) { error.value = getApiErrorMessage(cause, '详情查询失败。') } finally { loading.value = false } }
async function runOrderAction(kind: 'submit' | 'cancel' | 'inbound' | 'measure' | 'outbound') { await submit.submit(async () => { const id = lookupId.value.trim(); let result: unknown
  if (kind === 'submit') result = await orders.submitOrder(id, action.version)
  if (kind === 'cancel') result = await orders.cancelOrder(id, { version: action.version })
  if (kind === 'inbound') result = await warehouse.confirmInbound(id, action.version)
  if (kind === 'measure') result = await warehouse.submitMeasurement(id, { actualWeight: action.actualWeight, actualLength: action.actualLength, actualWidth: action.actualWidth, actualHeight: action.actualHeight, measuredAt: new Date().toISOString(), version: action.version })
  if (kind === 'outbound') result = await warehouse.confirmOutbound(id, { trackingNo: action.trackingNo, outboundAt: new Date().toISOString(), version: action.version })
  selectResult(result)
}) }
function format(value: unknown) { return typeof value === 'object' ? JSON.stringify(value) : String(value ?? '—') }
onMounted(() => { if (['quotes', 'orders', 'exceptions', 'billing', 'audit'].includes(props.domain)) loadList() })
</script>
<template><section><div class="page-heading"><div><span class="kicker">业务工作台</span><h1>{{ info[0] }}</h1><p>{{ info[1] }}</p></div></div><div class="panel lookup-bar"><label v-if="domain === 'audit' && auth.scope === 'PLATFORM'"><span>租户 ID</span><input v-model="tenantId" /></label><label><span>{{ domain === 'audit' ? '日志 ID' : domain === 'billing' ? '批次 ID' : domain === 'exceptions' ? '异常单 ID' : domain === 'quotes' ? '报价 ID' : '订单 ID' }}</span><input v-model="lookupId" @keyup.enter="lookup" /></label><button class="btn btn--primary" :disabled="loading" @click="lookup"><Search :size="16" />查询详情</button><button v-if="['quotes','exceptions','billing','audit'].includes(domain)" class="btn btn--secondary" @click="loadList">查看列表</button></div><div v-if="domain === 'warehouse' && selected" class="panel action-panel"><label><span>版本</span><input v-model.number="action.version" type="number" min="0" /></label><label><span>实际重量 kg</span><input v-model.number="action.actualWeight" type="number" step="0.001" /></label><label><span>长 / 宽 / 高 cm</span><span class="inline-inputs"><input v-model.number="action.actualLength" type="number" /><input v-model.number="action.actualWidth" type="number" /><input v-model.number="action.actualHeight" type="number" /></span></label><label><span>物流单号</span><input v-model="action.trackingNo" /></label><div class="page-actions"><button class="btn btn--secondary" :disabled="submit.submitting.value" @click="runOrderAction('inbound')">确认入库</button><button class="btn btn--secondary" :disabled="submit.submitting.value" @click="runOrderAction('measure')">提交复称</button><button class="btn btn--primary" :disabled="submit.submitting.value" @click="runOrderAction('outbound')">确认出库</button></div></div><div v-if="domain === 'orders' && selected" class="page-actions action-row"><button class="btn btn--primary" :disabled="submit.submitting.value" @click="runOrderAction('submit')">提交订单</button><button class="btn btn--secondary" :disabled="submit.submitting.value" @click="runOrderAction('cancel')">取消订单</button></div><div v-if="submit.errorMessage.value" class="alert alert--error">{{ submit.errorMessage.value }}</div><DataState :loading="loading" :error="error" :empty="!rows.length && !selected"><div v-if="selected" class="panel detail-grid"><div v-for="(value,key) in selected" :key="key"><small>{{ key }}</small><span>{{ format(value) }}</span></div></div><div v-else class="panel table-panel"><div class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="key in columns" :key="key">{{ key }}</th></tr></thead><tbody><tr v-for="row in rows" :key="String(row.id)"><td v-for="key in columns" :key="key">{{ format(row[key]) }}</td></tr></tbody></table></div></div></DataState></section></template>
