import React, { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Navbar from './Navbar'
import { useApp } from '@/context/AppContext'
import { menuList } from '@/menu'
import './Layout.css'

// Mirrors src/layout/index.vue (+ resize mixin)
const Layout: React.FC = () => {
  const { sidebarOpened, device, closeSidebar } = useApp()
  const location = useLocation()

  // afterEach: document.title = route title
  useEffect(() => {
    const hiddenTitles: Record<string, string> = {
      '/dish/add': 'Add Item',
      '/employee/add': 'Add Employee',
      '/setmeal/add': 'Add Combo',
    }
    const item = menuList.find(
      (m) => location.pathname === m.path || location.pathname.startsWith(m.path + '/'),
    )
    document.title = hiddenTitles[location.pathname] || (item ? item.title : 'Sky Take-Out')
  }, [location.pathname])

  const classNames = [
    'app-wrapper',
    sidebarOpened ? 'openSidebar' : 'hideSidebar',
    device === 'mobile' ? 'mobile' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={classNames}>
      {device === 'mobile' && sidebarOpened && (
        <div className="drawer-bg" onClick={closeSidebar} />
      )}
      <Sidebar />
      <div className="main-container">
        <Navbar />
        <section className="app-main">
          <Outlet />
        </section>
      </div>
    </div>
  )
}

export default Layout
