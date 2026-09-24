import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import Navbar from '@/components/Navbar';
import { cancelOrder, getOrderDetail, reminder, repetition } from '@/lib/api/order';
import { useAuthStore } from '@/store/auth';
import { Order, ORDER_STATUS } from '@/types';

export default function OrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const [order, setOrder] = useState<Order | null>(null);
  const load = async () => { if (id) setOrder((await getOrderDetail(Number(id))).data); };
  useEffect(() => { if (!authenticated) navigate('/login'); else void load(); }, [authenticated, id, navigate]);
  if (!order) return <div className="min-h-screen bg-gray-50"><Navbar /><p className="text-center py-20">Loading order...</p></div>;
  const cancel = async () => { if (confirm('Cancel this order?')) { await cancelOrder(order.id); toast.success('Order cancelled'); await load(); } };
  const remind = async () => { await reminder(order.id); toast.success('Reminder sent to the merchant'); };
  const reorder = async () => { await repetition(order.id); toast.success('Items added to cart'); navigate('/cart'); };
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-3xl mx-auto px-4 py-8 space-y-6"><Link to="/orders" className="text-gray-500">Back to orders</Link><section className="bg-white rounded-xl shadow p-6"><div className="flex justify-between"><div><p className="text-gray-500 text-sm">Order Number</p><h1 className="text-xl font-bold">{order.number}</h1></div><span className="text-orange-500 font-semibold">{ORDER_STATUS[order.status]}</span></div><div className="flex gap-3 mt-6">{order.status <= 2 && <button onClick={cancel} className="border border-red-500 text-red-500 px-4 py-2 rounded-lg">Cancel Order</button>}{order.status >= 3 && order.status <= 4 && <button onClick={remind} className="bg-orange-500 text-white px-4 py-2 rounded-lg">Remind Merchant</button>}{order.status === 5 && <button onClick={reorder} className="bg-orange-500 text-white px-4 py-2 rounded-lg">Order Again</button>}</div></section><section className="bg-white rounded-xl shadow p-6"><h2 className="font-semibold text-lg mb-4">Items</h2>{order.orderDetailList?.map((item) => <div key={item.id} className="flex justify-between border-t py-3"><span>{item.name} × {item.number}</span><span>${Number(item.amount * item.number).toFixed(2)}</span></div>)}<div className="flex justify-between border-t pt-4 font-bold"><span>Total</span><span>${Number(order.amount).toFixed(2)}</span></div></section><section className="bg-white rounded-xl shadow p-6 space-y-2"><h2 className="font-semibold text-lg mb-4">Delivery</h2><p>{order.consignee} · {order.phone}</p><p className="text-gray-600">{order.address}</p><p className="text-gray-500">Ordered {order.orderTime}</p></section></main></div>;
}
