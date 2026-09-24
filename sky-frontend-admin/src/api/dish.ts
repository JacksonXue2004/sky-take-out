import request from '@/utils/request'

// Item Management
// 分页Search
export const getDishPage = (params: any) =>
  request({ url: '/dish/page', method: 'get', params })

// Delete（单/批量，ids 逗号分隔）
export const deleteDish = (ids: string) =>
  request({ url: '/dish', method: 'delete', params: { ids } })

// Edit
export const editDish = (params: any) =>
  request({ url: '/dish', method: 'put', data: { ...params } })

// Add 
export const addDish = (params: any) =>
  request({ url: '/dish', method: 'post', data: { ...params } })

// Search详情
export const queryDishById = (id: string | (string | null)[]) =>
  request({ url: `/dish/${id}`, method: 'get' })

// 获取Item Category列表
export const getCategoryList = (params: any) =>
  request({ url: '/category/list', method: 'get', params })

// 查Item列表
export const queryDishList = (params: any) =>
  request({ url: '/dish/list', method: 'get', params })

// 文件下载/预览（后端未实现，保持原样）
export const commonDownload = (params: any) =>
  request({
    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
    url: '/common/download',
    method: 'get',
    params,
  })

// 起售Off Sale
export const dishStatusByStatus = (params: any) =>
  request({ url: `/dish/status/${params.status}`, method: 'post', params: { id: params.id } })

// Item Category数据Search
export const dishCategoryList = (params: any) =>
  request({ url: '/category/list', method: 'get', params: { ...params } })
