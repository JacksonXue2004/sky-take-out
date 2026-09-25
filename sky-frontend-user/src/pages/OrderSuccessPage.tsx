import { Link, useSearchParams } from 'react-router-dom';
import Navbar from '@/components/Navbar';

export default function OrderSuccessPage() {
  const [params] = useSearchParams();
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-2xl mx-auto px-4 py-16"><div className="bg-white rounded-xl shadow p-12 text-center"><div className="w-16 h-16 mx-auto mb-6 rounded-full bg-green-100 text-green-600 flex items-center justify-center text-3xl">✓</div><h1 className="text-3xl font-bold mb-4">Order Placed Successfully</h1><p className="text-gray-600 mb-2">Payment was simulated successfully for this development environment.</p><p className="text-gray-500 mb-8">Order ID: {params.get('id')}</p><div className="flex justify-center gap-4"><Link to="/" className="border border-orange-500 text-orange-500 px-5 py-3 rounded-lg">Continue Shopping</Link><Link to="/orders" className="bg-orange-500 text-white px-5 py-3 rounded-lg">View Orders</Link></div></div></main></div>;
}
