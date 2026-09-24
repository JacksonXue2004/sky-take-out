import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Navbar from '@/components/Navbar';
import { getAddressList } from '@/lib/api/address';
import { useAuthStore } from '@/store/auth';
import { AddressBook } from '@/types';

export default function AccountPage() {
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuthStore();
  const [addresses, setAddresses] = useState<AddressBook[]>([]);
  useEffect(() => { if (!isAuthenticated) navigate('/login'); else getAddressList().then((response) => setAddresses(response.data || [])); }, [isAuthenticated, navigate]);
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-4xl mx-auto px-4 py-8"><h1 className="text-2xl font-bold mb-6">My Account</h1><section className="bg-white rounded-xl shadow p-6 mb-6"><h2 className="font-semibold text-lg">{user?.email}</h2><p className="text-gray-500">Customer account</p></section><div className="grid sm:grid-cols-3 gap-4 mb-6"><Link to="/orders" className="bg-white rounded-xl shadow p-6 text-center font-medium">My Orders</Link><Link to="/cart" className="bg-white rounded-xl shadow p-6 text-center font-medium">Shopping Cart</Link><Link to="/account/address" className="bg-white rounded-xl shadow p-6 text-center font-medium">Manage Addresses</Link></div><section className="bg-white rounded-xl shadow p-6"><div className="flex justify-between mb-4"><h2 className="font-semibold text-lg">Saved Addresses</h2><Link to="/account/address/add" className="text-orange-500">Add New</Link></div>{addresses.map((address) => <p key={address.id} className="border-t py-3">{address.consignee} · {address.phone}<br /><span className="text-gray-500">{address.provinceName} {address.cityName} {address.districtName} {address.detail}</span></p>)}{!addresses.length && <p className="text-gray-500">No saved addresses.</p>}</section></main></div>;
}
