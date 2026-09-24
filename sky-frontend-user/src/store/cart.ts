import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { CartItem } from '@/types';
import { addToCart, clearCart as clearServerCart, getCartList, reduceCartItem } from '@/lib/api/cart';
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
  clearCart: () => void;
  clearLocalCart: () => void;
  syncToServer: () => Promise<void>;
}

const totals = (items: CartItem[]) => ({
  totalCount: items.reduce((sum, item) => sum + item.number, 0),
  totalAmount: items.reduce((sum, item) => sum + (item.amount ?? item.price ?? 0) * item.number, 0),
});

const payload = (item: CartItem) => ({ dishId: item.dishId, setmealId: item.setmealId, dishFlavor: item.dishFlavor, number: 1 });

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [], totalCount: 0, totalAmount: 0, loading: false,
      fetchCart: async () => {
        if (!getToken()) return;
        set({ loading: true });
        try {
          const response = await getCartList();
          const items = response.data || [];
          set({ items, ...totals(items) });
        } finally { set({ loading: false }); }
      },
      addItem: (item) => {
        if (getToken()) {
          void addToCart(payload(item)).then(() => get().fetchCart());
          return;
        }
        const items = [...get().items];
        const index = items.findIndex((candidate) => candidate.dishId === item.dishId && candidate.setmealId === item.setmealId && candidate.dishFlavor === item.dishFlavor);
        if (index >= 0) items[index] = { ...items[index], number: items[index].number + 1 };
        else items.push({ ...item, id: Date.now() });
        set({ items, ...totals(items) });
      },
      updateQuantity: (id, quantity) => {
        const item = get().items.find((candidate) => candidate.id === id);
        if (!item) return;
        if (!getToken()) {
          const items = quantity <= 0 ? get().items.filter((candidate) => candidate.id !== id) : get().items.map((candidate) => candidate.id === id ? { ...candidate, number: quantity } : candidate);
          set({ items, ...totals(items) });
          return;
        }
        const operation = quantity > item.number ? addToCart(payload(item)) : reduceCartItem(payload(item));
        void operation.then(() => get().fetchCart());
      },
      removeItem: (id) => {
        const item = get().items.find((candidate) => candidate.id === id);
        if (!item) return;
        if (!getToken()) {
          const items = get().items.filter((candidate) => candidate.id !== id);
          set({ items, ...totals(items) });
          return;
        }
        void (async () => {
          for (let count = 0; count < item.number; count += 1) await reduceCartItem(payload(item));
          await get().fetchCart();
        })();
      },
      clearCart: () => {
        set({ items: [], totalCount: 0, totalAmount: 0 });
        if (getToken()) void clearServerCart();
      },
      clearLocalCart: () => set({ items: [], totalCount: 0, totalAmount: 0 }),
      syncToServer: async () => {
        if (!getToken()) return;
        const localItems = [...get().items];
        await clearServerCart();
        for (const item of localItems) {
          for (let count = 0; count < item.number; count += 1) await addToCart(payload(item));
        }
        await get().fetchCart();
      },
    }),
    { name: 'sky-cart', partialize: (state) => ({ items: state.items, totalCount: state.totalCount, totalAmount: state.totalAmount }) },
  ),
);
