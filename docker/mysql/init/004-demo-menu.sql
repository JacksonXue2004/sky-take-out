-- English demo menu: 30 dishes in 8 categories and 4 combos. The photos are served by both
-- frontends from /images/dishes; sky-frontend-user/public/images/dishes/CREDITS.md lists their
-- authors and licences.
--
-- On a new volume this runs after 001-003. Applied to an existing database, it replaces the whole
-- menu: categories, dishes, flavors, combos, and the shopping carts that point at them.
--
-- Menus are listed newest first, so each row is created a minute before the previous one and
-- appears in the order written here.

DELETE FROM shopping_cart;
DELETE FROM setmeal_dish;
DELETE FROM setmeal;
DELETE FROM dish_flavor;
DELETE FROM dish;
DELETE FROM category;

INSERT INTO category (id, type, name, sort, status, create_time, update_time, create_user, update_user) VALUES
  (101, 1, 'Sichuan Classics', 1, 1, NOW(), NOW(), 1, 1),
  (102, 1, 'Fish and Seafood', 2, 1, NOW(), NOW(), 1, 1),
  (103, 1, 'Meat and Poultry', 3, 1, NOW(), NOW(), 1, 1),
  (104, 1, 'Vegetables', 4, 1, NOW(), NOW(), 1, 1),
  (105, 1, 'Rice and Noodles', 5, 1, NOW(), NOW(), 1, 1),
  (106, 1, 'Dumplings and Dim Sum', 6, 1, NOW(), NOW(), 1, 1),
  (107, 1, 'Soups', 7, 1, NOW(), NOW(), 1, 1),
  (108, 1, 'Drinks and Desserts', 8, 1, NOW(), NOW(), 1, 1),
  (109, 2, 'Popular Combos', 9, 1, NOW(), NOW(), 1, 1),
  (110, 2, 'Business Combos', 10, 1, NOW(), NOW(), 1, 1);

INSERT INTO dish (id, name, category_id, price, image, description, status, create_time, update_time, create_user, update_user) VALUES
  (101, 'Kung Pao Chicken', 101, 14.99, '/images/dishes/kung-pao-chicken.jpg', 'Diced chicken wok-fried with peanuts, dried chilies and Sichuan peppercorns in a sweet and tangy sauce.', 1, NOW(), NOW(), 1, 1),
  (102, 'Mapo Tofu', 101, 12.99, '/images/dishes/mapo-tofu.jpg', 'Soft tofu and minced beef in a numbing, spicy chili bean sauce.', 1, NOW() - INTERVAL 1 MINUTE, NOW() - INTERVAL 1 MINUTE, 1, 1),
  (103, 'Twice Cooked Pork', 101, 15.99, '/images/dishes/twice-cooked-pork.jpg', 'Pork belly simmered, sliced thin, then stir-fried with leeks, green peppers and fermented bean paste.', 1, NOW() - INTERVAL 2 MINUTE, NOW() - INTERVAL 2 MINUTE, 1, 1),
  (104, 'Fish Fragrant Pork', 101, 14.99, '/images/dishes/fish-fragrant-pork.jpg', 'Shredded pork with wood ear mushrooms in a garlicky sweet, sour and spicy sauce. Named for the sauce; it contains no fish.', 1, NOW() - INTERVAL 3 MINUTE, NOW() - INTERVAL 3 MINUTE, 1, 1),
  (105, 'Chongqing Chicken', 101, 16.99, '/images/dishes/chongqing-chicken.jpg', 'Crispy fried chicken bites tossed with a pile of dried chilies and Sichuan peppercorns.', 1, NOW() - INTERVAL 4 MINUTE, NOW() - INTERVAL 4 MINUTE, 1, 1),
  (106, 'Sichuan Boiled Beef', 101, 18.99, '/images/dishes/sichuan-boiled-beef.jpg', 'Tender beef slices poached in a fiery chili broth over vegetables, finished with sizzling oil.', 1, NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 5 MINUTE, 1, 1),
  (107, 'Pickled Cabbage Fish', 102, 19.99, '/images/dishes/pickled-cabbage-fish.jpg', 'Fish fillets in a tangy broth of pickled mustard greens, chilies and peppercorns.', 1, NOW() - INTERVAL 6 MINUTE, NOW() - INTERVAL 6 MINUTE, 1, 1),
  (108, 'Chili Fish Head', 102, 24.99, '/images/dishes/chili-fish-head.jpg', 'A whole fish head steamed under red and yellow chopped chilies, with noodles to soak up the sauce.', 1, NOW() - INTERVAL 7 MINUTE, NOW() - INTERVAL 7 MINUTE, 1, 1),
  (109, 'Red Braised Pork', 103, 17.99, '/images/dishes/red-braised-pork.jpg', 'Pork belly braised slowly in soy sauce, rice wine and rock sugar until glossy and tender.', 1, NOW() - INTERVAL 8 MINUTE, NOW() - INTERVAL 8 MINUTE, 1, 1),
  (110, 'Sweet and Sour Pork', 103, 14.99, '/images/dishes/sweet-and-sour-pork.jpg', 'Crispy pork with bell peppers in a bright sweet and sour glaze.', 1, NOW() - INTERVAL 9 MINUTE, NOW() - INTERVAL 9 MINUTE, 1, 1),
  (111, 'Peking Duck', 103, 32.99, '/images/dishes/peking-duck.jpg', 'Half a roast duck with crisp lacquered skin, carved to order, with pancakes, scallions and hoisin sauce.', 1, NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 10 MINUTE, 1, 1),
  (112, 'Cantonese BBQ Pork', 103, 15.99, '/images/dishes/cantonese-bbq-pork.jpg', 'Char siu: pork shoulder marinated in honey and five-spice, roasted until caramelized.', 1, NOW() - INTERVAL 11 MINUTE, NOW() - INTERVAL 11 MINUTE, 1, 1),
  (113, 'Beef with Broccoli', 103, 15.99, '/images/dishes/beef-with-broccoli.jpg', 'Sliced beef and broccoli stir-fried in a savory oyster sauce.', 1, NOW() - INTERVAL 12 MINUTE, NOW() - INTERVAL 12 MINUTE, 1, 1),
  (114, 'Di San Xian', 104, 12.99, '/images/dishes/di-san-xian.jpg', 'Eggplant, potato and green pepper wok-fried in a garlicky soy glaze.', 1, NOW() - INTERVAL 13 MINUTE, NOW() - INTERVAL 13 MINUTE, 1, 1),
  (115, 'Tomato Egg Stir Fry', 104, 10.99, '/images/dishes/tomato-egg-stir-fry.jpg', 'Soft scrambled eggs with ripe tomatoes, a favorite of Chinese home cooking.', 1, NOW() - INTERVAL 14 MINUTE, NOW() - INTERVAL 14 MINUTE, 1, 1),
  (116, 'Vegetable Delight', 104, 12.99, '/images/dishes/vegetable-delight.jpg', 'Buddha''s delight: tofu skin, mushrooms, snow peas and carrots in a light sauce. Vegetarian.', 1, NOW() - INTERVAL 15 MINUTE, NOW() - INTERVAL 15 MINUTE, 1, 1),
  (117, 'Steamed Rice', 105, 1.99, '/images/dishes/steamed-rice.jpg', 'A bowl of steamed jasmine rice.', 1, NOW() - INTERVAL 16 MINUTE, NOW() - INTERVAL 16 MINUTE, 1, 1),
  (118, 'Yangzhou Fried Rice', 105, 11.99, '/images/dishes/yangzhou-fried-rice.jpg', 'Fried rice with egg, shrimp, barbecued pork, peas and scallions.', 1, NOW() - INTERVAL 17 MINUTE, NOW() - INTERVAL 17 MINUTE, 1, 1),
  (119, 'Dan Dan Noodles', 105, 11.99, '/images/dishes/dan-dan-noodles.jpg', 'Wheat noodles in chili oil and sesame sauce, topped with seasoned minced pork.', 1, NOW() - INTERVAL 18 MINUTE, NOW() - INTERVAL 18 MINUTE, 1, 1),
  (120, 'Shrimp Chow Mein', 105, 13.99, '/images/dishes/shrimp-chow-mein.jpg', 'Egg noodles stir-fried with shrimp, bean sprouts and greens.', 1, NOW() - INTERVAL 19 MINUTE, NOW() - INTERVAL 19 MINUTE, 1, 1),
  (121, 'Beef Chow Fun', 105, 14.99, '/images/dishes/beef-chow-fun.jpg', 'Wide rice noodles seared in a hot wok with beef, bean sprouts and scallions.', 1, NOW() - INTERVAL 20 MINUTE, NOW() - INTERVAL 20 MINUTE, 1, 1),
  (122, 'Pork Dumplings', 106, 9.99, '/images/dishes/pork-dumplings.jpg', 'Twelve boiled pork and cabbage dumplings, with black vinegar for dipping.', 1, NOW() - INTERVAL 21 MINUTE, NOW() - INTERVAL 21 MINUTE, 1, 1),
  (123, 'Soup Dumplings', 106, 10.99, '/images/dishes/soup-dumplings.jpg', 'Eight xiaolongbao: thin-skinned steamed dumplings filled with pork and hot broth.', 1, NOW() - INTERVAL 22 MINUTE, NOW() - INTERVAL 22 MINUTE, 1, 1),
  (124, 'Scallion Pancakes', 106, 6.99, '/images/dishes/scallion-pancakes.jpg', 'Flaky pan-fried flatbread layered with scallions, with a dipping sauce.', 1, NOW() - INTERVAL 23 MINUTE, NOW() - INTERVAL 23 MINUTE, 1, 1),
  (125, 'Hot and Sour Soup', 107, 5.99, '/images/dishes/hot-and-sour-soup.jpg', 'A peppery, vinegary soup with tofu, bamboo shoots, mushrooms and egg ribbons.', 1, NOW() - INTERVAL 24 MINUTE, NOW() - INTERVAL 24 MINUTE, 1, 1),
  (126, 'Egg Drop Soup', 107, 4.99, '/images/dishes/egg-drop-soup.jpg', 'Silky egg ribbons in a light broth with scallions.', 1, NOW() - INTERVAL 25 MINUTE, NOW() - INTERVAL 25 MINUTE, 1, 1),
  (127, 'Fresh Soy Milk', 108, 3.49, '/images/dishes/fresh-soy-milk.jpg', 'Freshly made soy milk, served hot or iced.', 1, NOW() - INTERVAL 26 MINUTE, NOW() - INTERVAL 26 MINUTE, 1, 1),
  (128, 'Pearl Milk Tea', 108, 5.49, '/images/dishes/pearl-milk-tea.jpg', 'Black milk tea with chewy tapioca pearls.', 1, NOW() - INTERVAL 27 MINUTE, NOW() - INTERVAL 27 MINUTE, 1, 1),
  (129, 'Mango Pudding', 108, 5.99, '/images/dishes/mango-pudding.jpg', 'Chilled mango pudding with fresh mango and a splash of cream.', 1, NOW() - INTERVAL 28 MINUTE, NOW() - INTERVAL 28 MINUTE, 1, 1),
  (130, 'Egg Tarts', 108, 4.99, '/images/dishes/egg-tarts.jpg', 'Two flaky pastry shells filled with a smooth, lightly sweet egg custard.', 1, NOW() - INTERVAL 29 MINUTE, NOW() - INTERVAL 29 MINUTE, 1, 1);

INSERT INTO dish_flavor (id, dish_id, name, value) VALUES
  (101, 101, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (102, 101, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (103, 102, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (104, 103, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (105, 104, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (106, 104, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (107, 105, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (108, 106, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (109, 107, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (110, 108, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (111, 113, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (112, 114, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (113, 116, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (114, 118, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (115, 119, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (116, 120, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (117, 121, 'Dietary Preferences', '["No Scallions","No Garlic","No Cilantro","No Chili"]'),
  (118, 125, 'Spice Level', '["Not Spicy","Mild","Medium","Extra Spicy"]'),
  (119, 127, 'Sweetness', '["No Sugar","Light Sugar","Half Sugar","Extra Sugar","Full Sugar"]'),
  (120, 127, 'Temperature', '["Hot","Warm","No Ice","Light Ice","Extra Ice"]'),
  (121, 128, 'Sweetness', '["No Sugar","Light Sugar","Half Sugar","Extra Sugar","Full Sugar"]'),
  (122, 128, 'Temperature', '["Hot","Warm","No Ice","Light Ice","Extra Ice"]');

INSERT INTO setmeal (id, category_id, name, price, status, description, image, create_time, update_time, create_user, update_user) VALUES
  (101, 109, 'Sichuan Feast for 2', 49.99, 1, 'Chongqing Chicken, Pickled Cabbage Fish and Di San Xian, with two bowls of rice.', '/images/dishes/chongqing-chicken.jpg', NOW(), NOW(), 1, 1),
  (102, 109, 'Dim Sum for 2', 34.99, 1, 'Soup dumplings, pork dumplings and scallion pancakes, with two pearl milk teas.', '/images/dishes/soup-dumplings.jpg', NOW() - INTERVAL 1 MINUTE, NOW() - INTERVAL 1 MINUTE, 1, 1),
  (103, 110, 'Kung Pao Lunch Set', 19.99, 1, 'Kung Pao Chicken with steamed rice and a hot and sour soup.', '/images/dishes/kung-pao-chicken.jpg', NOW() - INTERVAL 2 MINUTE, NOW() - INTERVAL 2 MINUTE, 1, 1),
  (104, 110, 'Mapo Tofu Lunch Set', 16.99, 1, 'Mapo Tofu with steamed rice and an egg drop soup.', '/images/dishes/mapo-tofu.jpg', NOW() - INTERVAL 3 MINUTE, NOW() - INTERVAL 3 MINUTE, 1, 1);

INSERT INTO setmeal_dish (id, setmeal_id, dish_id, name, price, copies) VALUES
  (101, 101, 105, 'Chongqing Chicken', 16.99, 1),
  (102, 101, 107, 'Pickled Cabbage Fish', 19.99, 1),
  (103, 101, 114, 'Di San Xian', 12.99, 1),
  (104, 101, 117, 'Steamed Rice', 1.99, 2),
  (105, 102, 123, 'Soup Dumplings', 10.99, 1),
  (106, 102, 122, 'Pork Dumplings', 9.99, 1),
  (107, 102, 124, 'Scallion Pancakes', 6.99, 1),
  (108, 102, 128, 'Pearl Milk Tea', 5.49, 2),
  (109, 103, 101, 'Kung Pao Chicken', 14.99, 1),
  (110, 103, 117, 'Steamed Rice', 1.99, 1),
  (111, 103, 125, 'Hot and Sour Soup', 5.99, 1),
  (112, 104, 102, 'Mapo Tofu', 12.99, 1),
  (113, 104, 117, 'Steamed Rice', 1.99, 1),
  (114, 104, 126, 'Egg Drop Soup', 4.99, 1);
