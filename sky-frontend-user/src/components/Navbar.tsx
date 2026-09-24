import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';

export default function Navbar() {
  const navigate = useNavigate();
  const { isAuthenticated, user, logout } = useAuthStore();
  const totalCount = useCartStore((state) => state.totalCount);

  const handleLogout = () => {
    logout();
    toast.success('Logged out successfully');
    navigate('/login');
  };

  return (
    <nav className="bg-white shadow-sm sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center gap-6">
            <Link to="/" className="text-2xl font-bold text-orange-500">Sky Take-Out</Link>
            <Link to="/" className="hidden sm:block text-gray-700 hover:text-orange-500">Menu</Link>
          </div>
          <div className="flex items-center gap-3 text-sm sm:text-base">
            <Link to="/cart" className="relative text-gray-700 hover:text-orange-500 px-2 py-2">
              Cart
              {totalCount > 0 && <span className="ml-1 bg-orange-500 text-white text-xs rounded-full px-2 py-0.5">{totalCount}</span>}
            </Link>
            {isAuthenticated ? (
              <>
                <Link to="/orders" className="text-gray-700 hover:text-orange-500">Orders</Link>
                <Link to="/account" className="text-gray-700 hover:text-orange-500">{user?.email?.split('@')[0] || 'Account'}</Link>
                <button onClick={handleLogout} className="bg-orange-500 text-white px-3 py-2 rounded-lg hover:bg-orange-600">Logout</button>
              </>
            ) : (
              <>
                <Link to="/login" className="text-gray-700 hover:text-orange-500">Login</Link>
                <Link to="/register" className="bg-orange-500 text-white px-3 py-2 rounded-lg hover:bg-orange-600">Sign Up</Link>
              </>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
