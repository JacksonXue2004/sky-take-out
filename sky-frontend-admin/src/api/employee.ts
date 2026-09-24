import request from '@/utils/request'

// Employee Management
// Sign In
export const login = (data: any) =>
  request({ url: '/employee/login', method: 'post', data })

// 退出
export const userLogout = (params: any) =>
  request({ url: '/employee/logout', method: 'post', params })

// 分页Search
export const getEmployeeList = (params: any) =>
  request({ url: '/employee/page', method: 'get', params })

// EnabledDisabled
export const enableOrDisableEmployee = (params: any) =>
  request({ url: `/employee/status/${params.status}`, method: 'post', params: { id: params.id } })

// Add 
export const addEmployee = (params: any) =>
  request({ url: '/employee', method: 'post', data: { ...params } })

// Edit
export const editEmployee = (params: any) =>
  request({ url: '/employee', method: 'put', data: { ...params } })

// 反查详情
export const queryEmployeeById = (id: string | (string | null)[]) =>
  request({ url: `/employee/${id}`, method: 'get' })
