
export const API_BASE_URL = import.meta.env.VITE_API_URL || '';

export const API_CONFIG = {
  timeout: 10000,
};


export const TOKEN_KEY = 'token';
export const TOKEN_HEADER = 'authentication';
export const USER_KEY = 'user';

export const getToken = () => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
};

export const setToken = (token: string) => {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, token);
};

export const removeToken = () => {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
};

export const isAuthenticated = () => {
  return !!getToken();
};
