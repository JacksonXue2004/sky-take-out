// Sidebar menu — mirrors the visible Layout children in the original router.ts
// (order preserved). `icon` is either an iconfont class (icon-*) or a special key.
export interface MenuItem {
  path: string
  title: string
  icon: string
}

export const menuList: MenuItem[] = [
  { path: '/dashboard', title: 'Dashboard', icon: 'dashboard' },
  { path: '/statistics', title: 'Analytics', icon: 'icon-statistics' },
  { path: '/order', title: 'Order Management', icon: 'icon-order' },
  { path: '/setmeal', title: 'Combo Management', icon: 'icon-combo' },
  { path: '/dish', title: 'Item Management', icon: 'icon-dish' },
  { path: '/category', title: 'Category Management', icon: 'icon-category' },
  { path: '/employee', title: 'Employee Management', icon: 'icon-employee' },
]
