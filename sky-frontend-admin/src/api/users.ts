import request from '@/utils/request'

// Change Password（后端未实现此接口，保持与原前端一致）
export const editPassword = (data: any) =>
  request({ url: '/employee/editPassword', method: 'put', data })

// 获取营业Status
export const getStatus = () =>
  request({ url: '/shop/status', method: 'get' })

// 设置营业Status (PUT /shop/{status})
export const setStatus = (data: any) =>
  request({ url: '/shop/' + data, method: 'put', data })
