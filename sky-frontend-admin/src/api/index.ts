import request from '@/utils/request'

// Dashboard - 今日订单概览
export const getOrderData = () =>
  request({ url: '/workspace/overviewOrders', method: 'get' })

// Dashboard - Item Overview
export const getOverviewDishes = () =>
  request({ url: '/workspace/overviewDishes', method: 'get' })

// Dashboard - Combo Overview
export const getSetMealStatistics = () =>
  request({ url: '/workspace/overviewSetmeals', method: 'get' })

// Dashboard - 今日营业数据
export const getBusinessData = () =>
  request({ url: '/workspace/businessData', method: 'get' })

// 报表 - Revenue
export const getTurnoverStatistics = (params: any) =>
  request({ url: '/report/turnoverStatistics', method: 'get', params })

// 报表 - Customers
export const getUserStatistics = (params: any) =>
  request({ url: '/report/userStatistics', method: 'get', params })

// 报表 - Orders
export const getOrderStatistics = (params: any) =>
  request({ url: '/report/ordersStatistics', method: 'get', params })

// 报表 - Top 10 Items
export const getTop = (params: any) =>
  request({ url: '/report/top10', method: 'get', params })

// 报表 - 数据概览（后端未实现，保持原样）
export const getDataOverView = (params: any) =>
  request({ url: '/report/dataOverView', method: 'get', params })

// 报表 - 导出
export const exportInfor = () =>
  request({ url: '/report/export', method: 'get', responseType: 'blob' })
