'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { useAuthStore } from '@/store/auth';
import { getOrderDetail, cancelOrder, reminder } from '@/lib/api/order';
import { Order, ORDER_STATUS } from '@/types';
import toast, { Toaster } from 'react-hot-toast';
import Link from 'next/link';

export default function OrderDetailPage() {
  const params = useParams();
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
      return;
    }
    if (params.id) {
      loadOrder();
    }
  }, [isAuthenticated, params.id, router]);

  const loadOrder = async () => {
    try {
      setLoading(true);
      const res = await getOrderDetail(Number(params.id));
      setOrder(res.data);
    } catch (error) {
      console.error('Failed to load order:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!order) return;
    if (!confirm('Are you sure you want to cancel this order?')) return;

    try {
      setCancelling(true);
      await cancelOrder(order.id);
      toast.success('Order cancelled successfully');
      loadOrder();
    } catch (error) {
      // Error handled
    } finally {
      setCancelling(false);
    }
  };

  const handleReminder = async () => {
    if (!order) return;
    try {
      await reminder(order.id);
      toast.success('Reminder sent to restaurant!');
    } catch (error) {
      // Error handled
    }
  };

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

  const showCancelButton = order && order.status <= 2 && order.status !== 6;
  const showReminderButton = order && order.status >= 3 && order.status <= 4;
  const showRepetitionButton = order && order.status === 5;

  // Order status timeline
  const statusTimeline = [
    { status: 1, label: 'Order Placed', time: order?.orderTime },
    { status: 2, label: 'Confirmed', time: null },
    { status: 3, label: 'Preparing', time: null },
    { status: 4, label: 'Delivering', time: null },
    { status: 5, label: 'Delivered', time: order?.deliveryTime },
  ];

  return (
    <div className="min-h-screen bg-gray-50">
      <Toaster position="top-right" />
      <Navbar />

      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="flex items-center gap-4 mb-6">
          <button
            onClick={() => router.back()}
            className="text-gray-500 hover:text-gray-700"
          >
            ← Back
          </button>
          <h1 className="text-2xl font-bold text-gray-900">Order Details</h1>
        </div>

        {loading && (
          <div className="text-center py-12">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
          </div>
        )}

        {!loading && order && (
          <div className="space-y-6">
            {/* Order Status */}
            <div className="bg-white rounded-xl shadow-md p-6">
              <div className="flex justify-between items-center mb-4">
                <div>
                  <p className="text-sm text-gray-500">Order Number</p>
                  <p className="font-semibold">{order.number}</p>
                </div>
                <span
                  className={`px-3 py-1 rounded-full text-sm font-medium ${getStatusColor(order.status)}`}
                >
                  {ORDER_STATUS[order.status]}
                </span>
              </div>

              {/* Status Timeline */}
              <div className="mt-6">
                <div className="flex justify-between">
                  {statusTimeline.map((step) => (
                    <div key={step.status} className="flex flex-col items-center flex-1">
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-sm ${
                          order.status >= step.status
                            ? 'bg-orange-500'
                            : 'bg-gray-300'
                        }`}
                      >
                        {step.status}
                      </div>
                      <span className="text-xs mt-1 text-center">{step.label}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-3 mt-6">
                {showCancelButton && (
                  <button
                    onClick={handleCancel}
                    disabled={cancelling}
                    className="flex-1 border border-red-500 text-red-500 py-2 rounded-lg hover:bg-red-500 hover:text-white transition-colors"
                  >
                    {cancelling ? 'Cancelling...' : 'Cancel Order'}
                  </button>
                )}
                {showReminderButton && (
                  <button
                    onClick={handleReminder}
                    className="flex-1 bg-orange-500 text-white py-2 rounded-lg hover:bg-orange-600 transition-colors"
                  >
                    🔔 Remind Restaurant
                  </button>
                )}
                {showRepetitionButton && (
                  <Link
                    href={`/repetition/${order.id}`}
                    className="flex-1 bg-orange-500 text-white py-2 rounded-lg hover:bg-orange-600 transition-colors text-center"
                  >
                    🔁 Order Again
                  </Link>
                )}
              </div>
            </div>

            {/* Order Items */}
            <div className="bg-white rounded-xl shadow-md p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">
                📋 Order Items
              </h2>
              <div className="divide-y">
                {order.orderDetailList?.map((detail) => (
                  <div key={detail.id} className="py-3 flex gap-4">
                    <div className="w-16 h-16 bg-gray-200 rounded-lg overflow-hidden flex-shrink-0">
                      {detail.image ? (
                        <img
                          src={detail.image}
                          alt={detail.name}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-2xl">
                          🍽️
                        </div>
                      )}
                    </div>
                    <div className="flex-1">
                      <h3 className="font-medium text-gray-900">{detail.name}</h3>
                      {detail.dishFlavor && (
                        <p className="text-sm text-gray-500">{detail.dishFlavor}</p>
                      )}
                      <p className="text-sm text-gray-500">Qty: {detail.number}</p>
                    </div>
                    <div className="text-right">
                      <p className="font-semibold">
                        ${((detail.amount * detail.number) / 100).toFixed(2)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
              <div className="border-t pt-4 mt-4 flex justify-between font-semibold">
                <span>Total</span>
                <span className="text-xl text-orange-500">
                  ${(order.amount / 100).toFixed(2)}
                </span>
              </div>
            </div>

            {/* Delivery Info */}
            <div className="bg-white rounded-xl shadow-md p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">
                📍 Delivery Information
              </h2>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Order Time</span>
                  <span>{order.orderTime}</span>
                </div>
                {order.checkoutTime && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Payment Time</span>
                    <span>{order.checkoutTime}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500">Consignee</span>
                  <span>{order.consignee}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Phone</span>
                  <span>{order.phone}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Delivery Address</span>
                  <span className="text-right">{order.address}</span>
                </div>
                {order.remark && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Note</span>
                    <span className="text-right">{order.remark}</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}