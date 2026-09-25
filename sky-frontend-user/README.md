# Sky Take-Out Customer Frontend

Customer ordering application built with React, TypeScript, Vite, React Router, Zustand, Axios, and Tailwind CSS.

## Local development

The Spring Boot API must run on `http://localhost:8080`. Vite proxies `/user` requests to that server.

```bash
npm install
npm run dev
```

Open `http://localhost:3000`.

## Production build

```bash
npm run build
```

The static build is written to `dist/`. Configure the production web server with SPA fallback to `index.html` and proxy `/user` to the Spring Boot backend.

## Checkout behavior

The development environment does not require a third-party payment account. Its checkout calls the authenticated development simulation endpoint, which reuses the backend's existing payment-success, order-status, persistence, and merchant WebSocket notification workflow.
