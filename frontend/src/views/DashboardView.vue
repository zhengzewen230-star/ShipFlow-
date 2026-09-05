<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowUpRight, Building2, CircleAlert, ClipboardList, PackageCheck, RefreshCw, Route, ScrollText, Users } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { getApiErrorMessage } from '@/services/http'
import type { OperationsMetric, OperationsTimeRange, OperationsWorkbench } from '@/services/operations'
import { getWarehouseOverview, type WarehouseOverview } from '@/services/warehouse'
import { formatChargeableWeight } from '@/utils/display'
import MetricCard from '@/components/MetricCard.vue'
import WorkQueue from '@/components/WorkQueue.vue'
import RiskPanel from '@/components/RiskPanel.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import {
  classifyWorkbenchError,
  copyWorkbenchTraceId,
  createOperationsWorkbenchLoader,
  formatMoney,
  formatShanghaiDateTime,
  formatShanghaiWindow,
  formatWeightKg,
  isOperationsWorkbenchEmpty,
  metricUnitLabel,
  workbenchStatusLabel,
  workbenchTargetLocation,
} from './operationsWorkbench'

const auth = useAuthStore()
const isPlatform = computed(() => auth.scope === 'PLATFORM')
const canReadOperations = computed(() => auth.hasPermission('operations:read'))
const canReadWarehouse = computed(() => auth.hasPermission('warehouse:manage'))
const canCreateQuote = computed(() => auth.hasPermission('quote:create'))
const timeRange = ref<OperationsTimeRange>('TODAY')
const workbench = ref<OperationsWorkbench>()
const loading = ref(false)
const error = ref('')
const errorKind = ref<ReturnType<typeof classifyWorkbenchError>['kind']>()
const errorTraceId = ref<string>()
const traceCopyMessage = ref('')
const loader = createOperationsWorkbenchLoader()
const warehouseOverview = ref<WarehouseOverview>()
const warehouseLoading = ref(false)
const warehouseError = ref('')

const metricIcons: Record<string, unknown> = {
  PENDING_ORDERS: ClipboardList,
  PENDING_INBOUND: PackageCheck,
  PENDING_MEASUREMENT: PackageCheck,
  PENDING_LABEL: ScrollText,
  PENDING_OUTBOUND: Building2,
  IN_TRANSIT: Route,
  TRACKING_EXCEPTION: CircleAlert,
  PENDING_FINANCE: ClipboardList,
}

function metricIcon(metric: OperationsMetric) { return metricIcons[metric.key] ?? ClipboardList }
function targetLocation(target: OperationsMetric['target']) { return workbenchTargetLocation(target) ?? { path: '/app' } }
function reloadLabel() { return loading.value ? '刷新中…' : '刷新' }
const warehouseStages = ['待入库', '待复称', '待贴标/打单', '待交接/出库', '在途运输']
const warehouseMetrics = computed(() => [
  [PackageCheck, '待入库', warehouseOverview.value?.pendingInbound ?? 0],
  [PackageCheck, '待复称', warehouseOverview.value?.pendingMeasurement ?? 0],
  [ScrollText, '待贴标/打单', warehouseOverview.value?.pendingLabel ?? 0],
  [Building2, '待交接/出库', warehouseOverview.value?.pendingHandover ?? 0],
  [ClipboardList, '今日入库', warehouseOverview.value?.todayInbound ?? 0],
  [ClipboardList, '今日出库', warehouseOverview.value?.todayOutbound ?? 0],
  [Route, '在途运输', warehouseOverview.value?.inTransit ?? 0],
  [CircleAlert, '轨迹异常', warehouseOverview.value?.trackingExceptions ?? 0],
] as const)
const queueItems = computed(() => (workbench.value?.todos ?? []).map((todo, index) => ({
  key: todo.key,
  label: todo.label,
  count: todo.count,
  priority: index === 0 ? '优先处理' : '待处理',
  to: targetLocation(todo.target),
})))
const riskItems = computed(() => (workbench.value?.risks ?? []).map(risk => ({
  id: risk.id,
  level: risk.level,
  title: risk.title,
  description: risk.description,
  occurredAt: formatShanghaiDateTime(risk.occurredAt),
  to: targetLocation(risk.target),
})))
const coreMetrics = computed(() => {
  return workbench.value?.metrics ?? []
})

async function load() {
  if (isPlatform.value || (!canReadOperations.value && !canReadWarehouse.value)) return
  if (loading.value || warehouseLoading.value) return
  error.value = ''
  errorKind.value = undefined
  errorTraceId.value = undefined
  traceCopyMessage.value = ''
  warehouseError.value = ''
  const requests: Promise<void>[] = []
  if (canReadOperations.value) {
    loading.value = true
    requests.push(loader.load({ timeRange: timeRange.value, recentLimit: 10, riskLimit: 10 })
      .then(data => { workbench.value = data })
      .catch(cause => {
        const classified = classifyWorkbenchError(cause)
        errorKind.value = classified.kind
        errorTraceId.value = classified.traceId
        error.value = getApiErrorMessage(cause, classified.message)
      })
      .finally(() => { loading.value = false }))
  }
  if (canReadWarehouse.value) {
    warehouseLoading.value = true
    requests.push(getWarehouseOverview()
      .then(data => { warehouseOverview.value = data })
      .catch(cause => { warehouseError.value = getApiErrorMessage(cause, '仓库概览加载失败，请稍后重试。') })
      .finally(() => { warehouseLoading.value = false }))
  }
  await Promise.all(requests)
}

function retry() { void load() }
async function copyTraceId() {
  const copied = await copyWorkbenchTraceId(errorTraceId.value)
  traceCopyMessage.value = copied ? '追踪编号已复制。' : '追踪编号复制失败，请手动记录。'
}
watch(timeRange, () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <section v-if="isPlatform" class="dashboard">
    <div class="page-heading"><div><span class="kicker">平台工作台</span><h1>平台运营概览</h1><p>管理商户入驻、平台租户与脱敏审计信息；不显示租户专属的报价、订单与仓配数据。</p></div></div>
    <div class="dashboard-grid">
      <RouterLink v-if="auth.hasPermission('tenant:read')" class="panel" to="/app/tenants"><Building2 :size="22" /><h2>租户管理</h2><p>查看和维护平台租户。</p><span class="text-link">进入管理 <ArrowUpRight :size="15" /></span></RouterLink>
      <RouterLink v-if="auth.hasPermission('tenant:manage')" class="panel" to="/app/onboarding-applications"><Users :size="22" /><h2>商户入驻申请</h2><p>审核企业入驻申请，后续创建租户及管理员。</p><span class="text-link">查看申请 <ArrowUpRight :size="15" /></span></RouterLink>
      <RouterLink v-if="auth.hasPermission('tenant:manage')" class="panel" to="/app/guest-estimate-leads"><ClipboardList :size="22" /><h2>访客预估线索</h2><p>预估需求仅供运营跟进，不是正式报价。</p><span class="text-link">查看入口 <ArrowUpRight :size="15" /></span></RouterLink>
      <RouterLink v-if="auth.hasPermission('audit:read')" class="panel" to="/app/audit"><ScrollText :size="22" /><h2>审计日志</h2><p>按租户查询脱敏操作记录。</p><span class="text-link">查询记录 <ArrowUpRight :size="15" /></span></RouterLink>
    </div>
  </section>

  <section v-else class="dashboard">
    <div class="page-heading">
      <div><span class="kicker">商家业务员工作台</span><h1>运营概览</h1><p>指标、待办、订单和风险均来自同一真实 API 快照。</p></div>
      <div class="workbench-toolbar">
        <label>统计范围<select v-model="timeRange" :disabled="loading"><option value="TODAY">今日</option><option value="LAST_7_DAYS">近 7 天</option><option value="LAST_30_DAYS">近 30 天</option></select></label>
        <button class="btn btn--secondary" type="button" :disabled="loading || warehouseLoading || (!canReadOperations && !canReadWarehouse)" @click="load"><RefreshCw :size="16" />{{ reloadLabel() }}</button>
        <RouterLink v-if="canCreateQuote" class="btn btn--primary" to="/app/quotes/create">新建正式报价 <ArrowUpRight :size="17" /></RouterLink>
      </div>
    </div>

    <div v-if="!canReadOperations" class="panel workbench-state" role="status"><CircleAlert :size="24" /><strong>暂无运营概览权限</strong><p>当前账号没有 operations:read 权限，后端不会返回租户工作台数据。</p></div>
    <LoadingSkeleton v-else-if="loading" :rows="6" />
    <div v-else-if="errorKind === 'unauthorized'" class="panel workbench-state" role="alert"><strong>登录状态已失效</strong><p>{{ error }}</p><p v-if="errorTraceId" class="workbench-trace">追踪编号：{{ errorTraceId }} <button class="text-button" type="button" @click="copyTraceId">复制</button></p><small v-if="traceCopyMessage" role="status">{{ traceCopyMessage }}</small><RouterLink class="btn btn--primary" to="/login">重新登录</RouterLink></div>
    <div v-else-if="errorKind === 'forbidden'" class="panel workbench-state" role="alert"><CircleAlert :size="24" /><strong>当前账号暂无访问权限</strong><p>{{ error }}</p><p v-if="errorTraceId" class="workbench-trace">追踪编号：{{ errorTraceId }} <button class="text-button" type="button" @click="copyTraceId">复制</button></p><small v-if="traceCopyMessage" role="status">{{ traceCopyMessage }}</small></div>
    <div v-else-if="error" class="panel workbench-state" role="alert"><CircleAlert :size="24" /><strong>运营概览暂时不可用</strong><p>{{ error }}</p><p v-if="errorTraceId" class="workbench-trace">追踪编号：{{ errorTraceId }} <button class="text-button" type="button" @click="copyTraceId">复制</button></p><small v-if="traceCopyMessage" role="status">{{ traceCopyMessage }}</small><button class="btn btn--secondary" type="button" @click="retry">重试</button></div>
    <template v-else-if="workbench">
      <div class="workbench-meta"><span>统计窗口：{{ formatShanghaiWindow(workbench.timeRange) }}（{{ workbench.businessTimeZone }}）</span><span>刷新时间：{{ formatShanghaiDateTime(workbench.refreshedAt) }}</span></div>
      <div v-if="isOperationsWorkbenchEmpty(workbench)" class="panel workbench-empty-banner" role="status">当前统计窗口暂无待处理订单、待办或风险提醒。</div>

      <section aria-labelledby="workbench-metrics-title">
        <div class="panel__heading workbench-section-heading"><h2 id="workbench-metrics-title">今日核心指标</h2><span>后端返回数量，不在前端重新合计</span></div>
        <div class="metric-grid workbench-metrics">
          <MetricCard v-for="metric in coreMetrics" :key="metric.key" :label="metric.label" :value="metric.count" :hint="`${formatShanghaiWindow(metric.window)} · ${metricUnitLabel(metric)}`" :definition="metric.definition" :source="metric.dataSource && metric.timeField ? `${metric.dataSource}（${metric.timeField}）` : metric.dataSource" :to="targetLocation(metric.target)" :tone="metric.key.includes('EXCEPTION') ? 'danger' : metric.key.includes('FINANCE') || metric.key.includes('FEE') ? 'warning' : 'default'">
            <template #icon><component :is="metricIcon(metric)" :size="19" /></template>
          </MetricCard>
        </div>
      </section>

      <section class="workbench-two-column" aria-label="待办和风险">
        <WorkQueue :items="queueItems" />
        <RiskPanel :items="riskItems" />
      </section>

        <section class="panel workbench-panel" aria-labelledby="recent-orders-title"><div class="panel__heading workbench-section-heading"><div><h2 id="recent-orders-title">最近订单</h2><span>按后端返回的最近更新时间排序</span></div><RouterLink class="table-link" to="/app/orders">进入订单列表 <ArrowUpRight :size="15" /></RouterLink></div><p v-if="!workbench.recentOrders.length" class="data-state">暂无最近订单</p><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>订单号</th><th>店铺</th><th>目的地</th><th>当前状态</th><th>计费重量</th><th>预计费用</th><th>顺丰单号</th><th>最近更新时间</th><th>下一步操作</th></tr></thead><tbody><tr v-for="order in workbench.recentOrders" :key="order.orderId"><td><RouterLink class="table-link" :to="targetLocation(order.target)">{{ order.orderNo }}</RouterLink></td><td>{{ order.storeName }}</td><td>{{ order.destination }}</td><td>{{ workbenchStatusLabel(order.orderStatus) }}</td><td>{{ formatWeightKg(order.chargeableWeight) }}</td><td>{{ formatMoney(order.estimatedFee, order.currency) }}</td><td>{{ order.sfTrackingNo || '尚未生成' }}</td><td>{{ formatShanghaiDateTime(order.updatedAt) }}</td><td>{{ order.nextAction }}</td></tr></tbody></table></div></section>
    </template>

    <section v-if="canReadWarehouse" class="warehouse-overview" aria-labelledby="warehouse-overview-title">
      <div class="panel warehouse-list-panel">
        <div class="panel__heading"><div><span class="kicker">履约协同</span><h2 id="warehouse-overview-title">仓库履约概览</h2><p>保留既有仓库真实 API 投影，店铺和租户范围仍由后端校验。</p></div><RouterLink class="table-link" to="/app/warehouse">进入仓库作业 <ArrowUpRight :size="15" /></RouterLink></div>
        <div v-if="warehouseLoading" class="workbench-state" role="status">正在加载仓库概览…</div>
        <div v-else-if="warehouseError" class="workbench-state" role="alert"><CircleAlert :size="22" /><strong>仓库概览暂时不可用</strong><p>{{ warehouseError }}</p><button class="btn btn--secondary" type="button" @click="load">重试</button></div>
        <template v-else-if="warehouseOverview">
          <div class="metric-grid warehouse-metric-grid">
            <RouterLink v-for="([icon, label, count]) in warehouseMetrics" :key="label" class="workbench-card" to="/app/warehouse"><span class="workbench-card__icon"><component :is="icon" :size="20" /></span><div class="workbench-card__body"><small>{{ label }}</small><b>{{ count }}</b><em>后端仓库概览快照</em></div><ArrowUpRight class="workbench-card__arrow" :size="16" /></RouterLink>
          </div>
          <ol class="warehouse-process" aria-label="仓库履约阶段">
            <li v-for="(stage, index) in warehouseStages" :key="stage"><span>{{ index + 1 }}</span><b>{{ stage }}</b></li>
          </ol>
          <div class="warehouse-list-grid">
            <div class="warehouse-list-panel"><div class="panel__heading"><div><h2>最近仓库订单</h2><p>来自同一仓库概览响应。</p></div></div><p v-if="!warehouseOverview.recentOrders.length" class="data-state">当前没有待处理业务</p><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>订单号</th><th>顺丰单号</th><th>目的地</th><th>计费重量</th><th>下一步</th></tr></thead><tbody><tr v-for="order in warehouseOverview.recentOrders" :key="order.id"><td><RouterLink class="table-link" :to="`/app/orders?orderId=${order.id}`">{{ order.orderNo }}</RouterLink></td><td>{{ order.trackingNo || '尚未生成' }}</td><td>{{ order.destinationCountry || '—' }}</td><td>{{ formatChargeableWeight(order.chargeableWeight) }}</td><td>{{ order.action || '查看订单' }}</td></tr></tbody></table></div></div>
            <div class="warehouse-list-panel"><div class="panel__heading"><div><h2>轨迹异常</h2><p>仅展示后端标记的轨迹异常。</p></div><RouterLink class="table-link" to="/app/tracking">查看轨迹 <ArrowUpRight :size="15" /></RouterLink></div><p v-if="!warehouseOverview.recentTrackingExceptions.length" class="data-state">暂无轨迹异常</p><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>异常编号</th><th>订单号</th><th>状态</th><th>说明</th><th>发生时间</th></tr></thead><tbody><tr v-for="item in warehouseOverview.recentTrackingExceptions" :key="item.id"><td>{{ item.exceptionNo }}</td><td>{{ item.orderNo || item.orderId }}</td><td>{{ item.status }}</td><td>{{ item.description }}</td><td>{{ formatShanghaiDateTime(item.reportedAt) }}</td></tr></tbody></table></div></div>
          </div>
        </template>
      </div>
    </section>
  </section>
</template>
