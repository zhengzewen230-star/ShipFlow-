<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataState from '@/components/DataState.vue'
import ListPagination from '@/components/ListPagination.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { getOperationsMetricDrilldown, type OperationsMetricDrilldownPage, type OperationsTimeRange } from '@/services/operations'
import { displayValue } from '@/utils/display'
import { formatShanghaiDateTime, metricUnitLabel } from './operationsWorkbench'

const route = useRoute()
const router = useRouter()
const data = ref<OperationsMetricDrilldownPage>()
const loading = ref(false)
const error = ref('')
const errorCode = ref<string>()
const errorTraceId = ref<string>()
const errorStatus = ref<number>()
const page = computed(() => Math.max(1, Number(route.query.page ?? 1) || 1))
const pageSize = computed(() => [20, 50, 100].includes(Number(route.query.pageSize)) ? Number(route.query.pageSize) : 20)
const metricKey = computed(() => String(route.params.metricKey ?? '').toUpperCase())

function params(nextPage = page.value, nextPageSize = pageSize.value) {
  const requestedRange = typeof route.query.timeRange === 'string' ? route.query.timeRange : undefined
  const timeRange = requestedRange && ['TODAY', 'LAST_7_DAYS', 'LAST_30_DAYS', 'CUSTOM'].includes(requestedRange)
    ? requestedRange as OperationsTimeRange : undefined
  return { timeRange,
    from: typeof route.query.from === 'string' ? route.query.from : undefined,
    to: typeof route.query.to === 'string' ? route.query.to : undefined,
    storeId: typeof route.query.storeId === 'string' ? route.query.storeId : undefined,
    page: nextPage, pageSize: nextPageSize }
}
async function load() {
  loading.value = true; error.value = ''; errorCode.value = undefined; errorTraceId.value = undefined; errorStatus.value = undefined
  try { data.value = await getOperationsMetricDrilldown(metricKey.value, params()) }
  catch (cause) { const api = toApiError(cause); error.value = getApiErrorMessage(api, '指标明细加载失败，请重试。'); errorCode.value = api.code; errorTraceId.value = api.traceId; errorStatus.value = api.status }
  finally { loading.value = false }
}
async function changePage(next: number) { await router.replace({ query: { ...route.query, page: String(next) } }) }
async function changePageSize(next: number) { await router.replace({ query: { ...route.query, page: '1', pageSize: String(next) } }) }
watch(() => [metricKey.value, route.query], () => { void load() }, { immediate: true, deep: true })
</script>

<template>
  <section>
    <header class="page-heading"><div><span class="kicker">运营工作台</span><h1>{{ data?.metricLabel || '指标明细' }}</h1>
      <p>{{ data?.definition || '后端正在加载指标口径。' }}</p>
      <p v-if="data" class="muted">数据来源：{{ data.dataSource }}；时间字段：{{ data.timeField }}；统计窗口：{{ formatShanghaiDateTime(data.timeRange.from) }} 至 {{ formatShanghaiDateTime(data.timeRange.to) }}</p>
    </div><RouterLink class="btn btn--secondary" :to="{ path: '/app', query: route.query }">返回首页</RouterLink></header>
    <DataState :loading="loading" :error="error" :error-code="errorCode" :status="errorStatus" :trace-id="errorTraceId" :retry="load" :empty="Boolean(data && !data.items.length)" empty-title="该指标在当前范围内没有事实记录">
      <div v-if="data" class="panel table-panel"><div class="panel__heading"><h2>真实指标明细</h2><span>共 {{ data.total }} {{ metricUnitLabel({ key: data.metricKey, unit: data.unit }) }}</span></div>
        <div class="data-table-wrap"><table class="data-table"><thead><tr><th>事实编号</th><th>关联订单</th><th>状态</th><th>来源</th><th>发生时间</th></tr></thead><tbody>
          <tr v-for="item in data.items" :key="item.itemId"><td>{{ item.itemId }}</td><td>{{ item.displayNo || item.orderId || '—' }}</td><td>{{ displayValue('status', item.status) }}</td><td>{{ item.source }}</td><td>{{ formatShanghaiDateTime(item.occurredAt) }}</td></tr>
        </tbody></table></div>
      </div>
    </DataState>
    <ListPagination v-if="data" :page="data.page" :page-size="data.pageSize" :total="data.total" :total-pages="data.totalPages" :loading="loading" @update:page="changePage" @update:page-size="changePageSize" />
  </section>
</template>
