import { Dish, Setmeal, DishFlavor } from '@/types';
import { useCartStore } from '@/store/cart';
import toast from 'react-hot-toast';

interface DishCardProps {
  item: Dish | Setmeal;
  type: 'dish' | 'setmeal';
}

export default function DishCard({ item, type }: DishCardProps) {
  const { addItem } = useCartStore();

  const handleAddToCart = () => {
    const cartItem: any = {
      id: Date.now(),
      name: item.name,
      image: item.image || '',
      number: 1,
      amount: item.price,
      price: item.price,
    };

    if (type === 'dish') {
      cartItem.dishId = item.id;
      // If dish has flavors, use the first one as default
      const dish = item as Dish;
      if (dish.flavors && dish.flavors.length > 0) {
        cartItem.dishFlavor = dish.flavors[0].name;
      }
    } else {
      cartItem.setmealId = item.id;
    }

    addItem(cartItem);
    toast.success('Added to cart!');
  };

  return (
    <div className="bg-white rounded-xl shadow-md overflow-hidden hover:shadow-lg transition-shadow">
      <div className="aspect-video bg-gray-200 relative">
        {item.image ? (
          <img
            src={item.image}
            alt={item.name}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-4xl bg-gray-100">
            {type === 'dish' ? '🍽️' : '🍱'}
          </div>
        )}
        {item.status === 0 && (
          <div className="absolute top-2 right-2 bg-red-500 text-white text-xs px-2 py-1 rounded">
            Sold Out
          </div>
        )}
      </div>
      <div className="p-4">
        <h3 className="text-lg font-semibold text-gray-900">{item.name}</h3>
        {item.description && (
          <p className="text-sm text-gray-500 mt-1 line-clamp-2">
            {item.description}
          </p>
        )}
        <div className="flex items-center justify-between mt-3">
          <span className="text-xl font-bold text-orange-500">
            ${Number(item.price).toFixed(2)}
          </span>
          <button
            onClick={handleAddToCart}
            disabled={item.status === 0}
            className="bg-orange-500 text-white px-4 py-2 rounded-lg hover:bg-orange-600 disabled:bg-gray-300 transition-colors text-sm font-medium"
          >
            + Add to Cart
          </button>
        </div>
      </div>
    </div>
  );
}
