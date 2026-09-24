'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';
import toast, { Toaster } from 'react-hot-toast';
import Link from 'next/link';

export default function CartPage() {
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const { items, totalCount, totalAmount, clearCart, updateQuantity, removeItem } =
    useCartStore();

  const handleClearCart = () => {
    clearCart();
    toast.success('Cart cleared');
  };

  const handleCheckout = () => {
    if (items.length === 0) {
      toast.error('Your cart is empty');
      return;
    }
    if (!isAuthenticated) {
      toast.error('Please login to checkout');
      router.push('/login');
      return;
    }
    router.push('/checkout');
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Toaster position="top-right" />
      <Navbar />

      <div className="max-w-4xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">
          🛒 Your Cart ({totalCount} items)
        </h1>

        {items.length === 0 ? (
          <div className="bg-white rounded-xl shadow-md p-12 text-center">
            <div className="text-6xl mb-4">🛒</div>
            <h2 className="text-xl font-semibold text-gray-700 mb-2">
              Your cart is empty
            </h2>
            <p className="text-gray-500 mb-6">
              Browse our menu and add some delicious items!
            </p>
            <Link
              href="/"
              className="inline-block bg-orange-500 text-white px-6 py-3 rounded-lg font-medium hover:bg-orange-600 transition-colors"
            >
              Browse Menu
            </Link>
          </div>
        ) : (
          <>
            <div className="bg-white rounded-xl shadow-md divide-y divide-gray-200">
              {items.map((item) => (
                <div key={item.id} className="p-4 flex items-center gap-4">
                  <div className="w-20 h-20 bg-gray-200 rounded-lg overflow-hidden flex-shrink-0">
                    {item.image ? (
                      <img
                        src={item.image}
                        alt={item.name}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-2xl">
                        🍽️
                      </div>
                    )}
                  </div>
                  <div className="flex-1">
                    <h3 className="font-semibold text-gray-900">{item.name}</h3>
                    {item.dishFlavor && (
                      <p className="text-sm text-gray-500">{item.dishFlavor}</p>
                    )}
                    <p className="text-orange-500 font-semibold">
                      ${((item.amount ?? item.price ?? 0) / 100).toFixed(2)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => updateQuantity(item.id, item.number - 1)}
                      className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center"
                    >
                      -
                    </button>
                    <span className="font-medium w-8 text-center">{item.number}</span>
                    <button
                      onClick={() => updateQuantity(item.id, item.number + 1)}
                      className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center"
                    >
                      +
                    </button>
                  </div>
                  <div className="text-right">
                    <p className="font-bold text-lg">
                      ${(((item.amount ?? item.price ?? 0) * item.number) / 100).toFixed(2)}
                    </p>
                    <button
                      onClick={() => removeItem(item.id)}
                      className="text-gray-400 hover:text-red-500 text-sm"
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))}
            </div>

            <div className="bg-white rounded-xl shadow-md mt-6 p-6">
              <div className="flex justify-between items-center mb-4">
                <button
                  onClick={handleClearCart}
                  className="text-gray-500 hover:text-red-500 text-sm"
                >
                  Clear Cart
                </button>
                <div className="text-right">
                  <p className="text-gray-500">Total</p>
                  <p className="text-2xl font-bold text-orange-500">
                    ${(totalAmount / 100).toFixed(2)}
                  </p>
                </div>
              </div>
              <button
                onClick={handleCheckout}
                className="w-full bg-orange-500 text-white py-3 rounded-lg font-medium hover:bg-orange-600 transition-colors"
              >
                {isAuthenticated ? 'Proceed to Checkout' : 'Login to Checkout'}
              </button>
              {!isAuthenticated && (
                <p className="text-center text-gray-500 text-sm mt-3">
                  You can browse and add items to cart without logging in. Login is required only for checkout.
                </p>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
