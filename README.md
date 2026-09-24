# Sky Take-Out

[![CI](https://github.com/JacksonXue2004/sky-take-out/actions/workflows/ci.yml/badge.svg)](https://github.com/JacksonXue2004/sky-take-out/actions/workflows/ci.yml)

A full-stack restaurant ordering platform with separate customer and merchant experiences. Customers can browse an English menu, manage a cart and delivery addresses, place an order, and complete a portfolio-safe simulated payment. Merchants receive real-time WebSocket notifications and can accept, dispatch, and complete orders.

## Tech stack

- Customer: React 19, TypeScript, Vite, Zustand, Tailwind CSS
- Merchant: React 18, TypeScript, Vite, Ant Design, ECharts
- Backend: Java 8, Spring Boot 2.7, MyBatis, JWT, WebSocket
- Data: MySQL 8 and Redis 7
- Delivery: Docker Compose, Nginx, GitHub Actions, GitHub Container Registry

## Architecture and order flow

```text
Customer React :3000 ─┐
                      ├─ Nginx ─ Spring Boot :8080 ─ MySQL
Merchant React :8081 ─┘                    └─ Redis
         ▲                                      │
         └──────── WebSocket order alerts ──────┘
```

```text
Customer places an order
→ Spring Boot validates and stores it
→ simulated payment invokes the normal paid-order workflow
→ merchant receives a WebSocket notification
→ merchant accepts, dispatches, and completes the order
```

## Quick start with Docker

### Requirements

- Windows 10/11, macOS, or Linux
- Docker Desktop or Docker Engine with Compose
- Git

### 1. Clone and configure

```bash
git clone https://github.com/JacksonXue2004/sky-take-out.git
cd sky-take-out
cp .env.docker.example .env
```

On Windows PowerShell, use:

```powershell
git clone https://github.com/JacksonXue2004/sky-take-out.git
Set-Location sky-take-out
Copy-Item .env.docker.example .env
```

Open `.env` and replace every `replace-with-...` value. These values are local secrets and must not be committed.

### 2. Start the complete application

```bash
docker compose up -d --build
docker compose ps
```

The first build downloads base images and may take several minutes. Wait until MySQL, Redis, and the backend report `healthy`.

### 3. Open the applications

| Application | URL | Credentials |
| --- | --- | --- |
| Customer app | http://localhost:3000 | Create an account in the UI |
| Merchant portal | http://localhost:8081 | `admin` / `123456` |

The included demo database contains only non-sensitive English menu data. Prices and UI amounts are displayed in US dollars.

## Five-minute product walkthrough

1. Open the customer app and create an account.
2. Add an item from the English menu to the cart.
3. Open the cart and proceed to checkout.
4. Add a US delivery address and place the order.
5. The portfolio checkout simulates a successful payment; no payment card is required.
6. Open the merchant portal in another browser window and sign in.
7. Open **Orders**. The new order appears under **Pending Acceptance** and also generates a WebSocket notification.
8. Select **Accept**, then **Start Delivery**, and finally **Complete**.
9. Return to **My Orders** in the customer app and confirm that the order is completed.

## Services and ports

| Service | Host port | Purpose |
| --- | ---: | --- |
| Customer React | 3000 | Customer ordering experience |
| Merchant React | 8081 | Merchant operations portal |
| MySQL | 3307 | Optional host database access |
| Redis | 6380 | Optional host cache access |
| Spring Boot | internal only | Reached through the frontend Nginx proxies |

The customer proxy preserves `/user/*` and `/notify/*`. The merchant proxy maps `/api/*` to the existing `/admin/*` endpoints and preserves WebSocket upgrades on `/ws/*`.

## Useful commands

```bash
# View status
docker compose ps

# Follow backend logs
docker compose logs -f backend

# Rebuild after code changes
docker compose up -d --build

# Stop while keeping MySQL and Redis data
docker compose down
```

Do not run `docker compose down -v` unless you intentionally want to permanently delete the container database and Redis volumes.

## Local development without Docker

### Backend

Start local MySQL on port 3306 and Redis on port 6379. Configure the ignored `sky-server/src/main/resources/application-dev.yml`, then run `com.sky.SkyApplication` from IntelliJ IDEA or package the Maven project.

### Merchant React app

```bash
cd sky-frontend-admin
npm install
npm run dev
```

### Customer React app

```bash
cd sky-frontend-user
npm install
npm run dev
```

## CI/CD

Every pull request and development push runs:

- Maven backend packaging
- Merchant React production build
- Customer React production build
- Linux validation builds for all three Docker images

After changes reach `main`, a version tag such as `v1.0.0` or a manual workflow dispatch can publish immutable images to GitHub Container Registry. See [the detailed CI/CD and Docker guide](docs/CI-CD-DOCKER.md).

## Repository structure

```text
sky-frontend-admin/   Merchant React application
sky-frontend-user/    Customer React application
sky-server/           Spring Boot application
sky-common/           Shared backend utilities
sky-pojo/             Backend entities, DTOs, and VOs
docker/               Runtime configuration and safe demo seed
.github/workflows/    CI and image publishing workflows
```

## Notes

- The simulated payment endpoint is available only in the development profile and reuses the backend's normal payment-success and merchant-notification workflow.
- Database schemas and public API contracts remain owned by the Spring Boot backend.
- No production credentials or real customer data are included in the repository.
