const fieldLabels: Record<string, string> = {
  id: '编号', quoteNo: '报价单号', orderNo: '订单号', storeId: '店铺', channelId: '物流渠道',
  destinationCountry: '目的国家/地区', originCountry: '起运国家/地区', ruleVersionNo: '规则版本',
  declaredWeight: '申报重量（kg）', declaredLength: '申报长度（cm）', declaredWidth: '申报宽度（cm）',
  declaredHeight: '申报高度（cm）', declaredVolumeWeight: '体积重量（kg）', chargeableWeight: '计费重量（kg）',
  volumeWeight: '体积重量（kg）', volumeDivisor: '体积系数', roundingMode: '重量进位方式', roundingIncrement: '进位单位（kg）',
  priceRuleId: '价格规则编号', tierNo: '适用阶梯', declaredChargeableWeight: '计费重量（kg）',
  estimatedFee: '预估费用', currentFee: '当前费用', confirmedFee: '确认费用', currency: '币种',
  status: '状态', currentStatus: '订单状态', version: '版本号', createdAt: '创建时间', updatedAt: '更新时间',
  validFrom: '生效时间（北京时间）', validTo: '有效期至（北京时间）', providerId: '物流商', providerName: '物流商名称',
  providerCode: '物流商编码', channelName: '渠道名称', channelCode: '渠道编码', transportMode: '运输方式',
  serviceArea: '服务区域', trackingNo: '物流单号', eventCode: '轨迹节点', eventTime: '发生时间',
  description: '说明', processStatus: '处理状态', exceptionNo: '异常单号', exceptionType: '异常类型',
  reconciliationStatus: '对账状态', billBatchNo: '账单批次号', applicationNo: '申请编号',
  batchNo: '批次号', fileName: '文件名', totalCount: '总行数', successCount: '成功行数',
  failureCount: '错误行数', duplicateCount: '重复行数', importedAt: '导入时间',
  detailStatus: '明细状态', errorHandlingStatus: '错误处理状态', systemAmount: '系统费用',
  billedAmount: '供应商费用', differenceAmount: '差异金额', differenceReason: '差异原因',
  responsibleUserId: '负责人', claimStatus: '索赔状态', responsibleParty: '责任方',
  tenantId: '租户编号', operatorUserId: '操作人编号', actionType: '操作类型',
  resourceType: '业务对象', resourceId: '业务对象编号', resultStatus: '处理结果',
}

const countries: Record<string, string> = {
  CN: '中国（CN）', US: '美国（US）', CA: '加拿大（CA）', AU: '澳大利亚（AU）', JP: '日本（JP）',
  KR: '韩国（KR）', GB: '英国（GB）', DE: '德国（DE）', FR: '法国（FR）', IT: '意大利（IT）', ES: '西班牙（ES）',
}

const statuses: Record<string, string> = {
  ACTIVE: '启用', DISABLED: '停用', DRAFT: '草稿', VALID: '有效', EXPIRED: '已过期',
  PENDING_INBOUND: '待入库', INBOUND: '已入库', PENDING_PRICE_CONFIRMATION: '待确认费用',
  READY_FOR_OUTBOUND: '待出库', OUTBOUND: '已出库', IN_TRANSIT: '运输中', DELIVERED: '已签收',
  CANCELLED: '已取消', RETURNED: '已退回', LOST: '遗失', OPEN: '待处理', ASSIGNED: '已分配',
  PROCESSING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭', SUCCESS: '成功', FAILED: '失败',
  PENDING: '待处理', CONFIRMED: '已确认', ACCEPT: '接受', REJECT: '拒绝',
  WAITING_PROVIDER_FEEDBACK: '待物流商反馈', PENDING_FINANCE_CONFIRMATION: '待财务确认',
  SUBMITTED: '已提交', APPROVED: '已批准', PARTIALLY_APPROVED: '部分批准',
  PARTIAL_SUCCESS: '部分成功', COMPLETED: '已完成', AUTO_CLOSED: '自动完成',
  PENDING_CONFIRMATION: '待确认', IMPORTED: '已导入', MATCHED: '已匹配', ERROR: '错误',
  INACTIVE: '停用', NORMAL: '正常', WARNING: '预警', LABEL_READY: '面单已就绪',
  IGNORED: '已忽略', NOT_APPLICABLE: '不适用',
}

const transportModes: Record<string, string> = { AIR: '空运', SEA: '海运', LAND: '陆运', RAIL: '铁路', EXPRESS: '快递', MULTIMODAL: '多式联运' }
const roundingModes: Record<string, string> = { CEILING: '向上进位', ROUND: '四舍五入', NONE: '不进位' }
const exceptionTypes: Record<string, string> = { TRANSPORT: '运输', ADDRESS: '地址', CUSTOMS: '海关', OTHER: '其他' }
const responsibleParties: Record<string, string> = { PROVIDER: '物流商', MERCHANT: '商户', CUSTOMS: '海关', CUSTOMER: '客户' }
const platforms: Record<string, string> = { MARKETPLACE_A: '平台 A' }
const feeTypes: Record<string, string> = {
  RECONCILIATION: '费用对账', reconciliation: '费用对账',
  FEE_ADJUSTMENT: '费用调整', fee_adjustment: '费用调整',
  FREIGHT: '运费', INVALID: '无效费用类型',
}
const actions: Record<string, string> = { CREATE: '创建', UPDATE: '更新', DELETE: '删除', STATUS_CHANGE: '状态变更', PERMISSION_BIND: '权限绑定', LOGIN: '登录', LOGOUT: '退出登录', IMPORT: '导入', CONFIRM: '确认', SUBMIT: '提交', CANCEL: '取消' }
const resourceTypes: Record<string, string> = { QUOTE: '报价单', SHIPMENT_ORDER: '物流订单', ORDER: '订单', TENANT: '租户', STORE: '店铺', USER: '用户', ROLE: '角色', PERMISSION: '权限', EXCEPTION: '异常单', CLAIM: '索赔单', BILL_IMPORT_BATCH: '账单导入批次', RECONCILIATION: '对账记录', LOGISTICS_CHANNEL: '物流渠道', PRICE_RULE: '价格规则' }

const unknownEnumDiagnostics = new Set<string>()

function recordUnknownEnum(key: string, value: unknown) {
  const entry = `${key}:${String(value)}`
  if (unknownEnumDiagnostics.has(entry)) return
  unknownEnumDiagnostics.add(entry)
  if (import.meta.env.DEV) console.warn('[ShipFlow][display] 未映射枚举', { key, value })
}

function displayEnum(key: string, value: unknown, map: Record<string, string>, fallback = '未知状态') {
  const normalized = String(value ?? '').trim()
  if (!normalized) return '未配置'
  const label = map[normalized] ?? map[normalized.toUpperCase()]
  if (label) return label
  recordUnknownEnum(key, normalized)
  return fallback
}

export function displayLabel(key: string) { return fieldLabels[key] ?? '未配置' }
export function getUnknownEnumDiagnostics() { return [...unknownEnumDiagnostics] }

/** Converts a raw enum option to Chinese while preserving its option value for API submission. */
export function displayEnumOption(value: unknown) {
  const raw = String(value ?? '').trim()
  if (!raw) return undefined
  const maps = [statuses, exceptionTypes, responsibleParties, platforms, feeTypes, transportModes, roundingModes]
  for (const map of maps) {
    const label = map[raw] ?? map[raw.toUpperCase()]
    if (label) return label
  }
  if (/^[A-Z][A-Z0-9_]*$/.test(raw) || /^[a-z]+(?:_[a-z]+)+$/.test(raw)) {
    recordUnknownEnum('option', raw)
    return '未知状态'
  }
  return undefined
}

export function formatChargeableWeight(value: unknown) {
  if (value == null || value === '') return '—'
  const grams = Number(value)
  return Number.isFinite(grams) ? `${(grams / 1000).toFixed(2)} kg` : String(value)
}

export function formatQuantity(value: unknown, unit = '') {
  if (value == null || value === '') return '—'
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(numeric)}${unit ? ` ${unit}` : ''}` : '未配置'
}

export function formatMoney(value: unknown, currency?: unknown) {
  if (value == null || value === '') return '—'
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '未配置'
  const code = typeof currency === 'string' && /^[A-Z]{3}$/.test(currency) ? currency : undefined
  const amount = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(numeric)
  return code ? `${code} ${amount}` : amount
}

/** Translates safe, known bill-import validation messages without changing API payloads. */
export function formatBillingErrorReason(value: unknown) {
  const raw = String(value ?? '').trim()
  if (!raw) return '—'
  if (/^[\u4e00-\u9fff]/.test(raw)) return raw

  const duplicate = raw.match(/^Duplicate provider bill detail number:\s*(.+)$/i)
  if (duplicate) return `物流商账单明细号重复：${duplicate[1]}`
  if (/^Invalid billed amount$/i.test(raw)) return '账单金额无效'
  if (/^Shipment order not found for provider tracking number$/i.test(raw)) return '未找到对应物流单号的订单'
  if (/^Bill currency does not match order currency$/i.test(raw)) return '账单币种与订单币种不一致'

  recordUnknownEnum('billingErrorReason', raw)
  return '账单导入校验失败（原因未配置）'
}

function formatBeijingDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date).replace(/\//g, '-')
}

export function displayValue(key: string, value: unknown) {
  if (value == null || value === '') return '—'
  if (typeof value === 'string' && ['validFrom', 'validTo', 'createdAt', 'updatedAt', 'eventTime', 'occurredAt', 'reportedAt', 'resolvedAt', 'importedAt', 'confirmedAt', 'financeConfirmedAt'].includes(key)) return formatBeijingDateTime(value)
  if (key === 'chargeableWeight') return formatChargeableWeight(value)
  if (key === 'originCountry' || key === 'destinationCountry' || key.endsWith('Country')) return displayEnum(key, value, countries, '未配置')
  if (key === 'transportMode') return displayEnum(key, value, transportModes, '未配置')
  if (key === 'roundingMode') return displayEnum(key, value, roundingModes, '未配置')
  if (key === 'exceptionType') return displayEnum(key, value, exceptionTypes, '未配置')
  if (key === 'responsibleParty') return displayEnum(key, value, responsibleParties, '未配置')
  if (key === 'platformCode') return displayEnum(key, value, platforms, '未配置')
  if (key === 'feeType') return displayEnum(key, value, feeTypes, '未配置')
  if (key === 'errorMessage') return formatBillingErrorReason(value)
  if (key === 'actionType') return displayEnum(key, value, actions, '未配置')
  if (key === 'resourceType') return displayEnum(key, value, resourceTypes, '未配置')
  if (key === 'status' || key === 'currentStatus' || key.endsWith('Status') || key === 'processStatus') return displayEnum(key, value, statuses)
  if (Array.isArray(value)) return String(value.length)
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
