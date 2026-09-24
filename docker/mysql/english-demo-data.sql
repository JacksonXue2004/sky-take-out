SET NAMES utf8mb4;

UPDATE category SET name = CASE id
  WHEN 11 THEN 'Beverages'
  WHEN 12 THEN 'Rice & Staples'
  WHEN 13 THEN 'Popular Combos'
  WHEN 15 THEN 'Business Combos'
  WHEN 16 THEN 'Sichuan Grilled Fish'
  WHEN 17 THEN 'Sichuan Bullfrog'
  WHEN 18 THEN 'Steamed Specialties'
  WHEN 19 THEN 'Fresh Vegetables'
  WHEN 20 THEN 'Poached Fish'
  WHEN 21 THEN 'Soups'
  ELSE name
END;

UPDATE dish SET
  name = CASE id
    WHEN 46 THEN 'Wanglaoji Herbal Tea'
    WHEN 47 THEN 'Arctic Orange Soda'
    WHEN 48 THEN 'Snow Beer'
    WHEN 49 THEN 'Steamed Rice'
    WHEN 50 THEN 'Steamed Bun'
    WHEN 51 THEN 'Pickled Mustard Fish'
    WHEN 52 THEN 'Classic Pickled Bass'
    WHEN 53 THEN 'Sichuan Poached Carp'
    WHEN 54 THEN 'Stir-Fried Bok Choy'
    WHEN 55 THEN 'Garlic Napa Cabbage'
    WHEN 56 THEN 'Stir-Fried Broccoli'
    WHEN 57 THEN 'Flash-Fried Cabbage'
    WHEN 58 THEN 'Steamed Sea Bass'
    WHEN 59 THEN 'Dongpo Pork Knuckle'
    WHEN 60 THEN 'Pork Belly & Mustard'
    WHEN 61 THEN 'Chili Fish Head'
    WHEN 62 THEN 'Golden Bullfrog'
    WHEN 63 THEN 'Dry Pot Bullfrog'
    WHEN 64 THEN 'Spicy Bullfrog'
    WHEN 65 THEN '2.2 lb Grass Carp'
    WHEN 66 THEN 'Longsnout Catfish'
    WHEN 67 THEN '2.2 lb Catfish'
    WHEN 68 THEN 'Egg Drop Soup'
    WHEN 69 THEN 'Oyster Tofu Soup'
    ELSE name
  END,
  price = CASE id
    WHEN 46 THEN 2.99 WHEN 47 THEN 2.49 WHEN 48 THEN 4.99
    WHEN 49 THEN 1.99 WHEN 50 THEN 1.49
    WHEN 51 THEN 16.99 WHEN 52 THEN 18.99 WHEN 53 THEN 15.99
    WHEN 54 THEN 9.99 WHEN 55 THEN 10.99 WHEN 56 THEN 10.99 WHEN 57 THEN 9.99
    WHEN 58 THEN 22.99 WHEN 59 THEN 24.99 WHEN 60 THEN 18.99 WHEN 61 THEN 21.99
    WHEN 62 THEN 23.99 WHEN 63 THEN 22.99 WHEN 64 THEN 21.99
    WHEN 65 THEN 24.99 WHEN 66 THEN 29.99 WHEN 67 THEN 26.99
    WHEN 68 THEN 4.99 WHEN 69 THEN 6.99
    ELSE price
  END,
  description = CASE id
    WHEN 46 THEN 'Classic Chinese herbal tea.'
    WHEN 47 THEN 'Refreshing orange soda.'
    WHEN 48 THEN 'Crisp lager beer.'
    WHEN 49 THEN 'Steamed premium rice.'
    WHEN 50 THEN 'Soft steamed wheat bun.'
    WHEN 51 THEN 'Fish in a savory pickled mustard broth.'
    WHEN 52 THEN 'Sea bass with pickled mustard greens.'
    WHEN 53 THEN 'Grass carp in a spicy Sichuan broth.'
    WHEN 54 THEN 'Baby bok choy stir-fried to order.'
    WHEN 55 THEN 'Tender napa cabbage with fresh garlic.'
    WHEN 56 THEN 'Fresh broccoli stir-fried to order.'
    WHEN 57 THEN 'Cabbage flash-fried with aromatics.'
    WHEN 58 THEN 'Sea bass steamed with ginger and scallions.'
    WHEN 59 THEN 'Slow-braised pork knuckle in a rich sauce.'
    WHEN 60 THEN 'Pork belly with preserved mustard greens.'
    WHEN 61 THEN 'Fish head topped with chopped chili.'
    WHEN 62 THEN 'Bullfrog in a golden pickled broth.'
    WHEN 63 THEN 'Bullfrog and vegetables served dry-pot style.'
    WHEN 64 THEN 'Tender bullfrog in a spicy savory sauce.'
    WHEN 65 THEN 'Whole grass carp with vegetables.'
    WHEN 66 THEN 'Longsnout catfish with vegetables.'
    WHEN 67 THEN 'Whole catfish with vegetables.'
    WHEN 68 THEN 'Classic egg drop soup.'
    WHEN 69 THEN 'Tofu soup with oyster mushrooms.'
    ELSE description
  END;

UPDATE dish_flavor SET
  value = CASE name
    WHEN '甜味' THEN '["No Sugar","Light Sugar","Half Sugar","Extra Sugar","Full Sugar"]'
    WHEN '忌口' THEN '["No Scallions","No Garlic","No Cilantro","No Chili"]'
    WHEN '温度' THEN '["Hot","Warm","No Ice","Light Ice","Extra Ice"]'
    WHEN '辣度' THEN '["Not Spicy","Mild","Medium","Extra Spicy"]'
    ELSE value
  END,
  name = CASE name
    WHEN '甜味' THEN 'Sweetness'
    WHEN '忌口' THEN 'Dietary Preferences'
    WHEN '温度' THEN 'Temperature'
    WHEN '辣度' THEN 'Spice Level'
    ELSE name
  END;

UPDATE setmeal SET
  name = CASE id
    WHEN 12 THEN 'Two-Person Fish Combo'
    WHEN 13 THEN 'Family Dinner Combo'
    ELSE name
  END,
  description = CASE id
    WHEN 12 THEN 'A balanced fish dinner for two.'
    WHEN 13 THEN 'A generous family-style dinner.'
    ELSE description
  END;
