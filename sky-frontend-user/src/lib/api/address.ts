import request from '../request';

// 地址簿相关 API

// 查询所有地址
export const getAddressList = () => {
  return request.get('/user/addressBook/list');
};

// 新增地址
export const addAddress = (data: {
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
}) => {
  return request.post('/user/addressBook/add', data);
};

// 修改地址
export const updateAddress = (data: {
  id: number;
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
}) => {
  return request.put('/user/addressBook/update', data);
};

// 删除地址
export const deleteAddress = (id: number) => {
  return request.delete(`/user/addressBook/delete/${id}`);
};

// 设置默认地址
export const setDefaultAddress = (id: number) => {
  return request.put(`/user/addressBook/setDefault/${id}`);
};

// 查询默认地址
export const getDefaultAddress = () => {
  return request.get('/user/addressBook/getDefault');
};