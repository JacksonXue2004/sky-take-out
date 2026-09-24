# CI/CD and Docker

This infrastructure packages the existing applications without changing their API contracts, authentication, order workflow, WebSocket path, or database schema.

## Container ports

| Service | Container | Default host |
| --- | ---: | ---: |
| Merchant React frontend | 80 | 8081 |
| Customer React frontend | 80 | 3000 |
| Spring Boot backend | 8080 | Internal only |
| MySQL | 3306 | 3307 |
| Redis | 6379 | 6380 |

The Nginx containers preserve the current routes: merchant `/api/*` maps to `/admin/*`, `/ws/*` retains WebSocket upgrade headers, and customer `/user/*` and `/notify/*` map to the same backend paths.

## Local setup

Install Docker Desktop, then create the ignored environment file:

```powershell
Copy-Item .env.docker.example .env
```

Replace every `replace-with-...` value.

The repository does not generate or alter the database schema. Export the existing database before the first container start:

```powershell
mysqldump -u root -p --routines --triggers --single-transaction sky_take_out > docker/mysql/init/001-sky-take-out.sql
```

The dump is ignored by Git and is imported only when the `mysql-data` volume is empty.

Start and inspect the stack:

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Open the merchant portal at `http://localhost:8081` and customer app at `http://localhost:3000`.

Stop containers while preserving data:

```powershell
docker compose down
```

Do not use `docker compose down -v` unless permanent deletion of container database and Redis data is intended.

## CI

`.github/workflows/ci.yml` packages the Java 8 backend, builds both React applications, and validates all three Docker images on Linux for pull requests and development pushes.

## Continuous delivery

`.github/workflows/publish-images.yml` publishes immutable images after changes reach `main`, after a `v*` tag, or by manual dispatch:

- `ghcr.io/jacksonxue2004/sky-backend`
- `ghcr.io/jacksonxue2004/sky-admin-web`
- `ghcr.io/jacksonxue2004/sky-customer-web`

Images receive commit-SHA tags. The default branch also publishes `latest`; version tags such as `v1.0.0` produce matching image tags.

## Server deployment

Copy `compose.production.yml`, `docker/backend/application.yml`, an ignored `.env`, and the private first-time database dump to the server. If packages are private, authenticate first:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u JacksonXue2004 --password-stdin
```

Deploy:

```bash
docker compose -f compose.production.yml pull
docker compose -f compose.production.yml up -d
docker compose -f compose.production.yml ps
```

For rollback, set `IMAGE_TAG` in `.env` to the previous release or `sha-<commit>`, then run the same pull and up commands. Application rollback does not replace database volumes.
