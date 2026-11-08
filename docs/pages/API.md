# API reference

Every HTTP endpoint the backend serves. Access is enforced by the server: **OWNER** covers financial, administrative, and configuration data; **STAFF** shares all operational work. Webhook intake is the only public surface, and even that requires a valid vendor signature.

Responses are JSON through the app's standard success envelope. Errors are never stack traces or ad-hoc strings — they are RFC 7807 `ProblemDetail` documents with a stable `code`, a plain-language `detail`, and an `invalidParams` list for field-level validation failures (with HTTP 400 when input is bad, 404 for unknown resources, 409 when a refused save conflicts with real state). API-level examples: see the ledger and refusal notes below.

## Authentication (`/api/v1/auth`)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Public | Authenticates credentials and sets an `HttpOnly` session cookie. Records `passwordVerifiedAt` in the session for the 10-minute forced-change window. |
| `GET` | `/api/v1/auth/me` | Authenticated | Returns the current profile (`id`, `email`, `fullName`, `role`, `active`, `mustChangePassword`). |
| `POST` | `/api/v1/auth/password` | Authenticated | Changes the current password. `currentPassword` is optional only while `mustChangePassword=true` and the session was verified less than 10 minutes ago. |
| `POST` | `/api/v1/auth/logout` | Authenticated | Invalidates the HTTP session and clears cookies. |

### Password policy

Minimum 12 characters, no composition rules, no periodic rotation, maximum 72 bytes (BCrypt). The new password is rejected when it appears in the bundled top-10,000 common-password blocklist or contains the user's email local part or name tokens. Owner-issued passwords on user creation and password reset are always temporary: the account is flagged `must_change_password=true` and the owner sees the generated value exactly once.

### Authentication error codes

| HTTP | Code | Meaning |
| :--- | :--- | :--- |
| `401` | `UNAUTHORIZED` | Missing or expired session, or invalid login credentials. |
| `400` | `WEAK_PASSWORD` | Policy violation; `invalidParams` lists every failed rule under `newPassword` or `password`. |
| `400` | `INVALID_CURRENT_PASSWORD` | The supplied current password is wrong. Deliberately 400, not 401, so the SPA does not treat it as a dead session. |
| `400` | `CURRENT_PASSWORD_REQUIRED` | A forced change is older than the 10-minute verification window and the request omitted `currentPassword`. |
| `409` | `SELF_PASSWORD_RESET_NOT_ALLOWED` | Owners must change their own password through the account screen, not the admin reset action. |
| `403` | `PASSWORD_CHANGE_REQUIRED` | `MustChangePasswordFilter` blocked a protected endpoint until the password is changed. |
| `403` | `FORBIDDEN` | The authenticated role lacks access (for example STAFF reading the security log). |

## Catalog & products (`/api/v1/products`)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/products` | OWNER, STAFF | Full product list with nested variants and inventory balances. Retired products and variants are excluded unless `includeRetired=true`. `costPrice` is served to OWNER only. Search, category, and sort are applied by the frontend. |
| `GET` | `/api/v1/products/{id}` | OWNER, STAFF | One product with all of its variants — including retired ones, so history and direct links still work — plus prices, optional weight and reorder level, and inventory balances. `costPrice` is served to OWNER only. |
| `POST` | `/api/v1/products` | OWNER | Creates a product with one or more variants and a zero-stock inventory row per variant. Cost, weight, and reorder level may be omitted and are stored as `NULL`. Duplicate SKUs, negative values, a blank name, or an empty variant list return `400 VALIDATION_FAILED` with field-level `invalidParams`. |
| `PUT` | `/api/v1/products/{id}` | OWNER | Updates product details and the supplied variants. Variants with an `id` are updated, variants without one are added with zero stock, and omitted existing variants are left untouched (no hard delete). |
| `POST` | `/api/v1/products/{id}/retire` | OWNER | Retires the product without touching its variants. Repeating the call changes nothing and appends no history. |
| `POST` | `/api/v1/products/{id}/restore` | OWNER | Restores the product, preserving each variant's own active flag. |
| `POST` | `/api/v1/products/{productId}/variants/{variantId}/retire` | OWNER | Retires one variant. |
| `POST` | `/api/v1/products/{productId}/variants/{variantId}/restore` | OWNER | Restores one variant. |
| `GET` | `/api/v1/products/low-stock` | OWNER, STAFF | Sellable variants with a known reorder level at or below that threshold. Variants with no reorder level never appear. Carries no cost or margin. |

Retirement error:

| HTTP | Code | Meaning |
| :--- | :--- | :--- |
| `409` | `PRODUCT_RETIRED` | The SKU belongs to a retired product or variant. Order intake and supplier receiving refuse it; returns and restocks do not. |

## Inventory & stock (`/api/v1/stock`)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/stock/receive` | OWNER, STAFF | Records a supplier delivery. Automatically allocates units FIFO to waiting backlog orders. Refuses retired items with `409 PRODUCT_RETIRED`. |
| `POST` | `/api/v1/stock/count` | OWNER, STAFF | One-off physical count adjustment for a single variant. Writes an audit movement. |
| `GET` | `/api/v1/stock/variants/{variantId}/explanation` | OWNER, STAFF | Read-only stock explanation: on-hand, reserved and available figures, reorder level, sellable flag, open reservation holders, and the last ninety days of movements summarised by reason, in plain-language sentences with dates and order references. |
| `GET` | `/api/v1/stock/movements` | OWNER, STAFF | Immutable audit ledger history, filterable by `sku`, `reason`, and date range. |
| `POST` | `/api/v1/stock/counts` | OWNER, STAFF | Starts a shelf counting session. |
| `GET` | `/api/v1/stock/counts` | OWNER, STAFF | Paginated counting sessions, filterable by `status`. |
| `GET` | `/api/v1/stock/counts/{id}` | OWNER, STAFF | One counting session with its recorded items. |
| `POST` | `/api/v1/stock/counts/{id}/items` | OWNER, STAFF | Records a physical count for one variant, returning the variance. |
| `POST` | `/api/v1/stock/counts/{id}/complete` | OWNER, STAFF | Completes the count, reconciles inventory balances, and writes audit movements. Surfaces `RESERVATION_DEFICIT_DETECTED` when physical stock is below reserved stock. |
| `POST` | `/api/v1/stock/counts/{id}/cancel` | OWNER, STAFF | Aborts a counting session without ledger mutations. |

### Stock refusal details

A mismatched quantity is refused with `409 INSUFFICIENT_STOCK` (as a `ProblemDetail`) carrying extension fields: `sku`, `requestedQuantity`, `availableQuantity`, `onHandQuantity`, `reservedQuantity`, `reservedBy`, and `actions`. Each reservation holder includes the open order id, order number, reserved quantity, and order date. Action codes are stable and reusable by any client: `RECEIVE_STOCK`, `REDUCE_QUANTITY`, `VIEW_MOVEMENTS`, `VIEW_RESERVATIONS`, and `VIEW_RETIRED_PRODUCTS`.

The web interface turns known action codes into links — receive at `/stock/products?receiveSku={sku}`, movements at `/history/movements?sku={sku}`, reservations at `/stock/products?explainSku={sku}`, retired products at `/stock/products?retired=1` — and leaves `REDUCE_QUANTITY` as advice, because the correction belongs on the order.

The explanation endpoint derives everything from the ledger rather than storing it: reservation holders come from `PENDING` and `READY_TO_SHIP` order items, and movement summaries cover a ninety-day window grouped by reason with counts, net on-hand and reserved deltas, and the latest occurrence.

## Orders & dispatch (`/api/v1/orders`)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/orders` | OWNER, STAFF | Paginated order list filtered by `status` queue and `search`. |
| `GET` | `/api/v1/orders/{id}` | OWNER, STAFF | One order with its line items, reservation state, and shipment details. |
| `GET` | `/api/v1/orders/summary` | OWNER, STAFF | Counts per queue for the order tabs. |
| `POST` | `/api/v1/orders` | OWNER, STAFF | Manual order intake. Reserves available stock immediately or refuses the whole order with the insufficient-stock problem. |
| `POST` | `/api/v1/orders/import-csv` | OWNER, STAFF | Multipart CSV import. Returns a partial-success report: rows read, orders imported, orders waiting for stock, and rejected rows with row numbers and reasons. |
| `POST` | `/api/v1/orders/{id}/pack` | OWNER, STAFF | Moves the order to **Ready to ship**. |
| `POST` | `/api/v1/orders/{id}/ship` | OWNER, STAFF | Dispatches with `carrier`, `parcelSize`, and `trackingNumber`. Resolves the price-list rate and deducts stock atomically. |
| `POST` | `/api/v1/orders/{id}/cancel` | OWNER, STAFF | Cancels an unshipped order and releases its reserved stock. |
| `GET` | `/api/v1/orders/{id}/returns` | OWNER, STAFF | Returns recorded against one order. |
| `POST` | `/api/v1/orders/{id}/returns` | OWNER, STAFF | Processes a return, grading each unit `SELLABLE` (restocks on-hand) or `DAMAGED` (written off). |

## Reorder guidance (`/api/v1/analytics`, `/api/v1/settings/reorder`)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/reorder` | OWNER, STAFF | Urgency-sorted reorder list for every sellable variant: net units shipped over the ninety-day window, days of cover, suggested quantity, the assumptions used, and an explicit insufficient-history status instead of an invented estimate. Carries no cost or margin. |
| `GET` | `/api/v1/analytics/reorder/{variantId}` | OWNER, STAFF | Traces one suggestion to the shipped orders and processed returns behind its net demand — order numbers, dates, quantities, statuses, return condition. |
| `GET` | `/api/v1/settings/reorder` | OWNER, STAFF | Reads the current supplier lead time and when it was last changed. |
| `PUT` | `/api/v1/settings/reorder` | OWNER | Replaces the supplier lead time (1–365 days). Validation failures return `400 VALIDATION_FAILED` with `invalidParams`. Applies to every suggestion immediately. |

## Shipping summary & exports (OWNER only)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/shipping-summary` | OWNER | Aggregates shipping charged vs actual carrier costs by month with profit/loss margins. |
| `GET` | `/api/v1/export/{catalog\|orders\|history}` | OWNER | Streaming RFC 4180 CSV downloads with `Content-Disposition`. |

## Shipping rates & platform connections

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/shipping/rates` | OWNER, STAFF | Carrier price matrix (DHL, FedEx, UPS, Royal Mail × Small, Medium, Large). |
| `PUT` | `/api/v1/shipping/rates` | OWNER | Updates one carrier rate. |
| `GET` | `/api/v1/platforms` | OWNER | Lists all platform connections with sync status, slugs, and webhook URLs. |
| `GET` | `/api/v1/platforms/catalog` | OWNER | Describes every supported platform: display name, whether it can be configured, a realistic `apiEndpointExample`, required credential fields, signature headers, and the reason when unsupported. Carries no secret values. |
| `GET` | `/api/v1/platforms/{id}` | OWNER | One platform connection. |
| `POST` | `/api/v1/platforms` | OWNER | Creates a connection of any supported type. Returns the generated `slug` and webhook URL. An optional `Idempotency-Key` header makes a retry return the original connection with `200 OK`; a fresh create returns `201 Created` with `Location`. |
| `PATCH` | `/api/v1/platforms/{id}` | OWNER | Renames, re-addresses, rotates credentials or the signing secret, pauses, or resumes. Requires the `version` the client read; a stale save is refused with `409 CONCURRENT_CHANGE`. Pausing requires a non-blank `pauseReason` (max 500 characters). The webhook address itself is immutable after creation. |
| `GET` | `/api/v1/platforms/{id}/events` | OWNER | Paginated append-only history for one connection: change kind, affected field names, actor, time, and the reason recorded for a pause. Never values or secrets. |
| `POST` | `/api/v1/platforms/{id}/sync` | OWNER | Triggers an immediate sync pull for managed connectors (Shopify, WooCommerce). |
| `POST` | `/api/v1/platforms/{id}/test` | OWNER | Tests one connection. Managed connectors make a lightweight authenticated call and return `SUCCESS`, `AUTHENTICATION_FAILED`, `FAILED`, `UNREACHABLE`, or `NOT_CONFIGURED`; inbound-only channels return `AWAITING_INBOUND` with `lastInboundOrderAt` and are never called. The result is stored on the connection and appended to history as `TESTED`; it does not move the version or block later saves. |
| `DELETE` | `/api/v1/platforms/{id}` | OWNER | Pauses a connection. Requires a `reason` query parameter (max 500 characters). The webhook address immediately stops accepting traffic. |

### Platform connection error codes

| HTTP | Code | Meaning |
| :--- | :--- | :--- |
| `409` | `CONCURRENT_CHANGE` | The connection changed after the client read it. The detail says to reload and apply the change again. |
| `400` | `INVALID_PLATFORM_VERSION` | The edit did not carry a usable `version`. |
| `400` | `INVALID_IDEMPOTENCY_KEY` | The `Idempotency-Key` header is not 8–100 URL-safe characters (`A–Z`, `a–z`, `0–9`, `_`, `-`). |
| `409` | `PLATFORM_WEBHOOK_ADDRESS_IN_USE` | Another active connection already owns that webhook address. |
| `400` | `PAUSE_REASON_REQUIRED` | A pause carried no reason, or only whitespace. |
| `400` | `NO_PLATFORM_CHANGES` | The edit carried a version but no field to change. |

### Connection fields (`platform_connections`)

| Column | Type | Purpose |
| :--- | :--- | :--- |
| `platform_type` | `varchar` | Open-ended enum — no `CHECK` constraint after the V6 migration. |
| `display_name` | `varchar(100)` | Merchant-defined label shown in the UI (e.g. *"US TikTok Shop"*). |
| `slug` | `varchar(100) UNIQUE` | URL-safe identifier forming the webhook route (e.g. `tiktok-us` → `/api/v1/webhooks/orders/tiktok-us`). Unique among active connections via the partial index `uq_platform_connections_active_slug`; this address, not the platform type, is the identity of a connection. Several stores of the same type are allowed. |
| `version` | `bigint NOT NULL DEFAULT 0` | Optimistic-lock counter. Clients send the value they read; a save built on older data is refused rather than overwriting the newer one. |
| `creation_request_key` | `varchar(100) NULL` | The `Idempotency-Key` of the create that produced this row, when one was supplied. Unique among non-null values; never exposed by the API. |
| `pause_reason` | `varchar(500) NULL` | Why the connection is paused. Shown on the card while it is inactive, snapshotted onto the `PAUSED` history row, and cleared when the connection resumes. |
| `encrypted_credentials` | `text` | AES-256-GCM encrypted blob containing the webhook HMAC secret and any API tokens. |

### Platform type landscape

| Category | `platform_type` values | Ingestion pattern | Signature header |
| :--- | :--- | :--- | :--- |
| Direct-to-Consumer | `SHOPIFY`, `WOOCOMMERCE` | Outbound polling + inbound webhook | `X-Shopify-Hmac-Sha256` / `X-WC-Webhook-Signature` |
| Social commerce | `TIKTOK_SHOP` | Inbound webhook | `X-StockLedger-Signature` |
| Marketplaces | `AMAZON`, `ETSY` | Inbound webhook | `X-StockLedger-Signature` |
| Point-of-Sale | `SQUARE` | Inbound webhook | `X-Square-HMAC-SHA256` |
| Custom / automation | `CUSTOM_WEBHOOK` | Inbound webhook (Zapier, Make, scripts) | `X-StockLedger-Signature` |

`PlatformType` is the single source for this table **and** for the setup interface: display names, default webhook slugs, required credential fields, and signature headers are declared on the enum and published by `GET /api/v1/platforms/catalog`; the connection service and the inbound filter read from the same place. Adding a channel is one enum constant — the compiler forces every capability switch to account for it, and no second list exists to drift.

Every platform also accepts `X-StockLedger-Signature` (checked first) and `X-Webhook-Signature` (generic fallback, checked last) for merchants using a relay such as Zapier.

### Safe platform connection changes

A create that carries an `Idempotency-Key` first takes a transaction-scoped PostgreSQL advisory lock on `hashtext(key)`, then looks for a connection already created with that key. The lock makes simultaneous retries wait and find the first row instead of racing past the lookup and colliding on the unique index; different keys never block each other. The key is stored on the created row and returned only as part of the resource, so a replay answers `200` with the original connection.

Edits rely on `@Version`. The service compares the submitted `version` with the loaded row and refuses before doing work when they differ, which produces the clean `409`; Hibernate's `OptimisticLockingFailureException` is translated to the same response for the narrow race where another transaction commits between the check and the save.

A pause must explain itself. `PATCH` with `active=false` and `DELETE` both require a non-blank reason, trimmed before it is stored. The reason stays on the connection while it is paused so the card can show it, and is copied onto the `PAUSED` history row so older reasons survive later pauses and resumes. Resuming clears `pause_reason` on the connection and appends a `RESUMED` row with no reason.

### Hardened outbound calls

Every outbound call to a merchant's sales channel — order sync and connection test alike — goes through one guarded client; adapters only build the request, the client owns the safety policy.

- The scheme must be HTTPS unless `app.platform.allow-insecure-endpoints` is explicitly enabled for local tests.
- The host is resolved once, **every** returned address is checked, and the connection is opened to those checked addresses. A hostname that answers with a private, loopback, link-local, multicast, or special-purpose address is refused before a socket opens.
- Redirects are not followed, automatic retries are disabled, the response body is capped by `app.platform.outbound.max-response-bytes`, and both connect and read timeouts apply.
- Failures become plain-language messages that never include a credential or a raw response body.

A connection test is a read-only observation. It writes its result with a native update (`last_tested_at`, `last_test_outcome`, `last_test_http_status`, `last_test_detail`) so `version` and `updated_at` do not move, and appends a `TESTED` history row. An inbound-only channel is never called: it returns `AWAITING_INBOUND` and reports `last_inbound_order_at`, which moves only when a genuinely new webhook order is accepted, not on a replay.

## User administration & security log (OWNER only)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/users` | OWNER | Lists all users with roles, status, and `mustChangePassword`. |
| `POST` | `/api/v1/admin/users` | OWNER | Creates a user with a temporary password (`must_change_password=true`). |
| `PATCH` | `/api/v1/admin/users/{id}` | OWNER | Edits full name, role, or active status (self-deactivation and last-owner guards apply). |
| `DELETE` | `/api/v1/admin/users/{id}` | OWNER | Deactivates a user while preserving all ledger history. |
| `POST` | `/api/v1/admin/users/{id}/password-reset` | OWNER | Issues a new temporary password. The target must change it at next sign-in. Resetting your own password here is rejected. |
| `GET` | `/api/v1/admin/auth-events` | OWNER | Paginated, filterable security audit trail. Supports repeated `eventType`, `actorEmail`, `from`, `to`, `page`, and `size` (maximum 100). Raw `details` JSON is never rendered in the UI. |

## Inbound webhooks (`/api/v1/webhooks`)

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/webhooks/orders/{slug}` | Public + HMAC | Universal inbound order intake. Routes by `slug`, verifies the vendor's HMAC signature header, deduplicates via `idempotency_records`, and reserves stock atomically. |

The security chain:

```
POST /api/v1/webhooks/orders/{slug}
  │
  ├─ 1. Servlet reads raw body into CachedBodyHttpServletRequest (re-readable stream)
  ├─ 2. WebhookSignatureFilter extracts {slug} from URI
  ├─ 3. Looks up PlatformConnection by slug WHERE active = TRUE. If no slug matches, the platform name is accepted only when exactly one active connection of that type exists; with several stores on one platform the request is refused as ambiguous.
  ├─ 4. Decrypts AES-256-GCM credential blob → raw HMAC secret
  ├─ 5. Inspects request headers for vendor signature (Shopify / Square / universal)
  ├─ 6. Computes HMAC-SHA256(rawBody, secret) and compares constant-time
  ├─ 7. Returns 401 Unauthorized (RFC 7807) on mismatch
  └─ 8. Forwards to WebhookOrderController on success → idempotency check → atomic reservation
```

Accepted orders reserve stock through the same atomic SQL as every other intake channel and write one `ORDER_RESERVED` line to the movement ledger; replays within the 24-hour idempotency window return the original outcome without touching stock.
