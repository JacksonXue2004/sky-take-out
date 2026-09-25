import request from '../request';


export const login = (data: { email: string; password: string }) => {
  return request.post('/user/user/login', data);
};


export const register = (data: { email: string; password: string }) => {
  return request.post('/user/user/register', data);
};


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


export const payOrder = (orderNumber: string, payMethod = 1) => {
  return request.put('/user/order/payment', { orderNumber, payMethod });
};

// Development checkout: use the backend's existing paid-order workflow.
export const simulatePayment = (orderNumber: string) => {
  return request.put('/user/order/payment/simulate', { orderNumber, payMethod: 1 });
};


export const getUserOrders = (params: { page: number; pageSize: number; status?: number }) => {
  return request.get('/user/order/historyOrders', { params });
};


export const getOrderDetail = (id: number) => {
  return request.get(`/user/order/orderDetail/${id}`);
};


export const cancelOrder = (id: number) => {
  return request.put(`/user/order/cancel/${id}`);
};


export const repetition = (id: number) => {
  return request.post(`/user/order/repetition/${id}`);
};


export const reminder = (id: number) => {
  return request.get(`/user/order/reminder/${id}`);
};
