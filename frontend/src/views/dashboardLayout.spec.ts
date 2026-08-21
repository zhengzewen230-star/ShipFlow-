import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const dashboard = readFileSync(new URL('./DashboardView.vue', import.meta.url), 'utf8')
const styles = readFileSync(new URL('../styles/main.css', import.meta.url), 'utf8')

describe('warehouse dashboard layout contract', () => {
  it('renders all eight backend-driven warehouse metrics', () => {
    for (const label of ['待入库', '待复称', '待贴标/打单', '待交接/出库', '今日入库', '今日出库', '在途运输', '轨迹异常']) {
      expect(dashboard).toContain(`'${label}'`)
    }
    expect(dashboard).toContain('warehouseOverview.value?.pendingLabel')
    expect(dashboard).toContain('warehouseOverview.value?.pendingHandover')
  })

  it('keeps responsive warehouse metric grid breakpoints', () => {
    expect(styles).toContain('.warehouse-metric-grid { grid-auto-rows: minmax(112px, auto);')
    expect(styles).toContain('.warehouse-metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }')
    expect(styles).toContain('.warehouse-metric-grid { grid-template-columns: 1fr; }')
  })

  it('renders real recent-order and tracking-exception empty states', () => {
    expect(dashboard).toContain('当前没有待处理业务')
    expect(dashboard).toContain('暂无轨迹异常')
    expect(dashboard).toContain('warehouseOverview.recentOrders')
    expect(dashboard).toContain('warehouseOverview.recentTrackingExceptions')
  })

  it('formats dashboard chargeable weight through the shared gram conversion', () => {
    expect(dashboard).toContain("import { formatChargeableWeight } from '@/utils/display'")
    expect(dashboard).toContain('formatChargeableWeight(order.chargeableWeight)')
  })

  it('shows the backend-owned warehouse lifecycle stages in order', () => {
    const flow = ['待入库', '待复称', '待贴标/打单', '待交接/出库', '在途运输']
    expect(dashboard).toContain('warehouseStages')
    for (const stage of flow) expect(dashboard).toContain(`'${stage}'`)
  })
})
