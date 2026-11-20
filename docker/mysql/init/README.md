# Database initialization

MySQL runs the `.sql` files in this directory in name order, and only when it initializes an empty `mysql-data` volume.

| File | Contents |
| --- | --- |
| `001-demo-seed.sql` | Schema and non-sensitive English demo menu |
| `002-demo-admin.sql` | Demo merchant account (`admin` / `123456`) |
| `003-order-reliability.sql` | Changes made after the original course schema, each explained by a comment |

The backend integration tests start MySQL from these same files, so every migration is tested before it reaches a real database.

## Updating an existing database

A volume created before `003-order-reliability.sql` existed does not get it automatically. Apply it once, with the stack running:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < docker/mysql/init/003-order-reliability.sql
```

Running it a second time fails on the first statement, because the changes already exist. To start over with demo data instead, run `docker compose down -v`, which deletes the volume and every order in it.

Do not commit dumps containing real users, addresses, orders, credentials, or other private data.
