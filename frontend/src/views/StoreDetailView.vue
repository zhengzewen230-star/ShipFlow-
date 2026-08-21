<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { getStore, type StoreDetail } from '@/services/stores'
import { parseStoreId, storeDetailUnavailable, storeListBackQuery } from './storeDetail'

const route = useRoute()
const router = useRouter()
const detail = ref<StoreDetail>()
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const invalidId = computed(() => !parseStoreId(route.params.storeId))
const resourceFields = ['countryRegion', 'defaultShippingAddress', 'defaultLogisticsChannel'] as const
const unconfiguredResourceFields = computed(() => {
  const value = detail.value
  return value
    ? resourceFields.filter(field => value[field] == null && !value.unavailableFields.includes(field))
    : []
})
const unavailableResourceFields = computed(() => {
  const value = detail.value
  return value ? resourceFields.filter(field => value.unavailableFields.includes(field)) : []
})

async function load() {
  const storeId = parseStoreId(route.params.storeId)
  if (!storeId) {
    error.value = '店铺编号无效，无法加载详情。'
    errorTraceId.value = undefined
    return
  }
  if (loading.value) return
  loading.value = true
  error.value = ''
  errorTraceId.value = undefined
  try {
    detail.value = await getStore(storeId)
  } catch (cause) {
    detail.value = undefined
    const apiError = toApiError(cause)
    error.value = getApiErrorMessage(apiError, '店铺详情加载失败，请稍后重试。')
    errorTraceId.value = apiError.traceId
  } finally {
    loading.value = false
  }
}

function backToList() {
  void router.push({ name: 'app-stores', query: storeListBackQuery(route.query) })
}

function formatDate(value: string | null) {
  if (!value) return '未提供'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '时间格式无效' : date.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}

function auditActionLabel(value: string) {
  return ({ CREATE: '创建', UPDATE: '修改', STATUS_CHANGE: '状态变更' } as Record<string, string>)[value] ?? value
}

onMounted(() => { void load() })
watch(() => route.params.storeId, () => { void load() })
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="kicker">业务资料</span><h1>店铺详情</h1><p>仅展示当前账号有权访问的店铺信息，平台账号已脱敏。</p></div>
      <div class="page-actions">
        <button class="btn btn--secondary" type="button" @click="backToList"><ArrowLeft :size="16" />返回店铺列表</button>
        <button class="btn btn--secondary" type="button" :disabled="loading || invalidId" @click="load"><RefreshCw :size="16" />{{ loading ? '刷新中' : '刷新' }}</button>
      </div>
    </div>
    <div class="panel table-panel">
      <DataState :loading="loading" :error="error" :trace-id="errorTraceId" :retry="load" :empty="!detail && !error && !invalidId">
        <template v-if="detail">
          <div class="detail-grid">
            <div><span>店铺编码</span><strong>{{ detail.storeCode }}</strong></div>
            <div><span>店铺名称</span><strong>{{ detail.storeName }}</strong></div>
            <div><span>平台</span><strong>{{ detail.platformCode }}</strong></div>
            <div><span>平台账号</span><strong>{{ detail.platformAccountMasked }}</strong></div>
            <div><span>国家/地区</span><strong>{{ detail.countryRegion ?? storeDetailUnavailable(detail.unavailableFields, 'countryRegion') }}</strong></div>
            <div><span>默认发货地址</span><strong>{{ detail.defaultShippingAddress ?? storeDetailUnavailable(detail.unavailableFields, 'defaultShippingAddress') }}</strong></div>
            <div><span>默认物流渠道</span><strong>{{ detail.defaultLogisticsChannel ?? storeDetailUnavailable(detail.unavailableFields, 'defaultLogisticsChannel') }}</strong></div>
            <div><span>状态</span><strong>{{ detail.status === 'ACTIVE' ? '启用' : '停用' }}</strong></div>
            <div><span>版本</span><strong>{{ detail.version }}</strong></div>
            <div><span>历史订单数</span><strong>{{ detail.historicalOrderCount }}</strong></div>
            <div><span>最近更新时间</span><strong>{{ formatDate(detail.updatedAt) }}</strong></div>
          </div>
          <p v-if="unconfiguredResourceFields.length" class="muted">标记为“尚未配置”的店铺资源由租户管理员配置。</p>
          <p v-if="unavailableResourceFields.length" class="muted">标记为“功能暂不可用”的字段尚未由当前后端能力提供。</p>
          <div class="panel"><h2>变更审计摘要</h2><p v-if="!detail.auditSummary.length">暂无审计记录</p><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>操作</th><th>结果</th><th>发生时间（上海）</th></tr></thead><tbody><tr v-for="(item, index) in detail.auditSummary" :key="`${item.occurredAt}-${index}`"><td>{{ auditActionLabel(item.actionType) }}</td><td>{{ item.resultStatus === 'SUCCESS' ? '成功' : item.resultStatus }}</td><td>{{ formatDate(item.occurredAt) }}</td></tr></tbody></table></div></div>
        </template>
      </DataState>
    </div>
  </section>
</template>
