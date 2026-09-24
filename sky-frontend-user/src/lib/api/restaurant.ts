import request from '../request';

// 餐厅/菜品相关 API

// 获取店铺营业状态
export const getShopStatus = () => {
  return request.get('/user/shop/status');
};

// 获取分类列表
export const getCategoryList = () => {
  return request.get('/user/category/list');
};

// 根据分类获取菜品列表
export const getDishList = (categoryId: number) => {
  return request.get('/user/dish/list', { params: { categoryId } });
};

// 获取套餐列表
export const getSetmealList = (categoryId: number) => {
  return request.get('/user/setmeal/list', { params: { categoryId } });
};

// 获取所有菜品（用于首页展示）
export const getAllDishes = () => {
  return request.get('/user/dish/list');
};

// 获取套餐详情
export const getSetmealDetail = (id: number) => {
  return request.get(`/user/setmeal/${id}`);
};