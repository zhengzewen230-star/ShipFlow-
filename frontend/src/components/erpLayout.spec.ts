import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (name: string) => readFileSync(new URL(name, import.meta.url), 'utf8')
const layout = read('../layouts/AppLayout.vue')
const shell = read('./AppShell.vue')
const topBar = read('./TopBar.vue')
const tabs = read('./WorkspaceTabs.vue')
const pageHeader = read('./PageHeader.vue')
const styles = read('../styles/main.css')

describe('enterprise ERP application shell', () => {
  it('composes the shared shell instead of duplicating page navigation', () => {
    expect(layout).toContain('<AppShell>')
    for (const component of ['Sidebar', 'TopBar', 'WorkspaceTabs', 'PageHeader']) expect(shell).toContain(`<${component}`)
    expect(shell).toContain('id="main-content"')
    expect(shell).toContain('跳到主要内容')
    expect(shell).toContain('@keydown.esc')
    expect(topBar).toContain('@keydown.enter.prevent')
    expect(topBar).toContain('@keydown.space.prevent')
    expect(topBar).toContain('@keydown.enter.prevent="search"')
  })

  it('provides scope, timezone, global search, notifications and account controls', () => {
    expect(topBar).toContain('当前数据范围')
    expect(topBar).toContain('Asia/Shanghai')
    expect(topBar).toContain('role="search"')
    expect(topBar).toContain('通知中心')
    expect(topBar).toContain('aria-haspopup="menu"')
  })

  it('persists at most eight deep-link-safe workspace tabs', () => {
    expect(tabs).toContain("sessionStorage.getItem(STORAGE_KEY)")
    expect(tabs).toContain('next.slice(-8)')
    expect(tabs).toContain(':to="tab.path"')
  })

  it('exposes breadcrumbs and current URL filter context', () => {
    expect(pageHeader).toContain('aria-label="面包屑"')
    expect(pageHeader).toContain('Object.entries(route.query)')
    expect(pageHeader).toContain('当前筛选')
  })

  it('keeps keyboard focus and mobile navigation visible', () => {
    expect(styles).toContain(':focus-visible')
    expect(styles).toContain('@media (max-width:720px)')
    expect(styles).toContain('.navigation-open .sidebar')
  })
})
