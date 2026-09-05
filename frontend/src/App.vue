<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import NotificationHost from '@/components/NotificationHost.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { displayEnumOption } from '@/utils/display'

const inlineEnumLabels: Record<string, string> = {
  reconciliation: '费用对账',
  fee_adjustment: '费用调整',
}

const centeredTableHeaders = new Set([
  '状态', '类型', '责任方', '负责人', '索赔', '财务确认', '版本', '操作',
  '处理状态', '结果', '币种', '费用类型', '平台', '运输方式', '服务国家', '状态变化',
])
const numericTableHeaders = new Set([
  '金额', '系统费用', '供应商费用', '差异金额', '总行数', '成功行数', '错误行数', '重复行数',
])

function translateEnumOptions(root: Node) {
  const options = root instanceof HTMLOptionElement
    ? [root]
    : [...(root as ParentNode).querySelectorAll?.('option') ?? []]
  options.forEach(option => {
    const raw = option.value.trim()
    const label = displayEnumOption(raw)
    if (label && option.textContent?.trim() === raw) option.textContent = label
  })
}

/**
 * Presentation-only guard for the two legacy, user-facing billing type codes.
 * It intentionally changes text nodes only; form values and API payloads remain raw codes.
 */
function translateInlineEnumText(root: Node) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const textNodes: Text[] = []
  let current = walker.nextNode()
  while (current) {
    textNodes.push(current as Text)
    current = walker.nextNode()
  }
  textNodes.forEach(node => {
    const translated = node.data.replace(/\b(reconciliation|fee_adjustment)\b/g, raw => inlineEnumLabels[raw] ?? raw)
    if (translated !== node.data) node.data = translated
  })
}

/** Presentation-only column semantics keep a plain status header aligned with its centered badge cell. */
function applyTableColumnAlignment(root: Node) {
  const nestedTables = [...(root as ParentNode).querySelectorAll?.('table.data-table') ?? []]
  const containingTable = root instanceof HTMLTableElement && root.classList.contains('data-table')
    ? root
    : root.parentElement?.closest<HTMLTableElement>('table.data-table')
  const tables = containingTable && !nestedTables.includes(containingTable)
    ? [containingTable, ...nestedTables]
    : nestedTables
  tables.forEach(table => {
    const headers = [...table.querySelectorAll('thead th')]
    headers.forEach((header, index) => {
      const label = (header.textContent ?? '').replace(/\s+/g, '')
      const kind = numericTableHeaders.has(label) ? 'numeric' : centeredTableHeaders.has(label) ? 'center' : undefined
      if (!kind) return
      header.classList.add(`table-column--${kind}`)
      table.querySelectorAll('tbody tr').forEach(row => row.children[index]?.classList.add(`table-column--${kind}`))
    })
  })
}

function translateEnumPresentation(root: Node) {
  translateEnumOptions(root)
  translateInlineEnumText(root)
  applyTableColumnAlignment(root)
}

let optionObserver: MutationObserver | undefined
onMounted(() => {
  translateEnumPresentation(document)
  optionObserver = new MutationObserver(records => {
    for (const record of records) {
      if (record.type === 'characterData' && record.target.parentNode) {
        translateEnumPresentation(record.target.parentNode)
      }
      for (const node of record.addedNodes) {
        translateEnumPresentation(node)
      }
    }
  })
  optionObserver.observe(document.body, { childList: true, characterData: true, subtree: true })
})
onBeforeUnmount(() => optionObserver?.disconnect())
</script>
<template><RouterView /><NotificationHost /><ConfirmDialog /></template>
