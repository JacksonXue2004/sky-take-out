import request from '../request';


export const getCartList = () => {
  return request.get('/user/shoppingCart/list');
};


export const addToCart = (data: {
  dishId?: number;
  setmealId?: number;
  dishFlavor?: string;
  number?: number;
}) => {
  return request.post('/user/shoppingCart/add', data);
};


export const reduceCartItem = (data: {
  dishId?: number;
  setmealId?: number;
  dishFlavor?: string;
}) => {
  return request.post('/user/shoppingCart/sub', data);
};


export const clearCart = () => {
  return request.delete('/user/shoppingCart/clean');
};
