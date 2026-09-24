import { useEffect, useState } from 'react';
import Navbar from '@/components/Navbar';
import DishCard from '@/components/DishCard';
import { getCategoryList, getDishList, getSetmealList, getShopStatus } from '@/lib/api/restaurant';
import { Category, Dish, Setmeal } from '@/types';

export default function HomePage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [dishes, setDishes] = useState<Dish[]>([]);
  const [setmeals, setSetmeals] = useState<Setmeal[]>([]);
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [shopStatus, setShopStatus] = useState(1);
  const [loading, setLoading] = useState(true);

  const loadMenu = async (categoryId = 0) => {
    setLoading(true);
    try {
      const [dishRes, setmealRes] = await Promise.all([getDishList(categoryId), getSetmealList(categoryId)]);
      setDishes(dishRes.data || []);
      setSetmeals(setmealRes.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    Promise.all([getCategoryList(), getShopStatus()]).then(([categoryRes, statusRes]) => {
      setCategories(categoryRes.data || []);
      setShopStatus(statusRes.data ?? 1);
    });
    loadMenu();
  }, []);

  const selectCategory = (id: number | null) => {
    setActiveCategory(id);
    loadMenu(id ?? 0);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <header className="bg-gradient-to-r from-orange-500 to-red-500 text-white py-12 text-center">
        <h1 className="text-4xl md:text-5xl font-bold">Sky Take-Out</h1>
        <p className="text-xl mt-3 opacity-90">Fresh meals delivered fast</p>
        <span className="inline-block mt-4 bg-white/20 px-4 py-2 rounded-full">{shopStatus === 1 ? 'Open Now' : 'Closed'}</span>
      </header>
      <main className="max-w-7xl mx-auto px-4 py-8">
        <div className="flex flex-wrap gap-2 mb-8">
          <button onClick={() => selectCategory(null)} className={`px-4 py-2 rounded-full ${activeCategory === null ? 'bg-orange-500 text-white' : 'bg-white'}`}>All</button>
          {categories.map((category) => (
            <button key={category.id} onClick={() => selectCategory(category.id)} className={`px-4 py-2 rounded-full ${activeCategory === category.id ? 'bg-orange-500 text-white' : 'bg-white'}`}>{category.name}</button>
          ))}
        </div>
        {loading ? <p className="text-center py-16 text-gray-500">Loading menu...</p> : (
          <>
            {setmeals.length > 0 && <section className="mb-12"><h2 className="text-2xl font-bold mb-6">Value Set Meals</h2><div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">{setmeals.map((item) => <DishCard key={item.id} item={item} type="setmeal" />)}</div></section>}
            {dishes.length > 0 && <section><h2 className="text-2xl font-bold mb-6">Featured Dishes</h2><div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">{dishes.map((item) => <DishCard key={item.id} item={item} type="dish" />)}</div></section>}
            {dishes.length === 0 && setmeals.length === 0 && <p className="text-center py-16 text-gray-500">No menu items are available.</p>}
          </>
        )}
      </main>
    </div>
  );
}
