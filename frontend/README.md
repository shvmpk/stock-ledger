# StockLedger Frontend

React 19 + TypeScript + Vite single-page application for StockLedger. The SPA is a dense,
operational interface for the inventory ledger, order queues, shipping, returns, stock counts,
history, and owner-only settings.

The backend is the source of truth. The SPA never calculates inventory mutations locally; it sends
commands to the API and re-reads the affected queries.

## Prerequisites

- [Bun](https://bun.sh/) 1.2 or newer
- Java 21 and the backend repository at the workspace root
- Docker (for PostgreSQL) if you want to run the seeded backend locally
- A local `.env` file containing a Base64-encoded 32-byte `PLATFORM_CREDENTIALS_KEY` for the backend

## Quick start

From the repository root:

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Create the local platform credentials key once (gitignored)
[ -f .env ] || printf 'PLATFORM_CREDENTIALS_KEY=%s\n' "$(openssl rand -base64 32)" > .env
chmod 600 .env

# 3. Start the seeded backend on http://localhost:8080
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

In another terminal:

```bash
cd frontend
bun install
bun run dev
```

Open <http://localhost:5173>.

The Vite dev server proxies `/api` and `/actuator` to `http://localhost:8080`, so the browser uses
same-origin requests and the backend's HttpOnly session cookie works without CORS configuration.

### Demo accounts

The `dev` profile seeds two accounts:

| Role | Email | Password |
| :--- | :--- | :--- |
| Owner | `owner@stockledger.io` | `OwnerPass2026!` |
| Staff | `staff@stockledger.io` | `StaffPass2026!` |

The login page also has one-click demo credential buttons. Owner-only routes are hidden from staff
and guarded again on the server.

## Scripts

Run these from `frontend/`:

| Command | What it does |
| :--- | :--- |
| `bun run dev` | Start the Vite dev server with HMR and the backend proxy. |
| `bun run test` | Run the full Vitest suite once. |
| `bun run test:watch` | Run Vitest in watch mode. |
| `bun run typecheck` | Type-check with `tsc --noEmit`. |
| `bun run lint` | Run ESLint over the project. |
| `bun run build` | Type-check and create the production bundle in `dist/`. |
| `bun run preview` | Serve the production bundle locally. |

The frontend gate is:

```bash
bun run test && bun run typecheck && bun run lint && bun run build
```

## Configuration

Copy `.env.example` to `.env` only if the API is not reachable through the Vite proxy:

```bash
VITE_API_BASE_URL=/api/v1
```

The default is `/api/v1`. The API client sends `credentials: 'include'` on every request so the
backend session cookie is preserved. If you point the SPA at a different origin, that origin must
allow the frontend origin with credentials and must expose the same API contract.

## Routes and access

| Route | Screen | Access |
| :--- | :--- | :--- |
| `/login` | Sign in | Public |
| `/stock/products` | Catalog, variant ledger, receiving | Owner + Staff |
| `/stock/low-stock` | Low-stock and out-of-stock warnings | Owner + Staff |
| `/stock/count` | Stock-count sessions and shelf audit | Owner + Staff |
| `/orders/new` | New/pending queue | Owner + Staff |
| `/orders/waiting` | Waiting-for-stock queue | Owner + Staff |
| `/orders/ready` | Ready-to-ship queue | Owner + Staff |
| `/orders/shipped` | Shipped queue | Owner + Staff |
| `/orders/returns` | Returns and cancellations | Owner + Staff |
| `/history/movements` | Append-only stock ledger | Owner + Staff |
| `/history/shipping` | Monthly shipping summary | Owner only |
| `/settings/rates` | Carrier price list | Owner only |
| `/settings/platforms` | Shopify/WooCommerce sync status | Owner only |
| `/settings/users` | User administration | Owner only |
| `/settings/exports` | CSV export center | Owner only |

Unknown routes render a not-found page. Staff visiting an owner-only route see a forbidden page
rather than a redirect loop.

## Testing

The suite uses:

- **Vitest** for unit and component tests
- **React Testing Library** and `user-event` for accessible interactions
- **MSW 2** for API integration with realistic fixtures
- **vitest-axe** for automated accessibility checks on the login, catalog, order form, dispatch,
  queue, and shipping-rate screens

There is intentionally **no Playwright** or browser automation. The final end-to-end journey is a
manual browser checklist in [FRONTEND.md](../FRONTEND.md#manual-e2e-checklist-phase-123). Run it
against the seeded backend before declaring a release ready.

Useful test facts:

- jsdom uses `http://localhost:3000` as its URL; MSW handlers use relative `/api/v1/...` paths.
- `onUnhandledRequest: 'error'` makes accidental API calls fail loudly.
- Route tests use `MemoryRouter` + `useRoutes` rather than React Router's data router to avoid
  jsdom/undici `AbortSignal` incompatibilities.
- Mantine notifications are cleared and MSW state is reset after every test.

## Production build

```bash
bun run build
bun run preview
```

Deploy the contents of `dist/` behind a static host. The host must proxy `/api` to the backend and
serve `index.html` for client-side routes. Session authentication still requires the backend cookie
to be sent with API requests.

## Currency

The deployment has one implicit currency. All money is presented as GBP/en-GB through
`src/lib/format/money.ts`; there is no currency selector, conversion, or `/api/v1/meta` endpoint.

## Project layout

```text
frontend/src/
  auth/                 Session provider, guards, auth context
  components/           Shared feedback, data, domain, and layout components
  features/             auth, stock, orders, history, settings pages and modals
  lib/api/              Typed API client, endpoints, errors, response types
  lib/format/           GBP money, dates, quantities
  lib/query/            Query client and query keys
  test/                 MSW server/handlers/fixtures, render helper, axe types
  theme/                Mantine theme, tokens, fonts, global CSS
  routes.tsx            Lazy route tree with auth and owner guards
```

## Troubleshooting

- **401 on every request** — make sure the backend is running on port 8080 and you are using the
  Vite dev server (or a proxy with the same origin). Sign in again after restarting the backend.
- **Empty catalog or queues** — start the backend with `SPRING_PROFILES_ACTIVE=dev`; the seed only
  runs in the dev profile.
- **`MSW: unhandled request` during tests** — add or update the handler in
  `src/test/handlers.ts`; tests intentionally fail on unexpected network calls.
- **Port already in use** — Vite uses 5173 by default; stop the other process or pass
  `--port <port>` to `bun run dev`.
