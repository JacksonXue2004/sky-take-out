'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';
import toast from 'react-hot-toast';

export default function Navbar() {
  const router = useRouter();
  const { isAuthenticated, user, logout } = useAuthStore();
  const { totalCount } = useCartStore();

  const handleLogout = () => {
    logout();
    toast.success('Logged out successfully');
    router.push('/login');
  };

  return (
    <nav className="bg-white shadow-sm sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <Link href="/" className="text-2xl font-bold text-orange-500">
              🍜 Sky Take-Out
            </Link>
            <div className="hidden sm:ml-8 sm:flex sm:space-x-8">
              <Link
                href="/"
                className="text-gray-900 hover:text-orange-500 px-3 py-2 font-medium"
              >
                Menu
              </Link>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <Link
              href="/cart"
              className="relative text-gray-700 hover:text-orange-500 px-3 py-2"
            >
              🛒 Cart
              {totalCount > 0 && (
                <span className="absolute -top-1 -right-1 bg-orange-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
                  {totalCount}
                </span>
              )}
            </Link>
            {isAuthenticated ? (
              <>
                <Link
                  href="/orders"
                  className="text-gray-700 hover:text-orange-500 px-3 py-2 font-medium"
                >
                  Orders
                </Link>
                <Link
                  href="/account"
                  className="text-gray-700 hover:text-orange-500 px-3 py-2 font-medium"
                >
                  {user?.email?.split('@')[0] || 'Account'}
                </Link>
                <button
                  onClick={handleLogout}
                  className="bg-orange-500 text-white px-4 py-2 rounded-lg hover:bg-orange-600 transition-colors"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link
                  href="/login"
                  className="text-gray-700 hover:text-orange-500 px-3 py-2 font-medium"
                >
                  Login
                </Link>
                <Link
                  href="/register"
                  className="bg-orange-500 text-white px-4 py-2 rounded-lg hover:bg-orange-600 transition-colors"
                >
                  Sign Up
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
