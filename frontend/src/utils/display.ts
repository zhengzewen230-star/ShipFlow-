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
  PROCESSING: '处理中', RESOLVED: '已处理', CLOSED: '已关闭', SUCCESS: '成功', FAILED: '失败',
  PENDING: '待处理', CONFIRMED: '已确认', ACCEPT: '接受', REJECT: '拒绝',
}

const transportModes: Record<string, string> = { AIR: '空运', SEA: '海运', LAND: '陆运', RAIL: '铁路', EXPRESS: '快递', MULTIMODAL: '多式联运' }
const roundingModes: Record<string, string> = { CEILING: '向上进位', ROUND: '四舍五入', NONE: '不进位' }
const actions: Record<string, string> = { CREATE: '创建', UPDATE: '更新', DELETE: '删除', STATUS_CHANGE: '状态变更', PERMISSION_BIND: '权限绑定', LOGIN: '登录', LOGOUT: '退出登录', IMPORT: '导入', CONFIRM: '确认', SUBMIT: '提交', CANCEL: '取消' }
const resourceTypes: Record<string, string> = { QUOTE: '报价单', SHIPMENT_ORDER: '物流订单', ORDER: '订单', TENANT: '租户', STORE: '店铺', USER: '用户', ROLE: '角色', PERMISSION: '权限', EXCEPTION: '异常单', CLAIM: '索赔单', BILL_IMPORT_BATCH: '账单导入批次', RECONCILIATION: '对账记录', LOGISTICS_CHANNEL: '物流渠道', PRICE_RULE: '价格规则' }

export function displayLabel(key: string) { return fieldLabels[key] ?? key }

export function formatChargeableWeight(value: unknown) {
  if (value == null || value === '') return '—'
  const grams = Number(value)
  return Number.isFinite(grams) ? `${(grams / 1000).toFixed(2)} kg` : String(value)
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
  if (typeof value === 'string' && ['validFrom', 'validTo', 'createdAt', 'updatedAt', 'eventTime', 'occurredAt', 'reportedAt', 'resolvedAt', 'importedAt', 'confirmedAt'].includes(key)) return formatBeijingDateTime(value)
  if (key === 'chargeableWeight') return formatChargeableWeight(value)
  if (key === 'originCountry' || key === 'destinationCountry' || key.endsWith('Country')) return countries[String(value)] ?? String(value)
  if (key === 'transportMode') return transportModes[String(value)] ?? String(value)
  if (key === 'roundingMode') return roundingModes[String(value)] ?? String(value)
  if (key === 'actionType') return actions[String(value)] ?? String(value)
  if (key === 'resourceType') return resourceTypes[String(value)] ?? String(value)
  if (key === 'status' || key === 'currentStatus' || key.endsWith('Status') || key === 'processStatus') return statuses[String(value)] ?? String(value)
  if (Array.isArray(value)) return String(value.length)
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
