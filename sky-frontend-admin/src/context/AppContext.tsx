import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

interface AppContextValue {
  sidebarOpened: boolean
  device: 'desktop' | 'mobile'
  toggleSidebar: () => void
  closeSidebar: () => void
}

const WIDTH = 992

const AppContext = createContext<AppContextValue>(null as any)

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [sidebarOpened, setSidebarOpened] = useState(true)
  const [device, setDevice] = useState<'desktop' | 'mobile'>('desktop')

  const isMobile = () => document.body.getBoundingClientRect().width - 1 < WIDTH

  useEffect(() => {
    const onResize = () => {
      if (!document.hidden) {
        const mobile = isMobile()
        setDevice(mobile ? 'mobile' : 'desktop')
        if (mobile) setSidebarOpened(false)
      }
    }
    // initial
    if (isMobile()) {
      setDevice('mobile')
      setSidebarOpened(false)
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const toggleSidebar = useCallback(() => setSidebarOpened((v) => !v), [])
  const closeSidebar = useCallback(() => setSidebarOpened(false), [])

  const value = useMemo<AppContextValue>(
    () => ({ sidebarOpened, device, toggleSidebar, closeSidebar }),
    [sidebarOpened, device, toggleSidebar, closeSidebar],
  )

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

export const useApp = () => useContext(AppContext)
