# Database initialization

MySQL runs the `.sql` files in this directory in name order, and only when it initializes an empty `mysql-data` volume.

| File | Contents |
| --- | --- |
| `001-demo-seed.sql` | Schema of the original course tables |
| `002-demo-admin.sql` | Demo merchant account (`admin` / `123456`) |
| `003-order-reliability.sql` | Changes made after the original course schema, each explained by a comment |
| `004-demo-menu.sql` | English demo menu: 30 dishes in 8 categories and 4 combos, with photos served by the frontends |
| `005-shop-status.sql` | The shop's open or closed status, which used to live only in Redis |

The backend integration tests start MySQL from these same files, so every migration is tested before it reaches a real database.

The files also run unchanged on TiDB, which the Render deployment uses: [deploy/init-database.sh](../../deploy/init-database.sh) applies them to a TiDB Cloud cluster. TiDB cannot index a column in the same `ALTER TABLE` that adds it, so a new migration keeps those in separate statements.

## Updating an existing database

A volume created before a file existed does not get it automatically. Apply each missing file once, with the stack running and before starting a backend that needs it, for example `005-shop-status.sql`:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < docker/mysql/init/005-shop-status.sql
```

Running a file a second time fails on its first statement, because the changes already exist. After `005-shop-status.sql` the shop is open; a merchant who had closed it closes it again. To start over with demo data instead, run `docker compose down -v`, which deletes the volume and every order in it.

To replace the menu of an existing database with the current demo menu, apply `004-demo-menu.sql` the same way. It deletes the existing categories, dishes, flavors, combos and shopping carts first, so do it only where the menu is demo data. Then remove the two cached lists that are not tied to a category, or the customer app keeps showing the old menu:

```bash
docker compose exec redis redis-cli -a "$(sed -n 's/^REDIS_PASSWORD=//p' .env)" --no-auth-warning -n 10 DEL dish_all setmealCache::all
```

Do not commit dumps containing real users, addresses, orders, credentials, or other private data.
