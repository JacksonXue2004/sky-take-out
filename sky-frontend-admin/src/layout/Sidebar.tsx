import React from 'react'
import { ConfigProvider, Menu } from 'antd'
import { AppstoreOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { useApp } from '@/context/AppContext'
import { menuList } from '@/menu'
import logo from '@/assets/logo.png'
import miniLogo from '@/assets/mini-logo.png'

// Mirrors src/layout/components/Sidebar/*
const renderIcon = (icon: string) => {
  if (icon === 'dashboard') return <AppstoreOutlined />
  return <i className={'iconfont ' + icon} />
}

const Sidebar: React.FC = () => {
  const { sidebarOpened } = useApp()
  const location = useLocation()
  const navigate = useNavigate()

  const isCollapse = !sidebarOpened

  // active menu = the item whose path matches (or is a prefix of) the current path
  const selected =
    menuList.find(
      (m) => location.pathname === m.path || location.pathname.startsWith(m.path + '/'),
    )?.path || location.pathname

  const items = menuList.map((m) => ({
    key: m.path,
    icon: renderIcon(m.icon),
    label: m.title,
  }))

  return (
    <div className="sidebar-container">
      <div className="logo">
        <img src={isCollapse ? miniLogo : logo} alt="logo" />
      </div>
      <ConfigProvider
        theme={{
          components: {
            Menu: {
              darkItemBg: '#343744',
              darkSubMenuItemBg: '#2b2e39',
              darkPopupBg: '#343744',
              darkItemColor: '#bfcbd9',
              darkItemHoverColor: '#ffffff',
              darkItemSelectedBg: '#2a2d38',
              darkItemSelectedColor: '#ffc200',
            },
          },
        }}
      >
        <Menu
          theme="dark"
          mode="inline"
          inlineCollapsed={isCollapse}
          selectedKeys={[selected]}
          items={items}
          onClick={({ key }) => navigate(key)}
          style={{ border: 'none', background: '#343744' }}
        />
      </ConfigProvider>
    </div>
  )
}

export default Sidebar
