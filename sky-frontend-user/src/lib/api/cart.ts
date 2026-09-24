import request from '../request';

// 购物车相关 API

// 查询购物车
export const getCartList = () => {
  return request.get('/user/shoppingCart/list');
};

// 添加到购物车
export const addToCart = (data: {
  dishId?: number;
  setmealId?: number;
  dishFlavor?: string;
  number?: number;
}) => {
  return request.post('/user/shoppingCart/add', data);
};

// 减少购物车商品数量
export const reduceCartItem = (data: {
  dishId?: number;
  setmealId?: number;
  dishFlavor?: string;
}) => {
  return request.post('/user/shoppingCart/reduce', data);
};

// 清空购物车
export const clearCart = () => {
  return request.delete('/user/shoppingCart/cleanUp');
};