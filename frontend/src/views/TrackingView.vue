<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElTimeline, ElTimelineItem } from 'element-plus'
import { RefreshCw, Search } from '@lucide/vue'
import CopyTextButton from '@/components/CopyTextButton.vue'
import DataState from '@/components/DataState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { getShipmentTrackingTimeline, type ShipmentTrackingTimelineEvent } from '@/services/tracking'
import { trackingFilter } from './workbenchTargetFilters'

const route = useRoute()
const router = useRouter()
const reference = ref('')
const events = ref<ShipmentTrackingTimelineEvent[]>([])
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const errorCode = ref<string>()
const errorStatus = ref<number>()
const searched = ref(false)
const routeFilterNotice = ref('')
let requestNo = 0
const autoRefresh = ref(false)
const lastRefreshedAt = ref<string>()
let refreshTimer: ReturnType<typeof setInterval> | undefined

async function search() {
  const value = reference.value.trim()
  if (!value) return
  const currentRequest = ++requestNo
  loading.value = true
  error.value = ''
  errorCode.value = undefined
  errorStatus.value = undefined
  errorTraceId.value = undefined
  searched.value = true
  try {
    const result = await getShipmentTrackingTimeline(value)
    if (currentRequest === requestNo) {
      events.value = result
      lastRefreshedAt.value = new Date().toISOString()
      if (result.some(event => ['DELIVERED', 'CANCELLED', 'RETURNED', 'LOST'].includes(event.statusCode))) stopAutoRefresh()
    }
  } catch (cause) {
    if (currentRequest === requestNo) {
      events.value = []
      const apiError = toApiError(cause)
      error.value = getApiErrorMessage(apiError, '轨迹查询失败，请确认订单号或顺丰单号。')
      errorCode.value = apiError.code
      errorStatus.value = apiError.status
      errorTraceId.value = apiError.traceId
    }
  } finally {
    if (currentRequest === requestNo) loading.value = false
  }
}

function submitSearch() {
  const value = reference.value.trim()
  if (!value) return
  if (route.query.reference === value) void search()
  else void router.replace({ query: { ...route.query, reference: value } })
}

function stopAutoRefresh() {
  autoRefresh.value = false
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = undefined
}

function toggleAutoRefresh() {
  if (autoRefresh.value) {
    stopAutoRefresh()
    return
  }
  autoRefresh.value = true
  refreshTimer = setInterval(() => { if (!loading.value) void search() }, 30_000)
}

function occurredAt(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}

watch(() => route.fullPath, () => {
  stopAutoRefresh()
  const filter = trackingFilter(route.query)
  routeFilterNotice.value = filter.notice ?? ''
  reference.value = filter.reference ?? ''
  events.value = []
  searched.value = false
  errorTraceId.value = undefined
  errorCode.value = undefined
  errorStatus.value = undefined
  if (filter.reference) void search()
}, { immediate: true })

onUnmounted(stopAutoRefresh)
</script>

<template>
  <section class="tracking-view">
    <div class="page-heading">
      <div>
        <span class="kicker">物流追踪</span>
        <h1>轨迹全链路</h1>
        <p>输入业务订单号或顺丰运单号，查看仓内作业与顺丰路由的统一时间轴。</p>
      </div>
    </div>

    <form class="panel tracking-search" @submit.prevent="submitSearch">
      <label>
        <span>订单号 / 顺丰单号</span>
        <input v-model.trim="reference" placeholder="例如 UAT-SF-202608160001" autocomplete="off" />
      </label>
      <button class="btn btn--primary" type="submit" :disabled="loading || !reference.trim()">
        <Search :size="16" />查询轨迹
      </button>
      <button class="btn" type="button" :disabled="loading || !reference.trim()" @click="search">
        <RefreshCw :size="16" :class="{ spinning: loading }" /> 刷新
      </button>
      <button class="btn" type="button" :disabled="!reference.trim()" @click="toggleAutoRefresh">
        {{ autoRefresh ? '停止自动刷新' : '自动刷新（30 秒）' }}
      </button>
    </form>
    <div v-if="routeFilterNotice" class="alert alert--warning" role="status">{{ routeFilterNotice }}</div>

    <DataState :loading="loading" :error="error" :error-code="errorCode" :status="errorStatus" :trace-id="errorTraceId" :retry="search" :empty="searched && !events.length && !loading && !error">
      <div v-if="!searched" class="panel data-state">请输入订单号或顺丰单号开始查询。</div>
      <div v-else class="panel tracking-timeline-panel">
        <div class="panel__heading">
          <div>
            <h2>{{ events[0]?.orderNo || reference }} <CopyTextButton :value="events[0]?.orderNo || reference" label="订单号" /></h2>
            <p>{{ events[0]?.waybillNo ? `顺丰运单号：${events[0].waybillNo}` : '暂无顺丰运单号' }} <CopyTextButton v-if="events[0]?.waybillNo" :value="events[0].waybillNo" label="顺丰单号" /></p>
          </div>
          <span v-if="events.length" class="tracking-count">{{ events.length }} 个节点</span>
        </div>
        <small v-if="lastRefreshedAt" class="tracking-last-refreshed">最近刷新：{{ occurredAt(lastRefreshedAt) }}</small>
        <ElTimeline class="tracking-timeline">
          <ElTimelineItem
            v-for="event in events"
            :key="`${event.statusCode}-${event.occurredAt}-${event.id}`"
            :type="event.source === 'INTERNAL' ? 'success' : 'primary'"
            :timestamp="occurredAt(event.occurredAt)"
            placement="top"
          >
            <div class="tracking-event-card">
              <div class="tracking-event-heading">
                <strong>{{ event.title }}</strong>
                <span :class="['tracking-source', event.source === 'INTERNAL' ? 'tracking-source--internal' : 'tracking-source--sf']">
                  {{ event.source === 'INTERNAL' ? '仓内节点' : '顺丰节点' }}
                </span>
              </div>
              <p>{{ event.description || '—' }}</p>
              <small>{{ event.location || '—' }} · <StatusBadge :status="event.statusCode" /></small>
              <RouterLink v-if="['LOST', 'RETURNED', 'EXCEPTION'].includes(event.statusCode)" :to="{ path: '/app/exceptions', query: { orderId: event.orderId } }">查看异常处理</RouterLink>
            </div>
          </ElTimelineItem>
        </ElTimeline>
      </div>
    </DataState>
  </section>
</template>

<style scoped>
.tracking-search { display: flex; align-items: end; gap: 1rem; margin-bottom: 1.2rem; }
.tracking-search label { display: flex; flex: 1; flex-direction: column; gap: .35rem; color: var(--navy-800); font-size: .8rem; font-weight: 700; }
.tracking-search input { min-height: 42px; padding: .55rem .7rem; border: 1px solid var(--slate-300); border-radius: .45rem; }
.tracking-timeline-panel { padding: 1.35rem 1.5rem; }
.tracking-count { color: var(--slate-600); font-size: .85rem; }
.tracking-timeline { max-width: 820px; margin-top: 1.5rem; }
.tracking-event-card { padding: .8rem 1rem; border: 1px solid var(--slate-200); border-radius: .55rem; background: var(--slate-50); }
.tracking-event-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.tracking-event-card p { margin: .45rem 0; color: var(--slate-700); }
.tracking-event-card small { color: var(--slate-500); }
.tracking-source { padding: .2rem .45rem; border-radius: 999px; font-size: .72rem; font-weight: 700; }
.tracking-source--internal { color: #16803c; background: #e9f8ef; }
.tracking-source--sf { color: #2161b5; background: #eaf2ff; }
@media (max-width: 680px) { .tracking-search { align-items: stretch; flex-direction: column; } }
</style>
