import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import Navbar from '@/components/Navbar';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';

export default function CartPage() {
  const navigate = useNavigate();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const { items, totalCount, totalAmount, fetchCart, clearCart, updateQuantity, removeItem } = useCartStore();
  useEffect(() => { if (authenticated) void fetchCart(); }, [authenticated, fetchCart]);
  const checkout = () => {
    if (!items.length) return toast.error('Your cart is empty');
    if (!authenticated) { toast.error('Please login to checkout'); navigate('/login'); return; }
    navigate('/checkout');
  };
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-4xl mx-auto px-4 py-8"><h1 className="text-2xl font-bold mb-6">Your Cart ({totalCount})</h1>{!items.length ? <div className="bg-white rounded-xl shadow p-12 text-center"><p className="text-xl mb-6">Your cart is empty.</p><Link to="/" className="bg-orange-500 text-white px-6 py-3 rounded-lg">Browse Menu</Link></div> : <><div className="bg-white rounded-xl shadow divide-y">{items.map((item) => <div key={item.id} className="p-4 flex items-center gap-4"><img src={item.image || '/file.svg'} alt="" className="w-20 h-20 rounded-lg object-cover bg-gray-100" /><div className="flex-1"><h3 className="font-semibold">{item.name}</h3><p className="text-orange-500">${Number(item.amount ?? item.price ?? 0).toFixed(2)}</p></div><div className="flex items-center gap-3"><button onClick={() => updateQuantity(item.id, item.number - 1)} className="w-8 h-8 bg-gray-100 rounded-full">-</button><span>{item.number}</span><button onClick={() => updateQuantity(item.id, item.number + 1)} className="w-8 h-8 bg-gray-100 rounded-full">+</button></div><button onClick={() => removeItem(item.id)} className="text-red-500">Remove</button></div>)}</div><div className="bg-white rounded-xl shadow mt-6 p-6"><div className="flex justify-between mb-4"><button onClick={clearCart} className="text-red-500">Clear cart</button><strong className="text-xl text-orange-500">${Number(totalAmount).toFixed(2)}</strong></div><button onClick={checkout} className="w-full bg-orange-500 text-white py-3 rounded-lg">Proceed to Checkout</button></div></>}</main></div>;
}
