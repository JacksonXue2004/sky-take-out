# Database initialization

MySQL runs the `.sql` files in this directory in name order, and only when it initializes an empty `mysql-data` volume.

| File | Contents |
| --- | --- |
| `001-demo-seed.sql` | Schema of the original course tables |
| `002-demo-admin.sql` | Demo merchant account (`admin` / `123456`) |
| `003-order-reliability.sql` | Changes made after the original course schema, each explained by a comment |
| `004-demo-menu.sql` | English demo menu: 30 dishes in 8 categories and 4 combos, with photos served by the frontends |

The backend integration tests start MySQL from these same files, so every migration is tested before it reaches a real database.

## Updating an existing database

A volume created before `003-order-reliability.sql` existed does not get it automatically. Apply it once, with the stack running:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < docker/mysql/init/003-order-reliability.sql
```

Running it a second time fails on the first statement, because the changes already exist. To start over with demo data instead, run `docker compose down -v`, which deletes the volume and every order in it.

To replace the menu of an existing database with the current demo menu, apply `004-demo-menu.sql` the same way. It deletes the existing categories, dishes, flavors, combos and shopping carts first, so do it only where the menu is demo data. Then remove the two cached lists that are not tied to a category, or the customer app keeps showing the old menu:

```bash
docker compose exec redis redis-cli -a "$(sed -n 's/^REDIS_PASSWORD=//p' .env)" --no-auth-warning -n 10 DEL dish_all setmealCache::all
```

Do not commit dumps containing real users, addresses, orders, credentials, or other private data.
