import request from '@/utils/request'

// Order Management

export const getOrderDetailPage = (params: any) =>
  request({ url: '/order/conditionSearch', method: 'get', params })


export const queryOrderDetailById = (params: any) =>
  request({ url: `/order/details/${params.orderId}`, method: 'get' })

// Start Delivery
export const deliveryOrder = (params: any) =>
  request({ url: `/order/delivery/${params.id}`, method: 'put' })

// Complete
export const completeOrder = (params: any) =>
  request({ url: `/order/complete/${params.id}`, method: 'put' })

// Cancel
export const orderCancel = (params: any) =>
  request({ url: '/order/cancel', method: 'put', data: { ...params } })

// Accept
export const orderAccept = (params: any) =>
  request({ url: '/order/confirm', method: 'put', data: { ...params } })

// Reject
export const orderReject = (params: any) =>
  request({ url: '/order/rejection', method: 'put', data: { ...params } })


export const getOrderListBy = (_params: any) =>
  request({ url: '/order/statistics', method: 'get' })
