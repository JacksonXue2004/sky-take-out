import request from '../request';


export const getShopStatus = () => {
  return request.get('/user/shop/status');
};


export const getCategoryList = () => {
  return request.get('/user/category/list');
};


export const getDishList = (categoryId: number) => {
  return request.get('/user/dish/list', { params: { categoryId } });
};


export const getSetmealList = (categoryId: number) => {
  return request.get('/user/setmeal/list', { params: { categoryId } });
};


export const getAllDishes = () => {
  return request.get('/user/dish/list');
};


export const getSetmealDetail = (id: number) => {
  return request.get(`/user/setmeal/${id}`);
};