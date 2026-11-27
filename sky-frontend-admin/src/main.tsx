import React from 'react'
import ReactDOM from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import enUS from 'antd/locale/en_US'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { AppProvider } from './context/AppContext'
import './styles/iconfont.css'
import './styles/global.css'

ReactDOM.createRoot(document.getElementById('app')!).render(
  <ConfigProvider locale={enUS} theme={{ token: { colorPrimary: '#409EFF' } }}>
    <HashRouter>
      <AuthProvider>
        <AppProvider>
          <App />
        </AppProvider>
      </AuthProvider>
    </HashRouter>
  </ConfigProvider>,
)
