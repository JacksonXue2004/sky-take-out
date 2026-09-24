import { ReactNode, useEffect } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import HomePage from '@/pages/HomePage';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import CartPage from '@/pages/CartPage';
import CheckoutPage from '@/pages/CheckoutPage';
import AccountPage from '@/pages/AccountPage';
import AddressPage from '@/pages/AddressPage';
import OrdersPage from '@/pages/OrdersPage';
import OrderDetailPage from '@/pages/OrderDetailPage';
import OrderSuccessPage from '@/pages/OrderSuccessPage';
import { AUTH_EXPIRED_EVENT } from '@/lib/request';
import { getToken } from '@/lib/config';
import { useAuthStore } from '@/store/auth';

function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  return authenticated && getToken()
    ? children
    : <Navigate to="/login" replace state={{ from: location.pathname }} />;
}

export default function App() {
  const logout = useAuthStore((state) => state.logout);

  useEffect(() => {
    window.addEventListener(AUTH_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, logout);
  }, [logout]);

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/cart" element={<CartPage />} />
      <Route path="/checkout" element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />
      <Route path="/account" element={<ProtectedRoute><AccountPage /></ProtectedRoute>} />
      <Route path="/account/address" element={<ProtectedRoute><AddressPage /></ProtectedRoute>} />
      <Route path="/account/address/add" element={<ProtectedRoute><AddressPage startAdding /></ProtectedRoute>} />
      <Route path="/orders" element={<ProtectedRoute><OrdersPage /></ProtectedRoute>} />
      <Route path="/orders/:id" element={<ProtectedRoute><OrderDetailPage /></ProtectedRoute>} />
      <Route path="/order/success" element={<ProtectedRoute><OrderSuccessPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
