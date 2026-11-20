# CI/CD and Docker

How the applications are packaged, tested and deployed.

## Container ports

| Service | Container | Default host |
| --- | ---: | ---: |
| Merchant React frontend | 80 | 8081 |
| Customer React frontend | 80 | 3000 |
| Spring Boot backend | 8080 | Internal only |
| Actuator (health, metrics) | 9090 | Internal only |
| MySQL | 3306 | 3307 |
| Redis | 6379 | 6380 |

The Nginx containers preserve the current routes: merchant `/api/*` maps to `/admin/*`, `/ws/*` retains WebSocket upgrade headers, and customer `/user/*` and `/notify/*` map to the same backend paths. Actuator is not proxied; the backend healthcheck calls it from inside the container.

## Local setup

Install Docker Desktop, then create the ignored environment file:

```powershell
Copy-Item .env.docker.example .env
```

Replace every `replace-with-...` value.

When the `mysql-data` volume is empty, MySQL initializes it from `docker/mysql/init`: the demo schema and menu (`001`), the demo merchant account (`002`) and the later schema changes (`003`). A volume created before `003-order-reliability.sql` existed needs that file applied once; [docker/mysql/init/README.md](../docker/mysql/init/README.md) has the command.

Start and inspect the stack:

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Open the merchant portal at `http://localhost:8081` and customer app at `http://localhost:3000`. The local stack turns on the simulated checkout (`PAYMENT_SIMULATION_ENABLED`), because there is no Stripe account behind it.

Stop containers while preserving data:

```powershell
docker compose down
```

Do not use `docker compose down -v` unless permanent deletion of container database and Redis data is intended.

## CI

`.github/workflows/ci.yml` runs the backend unit and integration tests (the integration tests start MySQL 8.0 and Redis 7 with Testcontainers on the runner's Docker), packages the Java 8 backend, builds both React applications, and validates all three Docker images on Linux for pull requests and development pushes.

## Continuous delivery

`.github/workflows/publish-images.yml` publishes immutable images after changes reach `main`, after a `v*` tag, or by manual dispatch:

- `ghcr.io/jacksonxue2004/sky-backend`
- `ghcr.io/jacksonxue2004/sky-admin-web`
- `ghcr.io/jacksonxue2004/sky-customer-web`

Images receive commit-SHA tags. The default branch also publishes `latest`; version tags such as `v1.0.0` produce matching image tags.

## Server deployment

Copy `compose.production.yml`, `docker/backend/application.yml`, the `docker/mysql/init` directory and an ignored `.env` to the server. If packages are private, authenticate first:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u JacksonXue2004 --password-stdin
```

If the server's database was created before `003-order-reliability.sql`, apply that file before starting the new images; the new backend needs its columns and tables.

Deploy:

```bash
docker compose -f compose.production.yml pull
docker compose -f compose.production.yml up -d
docker compose -f compose.production.yml ps
```

`compose.production.yml` leaves the simulated checkout off. Set `PAYMENT_SIMULATION_ENABLED=true` in `.env` only for a public demo that takes no real money, since it lets every customer mark their own orders paid. Real payments need the `STRIPE_*` settings and a Stripe webhook pointing at `https://<host>/notify/stripe`.

For rollback, set `IMAGE_TAG` in `.env` to the previous release or `sha-<commit>`, then run the same pull and up commands. Application rollback does not replace database volumes, and an older image keeps working with the newer schema because `003` only adds columns, tables and indexes.
