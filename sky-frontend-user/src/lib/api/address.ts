import request from '../request';


export const getAddressList = () => {
  return request.get('/user/addressBook/list');
};


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
  return request.post('/user/addressBook', data);
};


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
  return request.put('/user/addressBook', data);
};


export const deleteAddress = (id: number) => {
  return request.delete('/user/addressBook', { params: { id } });
};


export const setDefaultAddress = (id: number) => {
  return request.put('/user/addressBook/default', { id });
};


export const getDefaultAddress = () => {
  return request.get('/user/addressBook/default');
};

export const getAddressById = (id: number) => {
  return request.get(`/user/addressBook/${id}`);
};
