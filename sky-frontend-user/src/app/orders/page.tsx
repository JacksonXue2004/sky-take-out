'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { useAuthStore } from '@/store/auth';
import { getUserOrders } from '@/lib/api/order';
import { Order, ORDER_STATUS } from '@/types';
import Link from 'next/link';
import toast, { Toaster } from 'react-hot-toast';

export default function OrdersPage() {
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const [orders, setOrders] = useState<Order[]>([]);
  const [activeStatus, setActiveStatus] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
      return;
    }
    loadOrders();
  }, [isAuthenticated, activeStatus, router]);

  const loadOrders = async () => {
    try {
      setLoading(true);
      const res = await getUserOrders({
        page,
        pageSize: 10,
        status: activeStatus,
      });
      setOrders(res.data?.records || []);
    } catch (error) {
      console.error('Failed to load orders:', error);
    } finally {
      setLoading(false);
    }
  };

  const statusTabs = [
    { label: 'All', value: undefined },
    { label: 'Pending Payment', value: 1 },
    { label: 'Pending Confirm', value: 2 },
    { label: 'Confirmed', value: 3 },
    { label: 'Delivering', value: 4 },
    { label: 'Completed', value: 5 },
    { label: 'Cancelled', value: 6 },
  ];

  const getStatusColor = (status: number) => {
    switch (status) {
      case 1: return 'bg-yellow-100 text-yellow-800';
      case 2: return 'bg-blue-100 text-blue-800';
      case 3: return 'bg-purple-100 text-purple-800';
      case 4: return 'bg-cyan-100 text-cyan-800';
      case 5: return 'bg-green-100 text-green-800';
      case 6: return 'bg-gray-100 text-gray-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Toaster position="top-right" />
      <Navbar />

      <div className="max-w-4xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">📦 My Orders</h1>

        {/* Status Filter Tabs */}
        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
          {statusTabs.map((tab) => (
            <button
              key={tab.label}
              onClick={() => setActiveStatus(tab.value)}
              className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors ${
                activeStatus === tab.value
                  ? 'bg-orange-500 text-white'
                  : 'bg-white text-gray-600 hover:bg-orange-100'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Loading */}
        {loading && (
          <div className="text-center py-12">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
          </div>
        )}

        {/* Orders List */}
        {!loading && orders.length > 0 && (
          <div className="space-y-4">
            {orders.map((order) => (
              <Link
                key={order.id}
                href={`/orders/${order.id}`}
                className="block bg-white rounded-xl shadow-md hover:shadow-lg transition-shadow"
              >
                <div className="p-4">
                  <div className="flex justify-between items-start mb-3">
                    <div>
                      <p className="text-sm text-gray-500">
                        Order #{order.number}
                      </p>
                      <p className="text-sm text-gray-500">
                        {order.orderTime}
                      </p>
                    </div>
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(order.status)}`}
                    >
                      {ORDER_STATUS[order.status]}
                    </span>
                  </div>

                  {order.orderDetailList && order.orderDetailList.length > 0 && (
                    <div className="text-sm text-gray-600 mb-3">
                      {order.orderDetailList.slice(0, 2).map((detail) => (
                        <span key={detail.id} className="mr-2">
                          {detail.name} x{detail.number}
                        </span>
                      ))}
                      {order.orderDetailList.length > 2 && (
                        <span className="text-gray-400">
                          +{order.orderDetailList.length - 2} more
                        </span>
                      )}
                    </div>
                  )}

                  <div className="flex justify-between items-center pt-3 border-t">
                    <span className="text-gray-500 text-sm">
                      {order.address}
                    </span>
                    <span className="text-xl font-bold text-orange-500">
                      ${(order.amount / 100).toFixed(2)}
                    </span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}

        {/* Empty State */}
        {!loading && orders.length === 0 && (
          <div className="text-center py-16">
            <div className="text-6xl mb-4">📦</div>
            <h3 className="text-xl font-semibold text-gray-700 mb-2">
              No orders yet
            </h3>
            <p className="text-gray-500 mb-6">
              Start ordering your favorite food!
            </p>
            <Link
              href="/"
              className="inline-block bg-orange-500 text-white px-6 py-3 rounded-lg font-medium hover:bg-orange-600 transition-colors"
            >
              Browse Menu
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}