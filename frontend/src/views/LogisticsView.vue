<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { useAuthStore } from '@/stores/auth'
import { getApiErrorMessage } from '@/services/http'
import * as logistics from '@/services/logistics'

const auth = useAuthStore()
const providers = ref<logistics.LogisticsProvider[]>([])
const channels = ref<logistics.LogisticsChannel[]>([])
const loading = ref(false); const error = ref('')
async function load() { loading.value = true; error.value = ''; try { if (auth.scope === 'PLATFORM') { const [p, c] = await Promise.all([logistics.listLogisticsProviders(), logistics.listLogisticsChannels()]); providers.value = p.items; channels.value = c.items } else { channels.value = (await logistics.listAvailableLogisticsChannels()).items } } catch (cause) { error.value = getApiErrorMessage(cause, '物流基础资料加载失败。') } finally { loading.value = false } }
onMounted(load)
</script>
<template><section><div class="page-heading"><div><span class="kicker">物流基础资料</span><h1>{{ auth.scope === 'PLATFORM' ? '物流商与渠道' : '可用物流渠道' }}</h1><p>{{ auth.scope === 'PLATFORM' ? '维护公共物流商、渠道、服务国家和已发布价格规则。' : '查看当前租户可使用的公共物流渠道。' }}</p></div><button class="btn btn--secondary" @click="load"><RefreshCw :size="16" />刷新</button></div><DataState :loading="loading" :error="error" :empty="!providers.length && !channels.length"><div v-if="providers.length" class="panel table-panel"><h2>物流商</h2><div class="data-table-wrap"><table class="data-table"><thead><tr><th>编码</th><th>名称</th><th>状态</th></tr></thead><tbody><tr v-for="item in providers" :key="item.id"><td>{{ item.providerCode }}</td><td>{{ item.providerName }}</td><td>{{ item.status }}</td></tr></tbody></table></div></div><div class="panel table-panel"><h2>物流渠道</h2><div class="data-table-wrap"><table class="data-table"><thead><tr><th>编码</th><th>名称</th><th>运输方式</th><th>服务区域</th><th>状态</th></tr></thead><tbody><tr v-for="item in channels" :key="item.id"><td>{{ item.channelCode }}</td><td>{{ item.channelName }}</td><td>{{ item.transportMode }}</td><td>{{ item.serviceArea }}</td><td>{{ item.status }}</td></tr></tbody></table></div></div></DataState></section></template>
