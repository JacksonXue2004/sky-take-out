'use client';

import { useEffect, useState } from 'react';
import Navbar from '@/components/Navbar';
import DishCard from '@/components/DishCard';
import { getCategoryList, getDishList, getSetmealList, getShopStatus } from '@/lib/api/restaurant';
import { Category, Dish, Setmeal } from '@/types';
import { Toaster } from 'react-hot-toast';

export default function HomePage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [dishes, setDishes] = useState<Dish[]>([]);
  const [setmeals, setSetmeals] = useState<Setmeal[]>([]);
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [shopStatus, setShopStatus] = useState<number>(1); // 1: 营业中, 0: 打烊中
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [catRes, shopRes] = await Promise.all([
        getCategoryList(),
        getShopStatus(),
      ]);

      setCategories(catRes.data || []);
      setShopStatus(shopRes.data ?? 1);

      // 获取所有菜品和套餐
      const [dishRes, setmealRes] = await Promise.all([
        getDishList(0),
        getSetmealList(0),
      ]);

      setDishes(dishRes.data || []);
      setSetmeals(setmealRes.data || []);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCategoryChange = async (categoryId: number | null) => {
    setActiveCategory(categoryId);
    try {
      const [dishRes, setmealRes] = await Promise.all([
        getDishList(categoryId ?? 0),
        getSetmealList(categoryId ?? 0),
      ]);
      setDishes(dishRes.data || []);
      setSetmeals(setmealRes.data || []);
    } catch (error) {
      console.error('Failed to load category data:', error);
    }
  };

  // 筛选当前分类下的菜品和套餐
  const filteredDishes = activeCategory 
    ? dishes.filter(d => d.categoryId === activeCategory)
    : dishes;
  const filteredSetmeals = activeCategory 
    ? setmeals.filter(s => s.categoryId === activeCategory)
    : setmeals;

  return (
    <div className="min-h-screen bg-gray-50">
      <Toaster position="top-right" />
      <Navbar />

      {/* 店铺头部区域 */}
      <div className="bg-gradient-to-r from-orange-500 to-red-500 text-white py-12">
        <div className="max-w-7xl mx-auto px-4 text-center">
          <div className="text-5xl mb-4">🍜</div>
          <h1 className="text-4xl md:text-5xl font-bold mb-4">
            Sky Take-Out
          </h1>
          <p className="text-xl mb-4 opacity-90">
            Fresh & Delicious Meals Delivered Fast
          </p>
          <div className="inline-flex items-center gap-2 bg-white/20 px-4 py-2 rounded-full">
            <span className={`w-2 h-2 rounded-full ${shopStatus === 1 ? 'bg-green-400' : 'bg-red-400'}`}></span>
            <span className="font-medium">
              {shopStatus === 1 ? 'Open Now' : 'Closed'}
            </span>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* 分类筛选 */}
        {categories.length > 0 && (
          <div className="mb-8">
            <h2 className="text-lg font-semibold text-gray-700 mb-4">
              Browse by Category
            </h2>
            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => handleCategoryChange(null)}
                className={`px-4 py-2 rounded-full font-medium transition-colors ${
                  activeCategory === null
                    ? 'bg-orange-500 text-white'
                    : 'bg-white text-gray-700 hover:bg-orange-100'
                }`}
              >
                All
              </button>
              {categories.map((cat) => (
                <button
                  key={cat.id}
                  onClick={() => handleCategoryChange(cat.id)}
                  className={`px-4 py-2 rounded-full font-medium transition-colors ${
                    activeCategory === cat.id
                      ? 'bg-orange-500 text-white'
                      : 'bg-white text-gray-700 hover:bg-orange-100'
                  }`}
                >
                  {cat.name}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* 加载状态 */}
        {loading && (
          <div className="text-center py-12">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
            <p className="mt-4 text-gray-500">Loading menu...</p>
          </div>
        )}

        {/* 套餐区域 */}
        {!loading && filteredSetmeals.length > 0 && (
          <section className="mb-12">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">
              🍱 Value Set Meals
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              {filteredSetmeals.map((setmeal) => (
                <DishCard key={setmeal.id} item={setmeal} type="setmeal" />
              ))}
            </div>
          </section>
        )}

        {/* 菜品区域 */}
        {!loading && filteredDishes.length > 0 && (
          <section className="mb-12">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">
              🍽️ Featured Dishes
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              {filteredDishes.map((dish) => (
                <DishCard key={dish.id} item={dish} type="dish" />
              ))}
            </div>
          </section>
        )}

        {/* 空状态 */}
        {!loading && filteredDishes.length === 0 && filteredSetmeals.length === 0 && (
          <div className="text-center py-16">
            <div className="text-6xl mb-4">🍽️</div>
            <h3 className="text-xl font-semibold text-gray-700 mb-2">
              No dishes available
            </h3>
            <p className="text-gray-500">
              {shopStatus === 0 
                ? 'We are currently closed. Please come back later!' 
                : 'Please check back later for our delicious menu items.'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
