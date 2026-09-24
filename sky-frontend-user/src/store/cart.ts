'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { CartItem } from '@/types';
import { getCartList, addToCart as apiAddToCart, clearCart as apiClearCart } from '@/lib/api/cart';
import { getToken } from '@/lib/config';

interface CartState {
  items: CartItem[];
  totalCount: number;
  totalAmount: number;
  loading: boolean;
  fetchCart: () => Promise<void>;
  addItem: (item: CartItem) => void;
  removeItem: (id: number) => void;
  updateQuantity: (id: number, quantity: number) => void;
  setItems: (items: CartItem[]) => void;
  clearCart: () => void;
  syncToServer: () => Promise<void>;
  updateTotals: (items: CartItem[]) => void;
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      totalCount: 0,
      totalAmount: 0,
      loading: false,

      fetchCart: async () => {
        const token = getToken();
        if (!token) {
          // 未登录时，使用本地存储的购物车
          return;
        }

        set({ loading: true });
        try {
          const res = await getCartList();
          const items: CartItem[] = res.data || [];
          set({ items });
          get().updateTotals(items);
        } catch {
          // Error handled silently
        } finally {
          set({ loading: false });
        }
      },

      addItem: (item: CartItem) => {
        const items = [...get().items];
        const existingIndex = items.findIndex(
          (i) => i.dishId === item.dishId && i.setmealId === item.setmealId && i.dishFlavor === item.dishFlavor
        );

        if (existingIndex >= 0) {
          items[existingIndex] = {
            ...items[existingIndex],
            number: items[existingIndex].number + 1,
          };
        } else {
          items.push({ ...item, id: Date.now() });
        }

        set({ items });
        get().updateTotals(items);

        // 如果已登录，同步到服务器
        const token = getToken();
        if (token) {
          try {
            apiAddToCart({
              number: 1,
              dishId: item.dishId,
              setmealId: item.setmealId,
              dishFlavor: item.dishFlavor,
            });
          } catch {
            // 忽略错误，本地购物车已更新
          }
        }
      },

      removeItem: (id: number) => {
        const items = get().items.filter((i) => i.id !== id);
        set({ items });
        get().updateTotals(items);
      },

      updateQuantity: (id: number, quantity: number) => {
        if (quantity <= 0) {
          get().removeItem(id);
          return;
        }
        const items = get().items.map((i) =>
          i.id === id ? { ...i, number: quantity } : i
        );
        set({ items });
        get().updateTotals(items);
      },

      setItems: (items: CartItem[]) => {
        set({ items });
        get().updateTotals(items);
      },

      clearCart: () => {
        set({ items: [], totalCount: 0, totalAmount: 0 });
        const token = getToken();
        if (token) {
          try {
            apiClearCart();
          } catch {
            // 忽略错误
          }
        }
      },

      syncToServer: async () => {
        const token = getToken();
        if (!token) return;

        const { items } = get();
        // 先清空服务器购物车，再同步本地购物车
        try {
          await apiClearCart();
          for (const item of items) {
            await apiAddToCart({
              number: item.number,
              dishId: item.dishId,
              setmealId: item.setmealId,
              dishFlavor: item.dishFlavor,
            });
          }
        } catch {
          // 忽略错误
        }
      },

      // 更新总数和总价的辅助方法
      updateTotals: (items: CartItem[]) => {
        const totalCount = items.reduce((sum, item) => sum + item.number, 0);
        const totalAmount = items.reduce((sum, item) => sum + (item.amount || 0) * item.number, 0);
        set({ totalCount, totalAmount });
      },
    }),
    {
      name: 'sky-cart', // localStorage 中的 key
      partialize: (state) => ({
        items: state.items,
        totalCount: state.totalCount,
        totalAmount: state.totalAmount,
      }),
    }
  )
);
