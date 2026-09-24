import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { register } from '@/lib/api/order';
import { useAuthStore } from '@/store/auth';
import { AuthForm } from './LoginPage';

export default function RegisterPage() {
  const navigate = useNavigate();
  const authenticate = useAuthStore((state) => state.login);
  const [form, setForm] = useState({ email: '', password: '', confirmPassword: '' });
  const [loading, setLoading] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (form.password !== form.confirmPassword) return toast.error('Passwords do not match');
    setLoading(true);
    try {
      const response = await register({ email: form.email, password: form.password });
      authenticate(response.data);
      toast.success('Account created');
      navigate('/');
    } finally { setLoading(false); }
  };
  return <AuthForm title="Create Account" action="Sign Up" loading={loading} form={form} setForm={setForm} onSubmit={submit} footer={<span>Already registered? <Link className="text-orange-500" to="/login">Sign in</Link></span>} />;
}
