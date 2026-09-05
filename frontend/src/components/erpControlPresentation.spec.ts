import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const styles = read('../styles/main.css')
const statusBadge = read('./StatusBadge.vue')
const app = read('../App.vue')
const finance = read('../views/FinanceView.vue')

describe('enterprise control presentation', () => {
  it('gives console controls a shared non-native button and focus treatment', () => {
    expect(styles).toContain('Enterprise control system')
    expect(styles).toContain('.app-content .table-sort')
    expect(styles).toContain('.app-content .table-link')
    expect(styles).toContain('.app-content .tab-button')
    expect(styles).toContain('.app-content .btn:focus-visible')
    expect(styles).toContain('.table-pagination')
  })

  it('keeps every list header and filter control on the shared alignment system', () => {
    expect(styles).toContain('Shared list surface: header and filter alignment')
    expect(styles).toContain('--table-header-height: 42px')
    expect(styles).toContain('.data-table th .table-sort')
    expect(styles).toContain("content: '↕'")
    expect(styles).toContain('background: transparent')
    expect(styles).toContain('text-align-last: center')
    expect(styles).toContain('--filter-control-height: 38px')
    expect(styles).toContain('td:has(.status-badge)')
  })

  it('uses a shared fixed table model so headers and data cells cannot calculate separate widths', () => {
    expect(styles).toContain('table-layout: fixed')
    expect(styles).toContain('box-sizing: border-box')
    expect(styles).toContain('text-overflow: ellipsis')
    expect(styles).toContain('Keep copy affordances out of column-width calculation')
    expect(styles).toContain('.exception-page .data-table')
  })

  it('keeps status text accessible and delegates enum labels to the common formatter', () => {
    expect(statusBadge).toContain("displayValue('status', raw)")
    expect(statusBadge).toContain('role="status"')
    expect(statusBadge).toContain('aria-label')
  })

  it('translates legacy visible enum text without changing form option values', () => {
    expect(app).toContain("import { displayEnumOption } from '@/utils/display'")
    expect(app).toContain('option.value.trim()')
    expect(app).toContain('option.textContent = label')
    expect(app).toContain("fee_adjustment: '费用调整'")
    expect(app).toContain('characterData: true')
  })

  it('applies shared semantic alignment to plain status headers and their data cells', () => {
    expect(app).toContain('applyTableColumnAlignment')
    expect(app).toContain("'状态', '类型', '责任方', '负责人'")
    expect(app).toContain('table-column--${kind}')
    expect(styles).toContain('.table-column--center')
    expect(styles).toContain('.table-column--numeric')
  })

  it('routes billing detail enums and import errors through the shared display formatter', () => {
    expect(finance).toContain("format('feeType', detail.feeType)")
    expect(finance).toContain("format('errorMessage', detail.errorMessage)")
    expect(finance).toContain("format('errorHandlingStatus', detail.errorHandlingStatus)")
    expect(finance).not.toContain('{{ detail.errorMessage || \'-\' }}')
    expect(finance).not.toContain('{{ detail.errorHandlingStatus || \'-\' }}')
  })
})
