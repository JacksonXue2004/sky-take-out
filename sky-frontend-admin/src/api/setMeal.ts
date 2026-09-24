import request from '@/utils/request'

// Combo Management
// 分页Search
export const getSetmealPage = (params: any) =>
  request({ url: '/setmeal/page', method: 'get', params })

// Delete（单/批量，ids 逗号分隔）
export const deleteSetmeal = (ids: string) =>
  request({ url: '/setmeal', method: 'delete', params: { ids } })

// Edit
export const editSetmeal = (params: any) =>
  request({ url: '/setmeal', method: 'put', data: { ...params } })

// Add 
export const addSetmeal = (params: any) =>
  request({ url: '/setmeal', method: 'post', data: { ...params } })

// Search详情
export const querySetmealById = (id: string | (string | null)[]) =>
  request({ url: `/setmeal/${id}`, method: 'get' })

// 起售Off Sale
export const setmealStatusByStatus = (params: any) =>
  request({ url: `/setmeal/status/${params.status}`, method: 'post', params: { id: params.ids } })

// Item Category数据Search
export const dishCategoryList = (params: any) =>
  request({ url: '/category/list', method: 'get', params: { ...params } })
