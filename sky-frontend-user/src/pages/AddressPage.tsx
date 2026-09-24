import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import Navbar from '@/components/Navbar';
import { addAddress, deleteAddress, getAddressList, setDefaultAddress } from '@/lib/api/address';
import { useAuthStore } from '@/store/auth';
import { AddressBook } from '@/types';

const emptyForm = { consignee: '', sex: '1', phone: '', provinceCode: 'US', provinceName: '', cityCode: '', cityName: '', districtCode: '', districtName: '', detail: '', label: 'Home' };

export default function AddressPage({ startAdding = false }: { startAdding?: boolean }) {
  const navigate = useNavigate();
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const [addresses, setAddresses] = useState<AddressBook[]>([]);
  const [adding, setAdding] = useState(startAdding);
  const [form, setForm] = useState(emptyForm);
  const load = async () => setAddresses((await getAddressList()).data || []);
  useEffect(() => { if (!authenticated) navigate('/login'); else void load(); }, [authenticated, navigate]);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await addAddress(form);
    toast.success('Address saved');
    setForm(emptyForm); setAdding(false); await load();
  };
  const makeDefault = async (id: number) => { await setDefaultAddress(id); toast.success('Default address updated'); await load(); };
  const remove = async (id: number) => { if (confirm('Delete this address?')) { await deleteAddress(id); await load(); } };
  return <div className="min-h-screen bg-gray-50"><Navbar /><main className="max-w-3xl mx-auto px-4 py-8"><div className="flex justify-between items-center mb-6"><div><Link to="/account" className="text-gray-500">Back to account</Link><h1 className="text-2xl font-bold">Delivery Addresses</h1></div><button onClick={() => setAdding(!adding)} className="bg-orange-500 text-white px-4 py-2 rounded-lg">{adding ? 'Cancel' : 'Add Address'}</button></div>{adding && <form onSubmit={submit} className="bg-white rounded-xl shadow p-6 grid sm:grid-cols-2 gap-4 mb-6"><Field label="Recipient" value={form.consignee} onChange={(value) => setForm({ ...form, consignee: value })} /><Field label="Phone" value={form.phone} onChange={(value) => setForm({ ...form, phone: value })} /><Field label="State / Province" value={form.provinceName} onChange={(value) => setForm({ ...form, provinceName: value, provinceCode: value })} /><Field label="City" value={form.cityName} onChange={(value) => setForm({ ...form, cityName: value, cityCode: value })} /><Field label="District / Area" value={form.districtName} onChange={(value) => setForm({ ...form, districtName: value, districtCode: value })} /><Field label="Label" value={form.label} onChange={(value) => setForm({ ...form, label: value })} /><label className="sm:col-span-2">Street address<input required value={form.detail} onChange={(e) => setForm({ ...form, detail: e.target.value })} className="mt-1 w-full border rounded-lg px-3 py-2" /></label><button className="sm:col-span-2 bg-orange-500 text-white py-3 rounded-lg">Save Address</button></form>}<div className="space-y-3">{addresses.map((address) => <div key={address.id} className="bg-white rounded-xl shadow p-5 flex justify-between gap-4"><div><div className="font-semibold">{address.consignee} · {address.phone} {address.isDefault === 1 && <span className="text-orange-500 text-sm">Default</span>}</div><p className="text-gray-600">{address.provinceName} {address.cityName} {address.districtName} {address.detail}</p></div><div className="flex gap-3 text-sm">{address.isDefault !== 1 && <button onClick={() => makeDefault(address.id)} className="text-orange-500">Set default</button>}<button onClick={() => remove(address.id)} className="text-red-500">Delete</button></div></div>)}{!addresses.length && !adding && <p className="bg-white rounded-xl p-10 text-center text-gray-500">No saved addresses.</p>}</div></main></div>;
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label>{label}<input required value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full border rounded-lg px-3 py-2" /></label>;
}
