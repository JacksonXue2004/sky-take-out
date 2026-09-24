import Cookies from 'js-cookie'

// App
const sidebarStatusKey = 'sidebar_status'
export const getSidebarStatus = () => Cookies.get(sidebarStatusKey)
export const setSidebarStatus = (v: string) => Cookies.set(sidebarStatusKey, v)

// storeId
const storeIdKey = 'storeId'
export const getStoreId = () => Cookies.get(storeIdKey)
export const setStoreId = (id: string) => Cookies.set(storeIdKey, id)
export const removeStoreId = () => Cookies.remove(storeIdKey)

// token (admin JWT) — sent as request header "token"
const tokenKey = 'token'
export const getToken = () => Cookies.get(tokenKey)
export const setToken = (token: string) => Cookies.set(tokenKey, token)
export const removeToken = () => Cookies.remove(tokenKey)

// userInfo
const userInfoKey = 'userInfo'
export const getUserInfo = () => Cookies.get(userInfoKey)
export const setUserInfo = (v: unknown) => Cookies.set(userInfoKey, v as string)
export const removeUserInfo = () => Cookies.remove(userInfoKey)
