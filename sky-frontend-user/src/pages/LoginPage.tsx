import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import Navbar from '@/components/Navbar';
import { login } from '@/lib/api/order';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const authenticate = useAuthStore((state) => state.login);
  const syncToServer = useCartStore((state) => state.syncToServer);
  const [form, setForm] = useState({ email: '', password: '' });
  const [loading, setLoading] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await login(form);
      authenticate(response.data);
      await syncToServer();
      toast.success('Login successful');
      const destination = (location.state as { from?: string } | null)?.from || '/';
      navigate(destination, { replace: true });
    } finally { setLoading(false); }
  };

  return <AuthForm title="Welcome Back" action="Sign In" loading={loading} form={form} setForm={setForm} onSubmit={submit} footer={<span>New here? <Link className="text-orange-500" to="/register">Create an account</Link></span>} />;
}

export function AuthForm({ title, action, loading, form, setForm, onSubmit, footer }: any) {
  return <div className="min-h-screen bg-gray-50"><Navbar /><form onSubmit={onSubmit} className="max-w-md mx-auto mt-16 p-8 bg-white rounded-xl shadow-md space-y-5"><h1 className="text-2xl font-bold text-center">{title}</h1><label className="block">Email<input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className="mt-1 w-full px-4 py-3 border rounded-lg" /></label><label className="block">Password<input type="password" required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} className="mt-1 w-full px-4 py-3 border rounded-lg" /></label>{form.confirmPassword !== undefined && <label className="block">Confirm Password<input type="password" required value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} className="mt-1 w-full px-4 py-3 border rounded-lg" /></label>}<button disabled={loading} className="w-full bg-orange-500 text-white py-3 rounded-lg disabled:bg-gray-300">{loading ? 'Please wait...' : action}</button><p className="text-center text-gray-600">{footer}</p></form></div>;
}
