'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { useAuthStore } from '@/store/auth';
import { useCartStore } from '@/store/cart';
import { getAddressList, getDefaultAddress } from '@/lib/api/address';
import { submitOrder, payOrder } from '@/lib/api/order';
import { AddressBook } from '@/types';
import toast, { Toaster } from 'react-hot-toast';
import Link from 'next/link';

export default function CheckoutPage() {
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const { items, totalAmount, fetchCart, clearCart: clearLocalCart } = useCartStore();

  const [addresses, setAddresses] = useState<AddressBook[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState(1); // 1: WeChat, 2: Alipay (we'll map to Stripe)
  const [remark, setRemark] = useState('');
  const [tablewareStatus, setTablewareStatus] = useState(1);
  const [tablewareNumber, setTablewareNumber] = useState(1);
  const [deliveryStatus, setDeliveryStatus] = useState(1);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
      return;
    }
    if (items.length === 0) {
      router.push('/cart');
      return;
    }
    loadAddresses();
  }, [isAuthenticated, items.length, router]);

  const loadAddresses = async () => {
    try {
      const [allRes, defaultRes] = await Promise.all([
        getAddressList(),
        getDefaultAddress(),
      ]);
      const allAddresses = allRes.data || [];
      setAddresses(allAddresses);

      // Select default address
      if (defaultRes.data) {
        setSelectedAddressId(defaultRes.data.id);
      } else if (allAddresses.length > 0) {
        setSelectedAddressId(allAddresses[0].id);
      }
    } catch (error) {
      console.error('Failed to load addresses:', error);
    }
  };

  const handleSubmitOrder = async () => {
    if (!selectedAddressId) {
      toast.error('Please select a delivery address');
      return;
    }

    try {
      setSubmitting(true);

      // 1. Submit order
      const submitRes = await submitOrder({
        addressBookId: selectedAddressId,
        payMethod: paymentMethod,
        remark,
        tablewareStatus,
        tablewareNumber,
        deliveryStatus,
      });

      const orderNumber = submitRes.data.orderNumber;

      // 2. Create Stripe payment session
      const paymentRes = await payOrder(orderNumber);

      // 3. Clear cart
      clearLocalCart();

      // 4. Redirect to Stripe Checkout
      if (paymentRes.data?.checkoutUrl) {
        toast.success('Order placed! Redirecting to payment...');
        window.location.href = paymentRes.data.checkoutUrl;
      } else {
        // If no checkout URL, go to order success page
        router.push(`/order/success?orderNumber=${orderNumber}`);
      }
    } catch (error) {
      // Error handled by interceptor
    } finally {
      setSubmitting(false);
    }
  };

  const selectedAddress = addresses.find((a) => a.id === selectedAddressId);

  return (
    <div className="min-h-screen bg-gray-50">
      <Toaster position="top-right" />
      <Navbar />

      <div className="max-w-3xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Checkout</h1>

        <div className="space-y-6">
          {/* Delivery Address */}
          <div className="bg-white rounded-xl shadow-md p-6">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-lg font-semibold text-gray-900">
                📍 Delivery Address
              </h2>
              <Link
                href="/account/address"
                className="text-orange-500 text-sm hover:text-orange-600"
              >
                Manage Addresses
              </Link>
            </div>

            {addresses.length === 0 ? (
              <div className="text-center py-6">
                <p className="text-gray-500 mb-4">No addresses saved</p>
                <Link
                  href="/account/address/add"
                  className="inline-block bg-orange-500 text-white px-4 py-2 rounded-lg text-sm"
                >
                  + Add Address
                </Link>
              </div>
            ) : (
              <div className="space-y-3">
                {addresses.map((addr) => (
                  <label
                    key={addr.id}
                    className={`flex items-start gap-3 p-4 rounded-lg border-2 cursor-pointer transition-colors ${
                      selectedAddressId === addr.id
                        ? 'border-orange-500 bg-orange-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={selectedAddressId === addr.id}
                      onChange={() => setSelectedAddressId(addr.id)}
                      className="mt-1"
                    />
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-gray-900">
                          {addr.consignee}
                        </span>
                        <span className="text-gray-500 text-sm">
                          {addr.phone}
                        </span>
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
                  </label>
                ))}
              </div>
            )}
          </div>

          {/* Order Items Summary */}
          <div className="bg-white rounded-xl shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">
              📋 Order Summary
            </h2>
            <div className="space-y-2 mb-4">
              {items.map((item) => (
                <div key={item.id} className="flex justify-between text-sm">
                  <span className="text-gray-600">
                    {item.name} x{item.number}
                    {item.dishFlavor && ` (${item.dishFlavor})`}
                  </span>
                  <span className="font-medium">
                    ${((item.amount * item.number) / 100).toFixed(2)}
                  </span>
                </div>
              ))}
            </div>
            <div className="border-t pt-4 flex justify-between font-semibold">
              <span>Total</span>
              <span className="text-orange-500 text-xl">
                ${(totalAmount / 100).toFixed(2)}
              </span>
            </div>
          </div>

          {/* Delivery Options */}
          <div className="bg-white rounded-xl shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">
              🚀 Delivery Options
            </h2>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Delivery Time
              </label>
              <div className="flex gap-4">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    checked={deliveryStatus === 1}
                    onChange={() => setDeliveryStatus(1)}
                  />
                  <span>ASAP (Recommended)</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    checked={deliveryStatus === 0}
                    onChange={() => setDeliveryStatus(0)}
                  />
                  <span>Choose Time</span>
                </label>
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Tableware
              </label>
              <div className="flex gap-4">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    checked={tablewareStatus === 1}
                    onChange={() => setTablewareStatus(1)}
                  />
                  <span>By Meal Quantity</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    checked={tablewareStatus === 0}
                    onChange={() => setTablewareStatus(0)}
                  />
                  <span>Choose Quantity</span>
                </label>
              </div>
              {tablewareStatus === 0 && (
                <input
                  type="number"
                  min="1"
                  value={tablewareNumber}
                  onChange={(e) => setTablewareNumber(Number(e.target.value))}
                  className="mt-2 w-24 px-3 py-2 border border-gray-300 rounded-lg"
                />
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Special Instructions
              </label>
              <textarea
                value={remark}
                onChange={(e) => setRemark(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 outline-none"
                rows={2}
                placeholder="Any special requests..."
              />
            </div>
          </div>

          {/* Payment */}
          <div className="bg-white rounded-xl shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">
              💳 Payment Method
            </h2>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 p-4 border-2 rounded-lg cursor-pointer flex-1 justify-center bg-gradient-to-r from-blue-500 to-purple-500 text-white">
                <input
                  type="radio"
                  className="text-white"
                  checked={paymentMethod === 1}
                  onChange={() => setPaymentMethod(1)}
                />
                <span>Pay with Card (Stripe)</span>
              </label>
            </div>
          </div>

          {/* Submit */}
          <button
            onClick={handleSubmitOrder}
            disabled={submitting || !selectedAddressId}
            className="w-full bg-orange-500 text-white py-4 rounded-xl text-lg font-medium hover:bg-orange-600 disabled:bg-gray-300 transition-colors"
          >
            {submitting ? 'Placing Order...' : `Place Order - $${(totalAmount / 100).toFixed(2)}`}
          </button>
        </div>
      </div>
    </div>
  );
}