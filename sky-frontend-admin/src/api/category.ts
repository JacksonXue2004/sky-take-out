import request from '@/utils/request'

// Category Management

export const getCategoryPage = (params: any) =>
  request({ url: '/category/page', method: 'get', params })

// Delete
export const deleCategory = (ids: string) =>
  request({ url: '/category', method: 'delete', params: { id: ids } })

// Edit
export const editCategory = (params: any) =>
  request({ url: '/category', method: 'put', data: { ...params } })

// Add
export const addCategory = (params: any) =>
  request({ url: '/category', method: 'post', data: { ...params } })

// EnabledDisabled
export const enableOrDisableEmployee = (params: any) =>
  request({ url: `/category/status/${params.status}`, method: 'post', params: { id: params.id } })
