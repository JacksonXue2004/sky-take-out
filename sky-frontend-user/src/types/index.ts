

export interface Result<T = any> {
  code: number;
  msg: string;
  data: T;
}

export interface User {
  id: number;
  email: string;
  name?: string;
  phone?: string;
  avatar?: string;
}

export interface LoginResponse {
  id: number;
  email: string;
  token: string;
}

export interface Category {
  id: number;
  name: string;
  type: number; // 1: dish, 2: setmeal
  sort: number;
  status: number;
}

export interface Dish {
  id: number;
  name: string;
  categoryId: number;
  price: number;
  image: string;
  description: string;
  status: number;
  flavors?: DishFlavor[];
}

export interface DishFlavor {
  id: number;
  dishId: number;
  name: string;
  value: string; // JSON array of options
}

export interface Setmeal {
  id: number;
  name: string;
  categoryId: number;
  price: number;
  image: string;
  description: string;
  status: number;
  setmealDishes?: SetmealDish[];
}

export interface SetmealDish {
  id: number;
  setmealId: number;
  dishId: number;
  name: string;
  price: number;
  copies: number;
}

export interface CartItem {
  id: number;
  name: string;
  image: string;
  userId?: number;
  dishId?: number;
  setmealId?: number;
  dishFlavor?: string;
  number: number;
  amount: number;
  price?: number;
}

export interface AddressBook {
  id: number;
  userId: number;
  consignee: string;
  sex: string;
  phone: string;
  provinceCode: string;
  provinceName: string;
  cityCode: string;
  cityName: string;
  districtCode: string;
  districtName: string;
  detail: string;
  label?: string;
  isDefault: number;
}

export interface OrderDetail {
  id: number;
  name: string;
  image: string;
  orderId: number;
  dishId?: number;
  setmealId?: number;
  dishFlavor?: string;
  number: number;
  amount: number;
}

export interface Order {
  id: number;
  number: string;
  status: number; // 1-6
  userId: number;
  addressBookId: number;
  orderTime: string;
  checkoutTime?: string;
  payMethod: number;
  payStatus: number;
  amount: number;
  remark?: string;
  phone: string;
  address: string;
  userName: string;
  consignee: string;
  cancelReason?: string;
  rejectionReason?: string;
  cancelTime?: string;
  estimatedDeliveryTime?: string;
  deliveryStatus?: number;
  deliveryTime?: string;
  orderDetailList?: OrderDetail[];
}

export type OrderStatusText = {
  [key: number]: string;
};

export const ORDER_STATUS: OrderStatusText = {
  1: 'Pending Payment',
  2: 'Pending Confirmation',
  3: 'Confirmed',
  4: 'Delivering',
  5: 'Completed',
  6: 'Cancelled',
};

export const CATEGORY_TYPE = {
  1: 'Dish',
  2: 'Setmeal',
};