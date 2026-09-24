'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { useAuthStore } from '@/store/auth';
import { getAddressList } from '@/lib/api/address';
import { AddressBook } from '@/types';
import Link from 'next/link';
import toast, { Toaster } from 'react-hot-toast';

export default function AccountPage() {
  const router = useRouter();
  const { isAuthenticated, user, fetchUser } = useAuthStore();
  const [addresses, setAddresses] = useState<AddressBook[]>([]);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
      return;
    }
    fetchUser();
    loadAddresses();
  }, [isAuthenticated, fetchUser, router]);

  const loadAddresses = async () => {
    try {
      const res = await getAddressList();
      setAddresses(res.data || []);
    } catch (error) {
      console.error('Failed to load addresses:', error);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Toaster position="top-right" />
      <Navbar />

      <div className="max-w-4xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">👤 My Account</h1>

        {/* User Info */}
        <div className="bg-white rounded-xl shadow-md p-6 mb-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Personal Information
          </h2>
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 bg-orange-500 rounded-full flex items-center justify-center text-white text-2xl">
              {user?.email?.[0].toUpperCase() || 'U'}
            </div>
            <div>
              <h3 className="font-semibold text-xl">{user?.email}</h3>
              <p className="text-gray-500">Member since today</p>
            </div>
          </div>
        </div>

        {/* Quick Links */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <Link
            href="/orders"
            className="bg-white rounded-xl shadow-md p-4 text-center hover:shadow-lg transition-shadow"
          >
            <div className="text-3xl mb-2">📦</div>
            <p className="font-medium">My Orders</p>
          </Link>
          <Link
            href="/cart"
            className="bg-white rounded-xl shadow-md p-4 text-center hover:shadow-lg transition-shadow"
          >
            <div className="text-3xl mb-2">🛒</div>
            <p className="font-medium">Shopping Cart</p>
          </Link>
          <Link
            href="/account/address"
            className="bg-white rounded-xl shadow-md p-4 text-center hover:shadow-lg transition-shadow"
          >
            <div className="text-3xl mb-2">📍</div>
            <p className="font-medium">Addresses</p>
          </Link>
          <Link
            href="/login"
            className="bg-white rounded-xl shadow-md p-4 text-center hover:shadow-lg transition-shadow"
          >
            <div className="text-3xl mb-2">⚙️</div>
            <p className="font-medium">Settings</p>
          </Link>
        </div>

        {/* Saved Addresses */}
        <div className="bg-white rounded-xl shadow-md p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Saved Addresses
            </h2>
            <Link
              href="/account/address/add"
              className="text-orange-500 text-sm hover:text-orange-600 font-medium"
            >
              + Add New
            </Link>
          </div>

          {addresses.length === 0 ? (
            <p className="text-gray-500 text-center py-6">
              No addresses saved yet
            </p>
          ) : (
            <div className="space-y-3">
              {addresses.map((addr) => (
                <div
                  key={addr.id}
                  className="border border-gray-200 rounded-lg p-4"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{addr.consignee}</span>
                        <span className="text-gray-500 text-sm">{addr.phone}</span>
                        {addr.isDefault === 1 && (
                          <span className="bg-orange-100 text-orange-600 text-xs px-2 py-0.5 rounded">
                            Default
                          </span>
                        )}
                        {addr.label && (
                          <span className="bg-gray-100 text-gray-600 text-xs px-2 py-0.5 rounded">
                            {addr.label}
                          </span>
                        )}
                      </div>
                      <p className="text-gray-600 text-sm mt-1">
                        {addr.provinceName} {addr.cityName}{' '}
                        {addr.districtName} {addr.detail}
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <Link
                        href={`/account/address/edit/${addr.id}`}
                        className="text-orange-500 text-sm hover:text-orange-600"
                      >
                        Edit
                      </Link>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}