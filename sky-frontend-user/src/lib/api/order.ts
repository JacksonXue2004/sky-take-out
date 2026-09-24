import request from '../request';

// 订单相关 API

// 用户登录
export const login = (data: { email: string; password: string }) => {
  return request.post('/user/user/login', data);
};

// 用户注册
export const register = (data: { email: string; password: string }) => {
  return request.post('/user/user/register', data);
};

// 获取登录用户信息
// 下单
export const submitOrder = (data: {
  addressBookId: number;
  payMethod: number;
  remark?: string;
  tablewareNumber?: number;
  tablewareStatus?: number;
  deliveryStatus?: number;
  estimatedDeliveryTime?: string;
  packAmount: number;
  amount: number;
}) => {
  return request.post('/user/order/submit', data);
};

// 订单支付（Stripe Checkout）
export const payOrder = (orderNumber: string, payMethod = 1) => {
  return request.put('/user/order/payment', { orderNumber, payMethod });
};

// Development checkout: use the backend's existing paid-order workflow.
export const simulatePayment = (orderNumber: string) => {
  return request.put('/user/order/payment/simulate', { orderNumber, payMethod: 1 });
};

// 用户端订单分页查询
export const getUserOrders = (params: { page: number; pageSize: number; status?: number }) => {
  return request.get('/user/order/historyOrders', { params });
};

// 查询订单详情
export const getOrderDetail = (id: number) => {
  return request.get(`/user/order/orderDetail/${id}`);
};

// 用户取消订单
export const cancelOrder = (id: number) => {
  return request.put(`/user/order/cancel/${id}`);
};

// 再来一单
export const repetition = (id: number) => {
  return request.post(`/user/order/repetition/${id}`);
};

// 客户催单
export const reminder = (id: number) => {
  return request.get(`/user/order/reminder/${id}`);
};
