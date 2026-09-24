import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import Navbar from '@/components/Navbar';
import { getAddressList } from '@/lib/api/address';
import { simulatePayment, submitOrder } from '@/lib/api/order';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';
import { AddressBook } from '@/types';

export default function CheckoutPage() {
  const navigate = useNavigate();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const { items, totalAmount, fetchCart, clearLocalCart } = useCartStore();
  const [addresses, setAddresses] = useState<AddressBook[]>([]);
  const [addressId, setAddressId] = useState<number | null>(null);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);
  useEffect(() => {
    if (!authenticated) { navigate('/login'); return; }
    void fetchCart();
    getAddressList().then((list) => {
      setAddresses(list.data || []);
      setAddressId(null);
    });
  }, [authenticated, fetchCart, navigate]);
  const placeOrder = async () => {
    if (!addressId) return toast.error('Add or select a delivery address');
    if (!items.length) return toast.error('Your cart is empty');
    setSubmitting(true);
    try {
      const submitted = await submitOrder({ addressBookId: addressId, payMethod: 1, remark, tablewareStatus: 1, tablewareNumber: 1, deliveryStatus: 1, packAmount: 0, amount: totalAmount });
      await simulatePayment(submitted.data.orderNumber);
      clearLocalCart();
      toast.success('Order placed and payment simulated');
      navigate(`/order/success?id=${submitted.data.id}`);
    } finally { setSubmitting(false); }
  };
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-3xl mx-auto px-4 py-8 space-y-6"><h1 className="text-2xl font-bold">Checkout</h1><section className="bg-white rounded-xl shadow p-6"><div className="flex justify-between mb-4"><h2 className="font-semibold text-lg">Delivery Address</h2><Link to="/account/address/add" className="text-orange-500">Add Address</Link></div>{addresses.map((address) => <label key={address.id} className={`block border-2 rounded-lg p-4 mb-3 cursor-pointer ${addressId === address.id ? 'border-orange-500 bg-orange-50' : 'border-gray-200'}`}><input type="radio" checked={addressId === address.id} onChange={() => setAddressId(address.id)} className="mr-3" />{address.consignee} · {address.phone}<p className="ml-7 text-sm text-gray-500">{address.provinceName} {address.cityName} {address.districtName} {address.detail}</p></label>)}{!addresses.length && <p className="text-gray-500">No address yet. Add one to continue.</p>}</section><section className="bg-white rounded-xl shadow p-6"><h2 className="font-semibold text-lg mb-4">Order Summary</h2>{items.map((item) => <div key={item.id} className="flex justify-between py-2"><span>{item.name} × {item.number}</span><span>${Number((item.amount ?? 0) * item.number).toFixed(2)}</span></div>)}<div className="border-t mt-3 pt-3 flex justify-between font-bold"><span>Total</span><span className="text-orange-500">${Number(totalAmount).toFixed(2)}</span></div></section><section className="bg-white rounded-xl shadow p-6"><label>Special Instructions<textarea value={remark} onChange={(event) => setRemark(event.target.value)} className="mt-2 w-full border rounded-lg p-3" /></label><p className="mt-4 text-sm text-gray-500">Portfolio demo checkout: no external payment method is required. Clicking below marks the order paid through the backend's normal paid-order workflow.</p></section><button disabled={submitting || !addressId || !items.length} onClick={placeOrder} className="w-full bg-orange-500 text-white py-4 rounded-xl disabled:bg-gray-300">{submitting ? 'Placing Order...' : 'Place Order'}</button></main></div>;
}
