import axios from 'axios';
import { API_BASE_URL, API_CONFIG, getToken, removeToken } from './config';
import toast from 'react-hot-toast';

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_CONFIG.timeout,
});

// 请求拦截器：添加 Token
request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    // 如果 code 为 1 表示成功
    if (res.code === 1) {
      return res;
    }
    // 否则显示错误信息
    toast.error(res.msg || '请求失败');
    return Promise.reject(new Error(res.msg || '请求失败'));
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token 无效，清除 token 但不强制跳转
      // 让业务页面自行处理未登录状态
      removeToken();
      // 只在非公开接口显示错误
      const url = error.config?.url || '';
      const publicApis = ['/user/category/list', '/user/dish/list', '/user/setmeal/list', '/user/shop/status', '/user/shoppingCart'];
      const isPublicApi = publicApis.some(api => url.includes(api));
      
      if (!isPublicApi) {
        toast.error('Please login to continue');
      }
    } else {
      // 500 错误可能是后端 Redis 连接问题等
      if (error.response?.status === 500) {
        toast.error('Server error, please try again later');
      } else if (error.message === 'Network Error') {
        toast.error('Network error, please check your connection');
      } else {
        toast.error(error.message || '请求失败');
      }
    }
    return Promise.reject(error);
  }
);

export default request;
