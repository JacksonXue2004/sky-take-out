'use client';

import { create } from 'zustand';
import { User, LoginResponse } from '@/types';
import { getToken, setToken as saveToken, removeToken } from '@/lib/config';
import { getUserInfo } from '@/lib/api/order';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (data: LoginResponse) => void;
  logout: () => void;
  fetchUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: getToken(),
  isAuthenticated: !!getToken(),

  login: (data: LoginResponse) => {
    saveToken(data.token);
    set({
      user: { id: data.id, email: data.email },
      token: data.token,
      isAuthenticated: true,
    });
  },

  logout: () => {
    removeToken();
    set({
      user: null,
      token: null,
      isAuthenticated: false,
    });
  },

  fetchUser: async () => {
    try {
      const res = await getUserInfo();
      if (res.data) {
        set({ user: res.data });
      }
    } catch {
      // Token invalid, clear auth
      removeToken();
      set({
        user: null,
        token: null,
        isAuthenticated: false,
      });
    }
  },
}));