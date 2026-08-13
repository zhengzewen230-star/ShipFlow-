<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowUpRight, CircleAlert, ClipboardList, PackageCheck, Route } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { getApiErrorMessage } from '@/services/http'
import { getOperationsSummary, getOperationsTodos, type OperationsSummary, type OperationsTodos } from '@/services/operations'

const summary = ref<OperationsSummary>({}); const todos = ref<OperationsTodos>({}); const loading = ref(false); const error = ref('')
const metrics = computed(() => [[ClipboardList, '草稿订单', summary.value.draft ?? 0, '待继续完善'], [PackageCheck, '待入库', summary.value.pendingInbound ?? 0, '等待仓库接收'], [Route, '运输中', summary.value.inTransit ?? 0, '正在跨境运输'], [CircleAlert, '异常待办', todos.value.activeExceptions ?? 0, '需要团队关注']] as const)
async function load() { loading.value = true; error.value = ''; try { [summary.value, todos.value] = await Promise.all([getOperationsSummary(), getOperationsTodos()]) } catch (cause) { error.value = getApiErrorMessage(cause, '运营概览加载失败。') } finally { loading.value = false } }
onMounted(load)
</script>
<template><section class="dashboard"><div class="page-heading"><div><span class="kicker">工作台</span><h1>运营概览</h1><p>汇总当前租户的履约状态与跨域待办。</p></div><RouterLink class="btn btn--primary" to="/quote">新建报价 <ArrowUpRight :size="17" /></RouterLink></div><DataState :loading="loading" :error="error"><div class="metric-grid"><article v-for="([icon,label,value,note]) in metrics" :key="label"><span><component :is="icon" :size="21" /></span><div><small>{{ label }}</small><b>{{ value }}</b><em>{{ note }}</em></div></article></div><div class="panel todo-panel"><h2>待办事项</h2><div class="detail-grid"><div><small>待确认费用</small><span>{{ todos.pendingPriceConfirmation ?? 0 }}</span></div><div><small>待确认对账</small><span>{{ todos.pendingReconciliation ?? 0 }}</span></div><div><small>已提交索赔</small><span>{{ todos.submittedClaims ?? 0 }}</span></div></div></div></DataState></section></template>
