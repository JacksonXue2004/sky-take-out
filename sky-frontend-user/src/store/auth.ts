import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { LoginResponse, User } from '@/types';
import { getToken, removeToken, setToken } from '@/lib/config';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (data: LoginResponse) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: getToken(),
      isAuthenticated: Boolean(getToken()),
      login: (data) => {
        setToken(data.token);
        set({ user: { id: data.id, email: data.email }, token: data.token, isAuthenticated: true });
      },
      logout: () => {
        removeToken();
        set({ user: null, token: null, isAuthenticated: false });
      },
    }),
    { name: 'sky-auth', partialize: (state) => ({ user: state.user }) },
  ),
);
