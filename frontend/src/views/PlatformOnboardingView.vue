<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw } from '@lucide/vue'
import ActionError from '@/components/ActionError.vue'
import CopyTextButton from '@/components/CopyTextButton.vue'
import DataState from '@/components/DataState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import * as onboarding from '@/services/onboarding'
import { useSubmit } from '@/composables/useSubmit'
import { useConfirmAction } from '@/composables/useConfirmAction'

const applications = ref<onboarding.OnboardingApplication[]>([])
const loading = ref(false)
const error = ref('')
const errorCode = ref<string>()
const errorStatus = ref<number>()
const errorTraceId = ref<string>()
const selected = ref<onboarding.OnboardingApplication>()
const detail = ref<onboarding.OnboardingApplication>()
const detailLoading = ref(false)
const detailError = ref('')
const reviewRemark = ref('')
const tenantCode = ref('')
const adminUsername = ref('')
const submit = useSubmit()
const { confirm } = useConfirmAction()

async function load() {
  loading.value = true
  error.value = ''
  detail.value = undefined
  detailError.value = ''
  try {
    applications.value = (await onboarding.listOnboardingApplications()).items
  } catch (cause) {
    const converted = toApiError(cause)
    error.value = getApiErrorMessage(converted, '商户入驻申请加载失败。')
    errorCode.value = converted.code
    errorStatus.value = converted.status
    errorTraceId.value = converted.traceId
  } finally {
    loading.value = false
  }
}

function formatDate(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN')
}

function maskPhone(value?: string) {
  const phone = value?.trim()
  if (!phone) return '-'
  if (phone.length <= 7) return '***'
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

onMounted(load)

async function viewDetail(application: onboarding.OnboardingApplication) {
  detailLoading.value = true
  detailError.value = ''
  detail.value = undefined
  try {
    detail.value = await onboarding.getOnboardingApplication(application.id)
  } catch (cause) {
    detailError.value = getApiErrorMessage(cause, '申请详情加载失败，请稍后重试。')
  } finally {
    detailLoading.value = false
  }
}

async function approve(application: onboarding.OnboardingApplication) {
  if (!await confirm({ title: '确认通过入驻申请', description: '通过后将创建正式租户和管理员邀请，请确认企业资料与审核意见无误。', confirmLabel: '审核通过' })) return
  selected.value = application
  await submit.submit(async () => {
    if (!tenantCode.value.trim() || !adminUsername.value.trim() || !reviewRemark.value.trim()) throw new Error('请填写租户编码、管理员用户名和审核意见。')
    const result = await onboarding.approveOnboardingApplication(application.id, { tenantCode: tenantCode.value.trim(), adminUsername: adminUsername.value.trim(), reviewRemark: reviewRemark.value.trim(), version: application.version })
    reviewRemark.value = result.invitationToken ? `审核通过。请通过安全渠道发送一次性邀请令牌：${result.invitationToken}` : '审核通过。邀请已生成。'
    await load()
  })
}

async function reject(application: onboarding.OnboardingApplication) {
  if (!await confirm({ title: '确认驳回入驻申请', description: '驳回原因将保留在审核记录中，请确认原因准确完整。', confirmLabel: '驳回申请', danger: true })) return
  await submit.submit(async () => {
    if (!reviewRemark.value.trim()) throw new Error('请填写驳回原因。')
    await onboarding.rejectOnboardingApplication(application.id, { reviewRemark: reviewRemark.value.trim(), version: application.version })
    await load()
  })
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <span class="kicker">平台运营</span>
        <h1>商户入驻申请</h1>
        <p>审核企业入驻申请；通过后才会创建租户与首个商户管理员。</p>
      </div>
      <button class="btn btn--secondary" type="button" :disabled="loading" @click="load">
        <RefreshCw :size="16" />刷新
      </button>
    </div>
    <ActionError :message="submit.errorMessage.value" :code="submit.errorCode.value" :trace-id="submit.errorTraceId.value" />
    <div class="panel table-panel">
      <DataState :loading="loading" :error="error" :error-code="errorCode" :status="errorStatus" :trace-id="errorTraceId" :empty="!applications.length" empty-title="暂无商户入驻申请">
        <div class="data-table-wrap">
          <table class="data-table data-table--onboarding">
            <thead>
              <tr>
                <th>申请编号</th>
                <th>企业名称</th>
                <th>联系人</th>
                <th>联系电话</th>
                <th>企业邮箱</th>
                <th>国家/地区</th>
                <th>状态</th>
                <th>提交时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="application in applications" :key="application.id">
                <td>{{ application.applicationNo }} <CopyTextButton :value="application.applicationNo" label="申请编号" /></td>
                <td>{{ application.companyName }}</td>
                <td>{{ application.contactName || '-' }}</td>
                <td>{{ maskPhone(application.contactPhone) }}</td>
                <td>{{ application.businessEmail || '-' }}</td>
                <td>{{ application.countryCode }}</td>
                <td><StatusBadge :status="application.status" /></td>
                <td>{{ formatDate(application.createdAt) }}</td>
                <td><button class="table-link" type="button" :disabled="submit.submitting.value" @click="viewDetail(application)">查看详情</button><button v-if="application.status === 'PENDING'" class="table-link" type="button" :disabled="submit.submitting.value" @click="approve(application)">审核通过</button><button v-if="application.status === 'PENDING'" class="table-link" type="button" :disabled="submit.submitting.value" @click="reject(application)">驳回</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </DataState>
    </div>
    <div v-if="detailLoading" class="panel data-state">正在加载申请详情...</div>
    <div v-else-if="detailError" class="alert alert--error" role="alert">{{ detailError }}</div>
    <div v-else-if="detail" class="panel detail-grid">
      <div><small>申请编号</small><span>{{ detail.applicationNo }}</span></div>
      <div><small>企业名称</small><span>{{ detail.companyName }}</span></div>
      <div><small>联系人</small><span>{{ detail.contactName || '-' }}</span></div>
      <div><small>联系电话</small><span>{{ detail.contactPhone || '-' }}</span></div>
      <div><small>企业邮箱</small><span>{{ detail.businessEmail || '-' }}</span></div>
      <div><small>国家/地区</small><span>{{ detail.countryCode }}</span></div>
      <div><small>状态</small><span>{{ detail.status }}</span></div>
      <div><small>审核意见</small><span>{{ detail.reviewRemark || '-' }}</span></div>
      <div><small>提交时间</small><span>{{ formatDate(detail.createdAt) }}</span></div>
      <div><small>审核时间</small><span>{{ formatDate(detail.reviewedAt) }}</span></div>
    </div>
    <div v-else class="panel data-state">请选择一条申请查看详情。</div>
    <div v-if="applications.some(application => application.status === 'PENDING')" class="panel compact-form">
      <h2>审核信息</h2>
      <label><span>租户编码</span><input v-model.trim="tenantCode" maxlength="64" placeholder="审核通过时填写" /></label>
      <label><span>首个管理员用户名</span><input v-model.trim="adminUsername" maxlength="128" placeholder="审核通过时填写" /></label>
      <label><span>审核意见</span><textarea v-model.trim="reviewRemark" maxlength="500" rows="3" placeholder="通过或驳回的原因"></textarea></label>
      <p class="muted">邀请令牌只在审核通过响应中返回一次，请使用安全渠道发送。</p>
    </div>
  </section>
</template>
