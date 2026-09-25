import axios from 'axios';
import { API_BASE_URL, API_CONFIG, TOKEN_HEADER, getToken, removeToken } from './config';
import toast from 'react-hot-toast';

export const AUTH_EXPIRED_EVENT = 'sky:auth-expired';

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_CONFIG.timeout,
});


request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers[TOKEN_HEADER] = token;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);


request.interceptors.response.use(
  (response) => {
    const res = response.data;

    if (res.code === 1) {
      return res;
    }

    toast.error(res.msg || 'Request failed');
    return Promise.reject(new Error(res.msg || 'Request failed'));
  },
  (error) => {
    if (error.response?.status === 401) {


      removeToken();
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));

      const url = error.config?.url || '';
      const publicApis = ['/user/category/list', '/user/dish/list', '/user/setmeal/list', '/user/shop/status', '/user/shoppingCart'];
      const isPublicApi = publicApis.some(api => url.includes(api));

      if (!isPublicApi) {
        toast.error('Please login to continue');
      }
    } else {

      if (error.response?.status === 500) {
        toast.error('Server error, please try again later');
      } else if (error.message === 'Network Error') {
        toast.error('Network error, please check your connection');
      } else {
        toast.error(error.message || 'Request failed');
      }
    }
    return Promise.reject(error);
  }
);

export default request;
