import request from '@/utils/request'

// 消息中心（后端未实现 /messages/*，与原前端保持一致，实际未在界面使用）
export const getInformData = (params: any) =>
  request({ url: '/messages/page', method: 'get', params })

export const getCountUnread = () =>
  request({ url: '/messages/countUnread', method: 'get' })

export const batchMsg = (data: any) =>
  request({ url: '/messages/batch', method: 'put', data })

export const setStatus = (params: any) =>
  request({ url: `/messages/${params}`, method: 'PUT' })
