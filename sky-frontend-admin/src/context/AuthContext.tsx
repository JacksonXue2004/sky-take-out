import React, { createContext, useCallback, useContext, useMemo, useState } from 'react'
import Cookies from 'js-cookie'
import { message } from 'antd'
import { login as loginApi, userLogout } from '@/api/employee'
import { getToken, setToken, removeToken } from '@/utils/cookies'

interface UserInfo {
  id?: number
  userName?: string
  name?: string
  token?: string
  [k: string]: any
}

interface AuthContextValue {
  token: string
  userInfo: UserInfo
  name: string
  login: (payload: { username: string; password: string }) => Promise<any>
  logout: () => Promise<void>
}

function readUserInfo(): UserInfo {
  // The Vue app stored the login payload under cookie `user_info` (JSON).
  try {
    const raw = Cookies.get('user_info')
    if (raw) return JSON.parse(raw)
  } catch {
    /* ignore */
  }
  return {}
}

const AuthContext = createContext<AuthContextValue>(null as any)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setTokenState] = useState<string>(getToken() || '')
  const [userInfo, setUserInfo] = useState<UserInfo>(readUserInfo())

  const login = useCallback(async (payload: { username: string; password: string }) => {
    const username = (payload.username || '').trim()
    const password = payload.password
    setUserInfo((u) => ({ ...u }))
    Cookies.set('username', username)
    const res = await loginApi({ username, password })
    const data = res.data
    if (String(data.code) === '1') {
      setToken(data.data.token)
      setTokenState(data.data.token)
      setUserInfo(data.data)
      Cookies.set('user_info', JSON.stringify(data.data))
      return data
    } else {
      message.error(data.msg)
      return data
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await userLogout({})
    } catch {
      /* ignore network errors on logout, still clear locally */
    }
    removeToken()
    setTokenState('')
    setUserInfo({})
    Cookies.remove('username')
    Cookies.remove('user_info')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      userInfo,
      name: userInfo?.name === '\u7ba1\u7406\u5458' ? 'Administrator' : (userInfo?.name || ''),
      login,
      logout,
    }),
    [token, userInfo, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
