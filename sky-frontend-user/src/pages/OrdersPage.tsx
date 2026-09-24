import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Navbar from '@/components/Navbar';
import { getUserOrders } from '@/lib/api/order';
import { useAuthStore } from '@/store/auth';
import { Order, ORDER_STATUS } from '@/types';

export default function OrdersPage() {
  const navigate = useNavigate();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const [orders, setOrders] = useState<Order[]>([]);
  const [status, setStatus] = useState<number | undefined>();
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    if (!authenticated) { navigate('/login'); return; }
    setLoading(true);
    getUserOrders({ page: 1, pageSize: 20, status }).then((response) => setOrders(response.data?.records || [])).finally(() => setLoading(false));
  }, [authenticated, navigate, status]);
  const tabs = [{ label: 'All', value: undefined }, { label: 'Pending', value: 2 }, { label: 'Confirmed', value: 3 }, { label: 'Delivering', value: 4 }, { label: 'Completed', value: 5 }];
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-4xl mx-auto px-4 py-8"><h1 className="text-2xl font-bold mb-6">My Orders</h1><div className="flex gap-2 mb-6 overflow-auto">{tabs.map((tab) => <button key={tab.label} onClick={() => setStatus(tab.value)} className={`px-4 py-2 rounded-full whitespace-nowrap ${status === tab.value ? 'bg-orange-500 text-white' : 'bg-white'}`}>{tab.label}</button>)}</div>{loading ? <p className="text-center py-12">Loading orders...</p> : <div className="space-y-4">{orders.map((order) => <Link key={order.id} to={`/orders/${order.id}`} className="block bg-white rounded-xl shadow p-5 hover:shadow-md"><div className="flex justify-between mb-3"><div><p className="font-semibold">Order {order.number}</p><p className="text-sm text-gray-500">{order.orderTime}</p></div><span className="text-orange-500 font-medium">{ORDER_STATUS[order.status]}</span></div><div className="flex justify-between border-t pt-3"><span className="text-gray-500">{order.consignee} · {order.address}</span><strong>${Number(order.amount).toFixed(2)}</strong></div></Link>)}{!orders.length && <div className="bg-white rounded-xl p-12 text-center"><p className="mb-6 text-gray-500">No orders found.</p><Link to="/" className="bg-orange-500 text-white px-5 py-3 rounded-lg">Browse Menu</Link></div>}</div>}</main></div>;
}
