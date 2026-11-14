# StockLedger — Load Testing & Concurrency Numbers

This page is the reproducible runbook for the k6 load tests, plus the recorded
numbers and what they prove. The load suite has two jobs:

1. **Prove the zero-oversell invariant over the wire** — not just inside the JVM.
   50 simultaneous checkouts against a variant holding exactly one unit must
   produce exactly one success, forty-nine clean `409` rejections, and zero
   server errors.
2. **Measure sustained read/write latency** of the two hottest endpoints
   (catalog browse and order placement) under continuous load.

The tests live in [`k6/load-test.js`](https://github.com/shvmpk/stock-ledger/blob/main/k6/load-test.js)
and drive the **real full stack** — nginx edge → distroless Spring Boot →
PostgreSQL — the same path a browser takes.

---

## 1. Prerequisites

- **k6** (`brew install k6`) — the runbook below was verified with k6 v2.4.0.
- **Built images** — `docker compose -f docker-compose.yml --profile full build`
  (the e2e suite builds them too; if you have run e2e recently they exist).
- **The dev stack must be stopped.** The compose file pins container names
  (`stockledger-postgres`, …), so two stacks cannot run at once:
  `docker compose -f docker-compose.yml --profile full down` if it is up.

---

## 2. Runbook

The tests run against an **isolated throwaway stack**: a separate Compose
project name (`stockledger-loadtest`) gets its own PostgreSQL volume, the dev
seeder populates it fresh, and `down -v` wipes it afterwards. Your development
data is never touched.

```bash
# 1. Boot the throwaway stack (fresh DB volume, images come from cache)
docker compose -p stockledger-loadtest -f docker-compose.yml --profile full up -d --build --wait

# 2. Oversell race FIRST (fresh DB = negligible order-number collision noise)
k6 run -e MODE=race k6/load-test.js

# 3. Sustained load (10 VUs, ~100 s)
k6 run k6/load-test.js

# 4. Wipe the throwaway stack and its volume
docker compose -p stockledger-loadtest -f docker-compose.yml --profile full down -v
```

Notes on the design:

- **Race before steady.** Each run provisions its own product via the API
  (`setup()`) with run-unique SKUs (`LOAD-GEN-BLK-OS-XXXX`, 10,000 units for
  traffic; `LOAD-GEN-LTD-XXXX`, exactly 1 unit for the race) and `teardown()`
  retires it — so both modes can share one boot. Running the race first simply
  keeps its measurement clean of the order-number collisions described in
  finding 1 below.
- `setup()` polls a real `POST /api/v1/auth/login` until the backend has
  finished seeding, so a cold stack works without manual waits.
- To export machine-readable summaries, add
  `--summary-export /tmp/steady.json` (and `race.json`) to each `k6 run`.

### What each mode does

| Mode | Load model | Measured |
| :--- | :--- | :--- |
| `steady` (default) | 10 VUs ramp over 30 s, hold 60 s, drain 10 s. Each iteration: `GET /api/v1/products` + one 1-unit `POST /api/v1/orders` against the 10,000-unit variant, 1 s think time. | Latency percentiles per endpoint, error rate, order throughput (~7–8 orders/s). |
| `race` | `per-vu-iterations`: 50 VUs spawn at once, each fires exactly one order for the 1-unit variant. | Exact count of `201` / `409` / 5xx outcomes — the oversell invariant. |

---

## 3. Environment context (for the numbers below)

| Attribute | Value |
| :--- | :--- |
| Recorded | 2026-11-14 |
| Host | Apple Silicon (arm64) MacBook, Docker Desktop 29.8.2 |
| k6 | v2.4.0 (brew), traffic through the nginx edge on `localhost:3000` |
| Stack | `postgres:18-alpine`, distroless backend (custom jlink JRE, Java 21.0.13), unprivileged nginx frontend — all containers on one Docker VM |
| Database | Fresh isolated volume, dev seeder dataset (30 products, 12 orders) |

Shared-runner CI numbers would be noise (different CPU each run), so there is
**deliberately no k6 job in CI** — the runbook above is the deliverable.

---

## 4. Recorded results

### 4.1 Oversell race — 50 parallel orders, 1 unit in stock

| Metric | Result | Threshold |
| :--- | :--- | :--- |
| Orders accepted (`201 Created`) | **1** | must be exactly 1 ✓ |
| Orders rejected (`409 INSUFFICIENT_STOCK`) | **49** | must be exactly 49 ✓ |
| Server errors / unexpected statuses | **0** | must be 0 ✓ |
| Checks | 53/53 (100%) | rate == 1 ✓ |
| All 50 orders completed | ~0.4 s wall (~116 orders/s) | — |
| `race_order_ms` | avg 105 · p50 109 · p90 152 · p95 154 · max 158 | — |

Latencies include JIT cold-start (the race fires within seconds of backend
boot); a warm backend places the same orders in ~5–15 ms each (see steady).
These numbers are from the post-fix verification run (finding 1 below).

**What this proves:** every one of the 50 concurrent checkouts went through
nginx → Spring Security → the atomic SQL reservation predicate
(`UPDATE inventory_levels … WHERE (on_hand - reserved) >= :qty`) →
`stock_movements` append, and the ledger allowed exactly one winner. No unit
was double-sold; the other 49 got a clean, actionable `409` — no 500s, no
oversell, at the outermost boundary of the system.

### 4.2 Steady — 10 VUs, 100 s

| Metric | Result | Threshold |
| :--- | :--- | :--- |
| Orders created | **768** (~7.63 orders/s) | — |
| `create_order_ms` | avg 14.9 · p50 14.8 · p90 20.7 · **p95 22.5** · max 29.3 | p95 < 500 ✓ |
| `list_products_ms` | avg 18.3 · p50 16.7 · p90 27.5 · **p95 33.8** · max 73.0 | p95 < 800 ✓ |
| `http_req_failed` | **0.00%** (0 / 1,541 requests) | < 1% ✓ |
| Checks | 100% | rate == 1 ✓ |
| Iteration duration | p95 1.04 s (≈ 1 s think time + requests) | — |

Order placement — which atomically reserves stock, appends an immutable
`stock_movements` row, and inserts the order and its items in one transaction —
runs at **p95 22 ms** through the full stack, with the catalog browse at
**p95 34 ms**. Both have enormous headroom against their thresholds at this
load; the 10-VU profile comfortably sustains ~15 requests/s. These numbers are
from the post-fix verification run (finding 1 below): 768 orders, zero
collisions, and the sequence adds no measurable latency (pre-fix p95 22 ms).

### 4.3 JVM-level zero-oversell (Testcontainers)

`ZeroOversellConcurrencyTest` (in `src/test/…/concurrency/`) asserts the same
invariant inside the service layer with real threads and a real PostgreSQL:
**10 concurrent orders against a 1-unit variant → exactly 1 reservation, 9
rejected with `INSUFFICIENT_STOCK`**. The k6 race in 4.1 is the same proof one
layer further out, through the HTTP stack. Re-run locally on the recording
date above: passing (test method ~0.6 s, plus Testcontainers startup).

---

## 5. Findings from the first runs

### 5.1 Order-number collisions under load (found by this suite — **fixed**)

`OrderService.generateOrderNumber()` originally drew a **random** 5-digit
suffix (`ORD-2026-NNNNN`, ~90,000 values), and `orders.order_number` is unique
across **all history**. Each new order therefore collided with probability ≈
*(orders already stored) / 90,000* — the failure rate **grew linearly with the
store's lifetime order count**.

What the suite observed before the fix (steady mode):

| Run | New orders | Orders already in DB | Colliding inserts → HTTP 500 |
| :--- | :--- | :--- | :--- |
| A | 766 | ~12 (seed) + same-run | 5 |
| B | 772 | ~770 (run A's orders persisted) | 14 — matches the expected ≈10 for that history size |

Real-world impact at the time: a store with 10,000 historical orders taking
100 orders/day would expect **~11 failed checkouts per day**, each surfacing as
a raw `500 INTERNAL_SERVER_ERROR` (the reservation rolled back, so **no
oversell** — but the customer saw an error).

**Fix (TDD'd):** the suffix now comes from a **PostgreSQL sequence**
(`order_number_seq`, migration `V18`), handed out atomically by
`SELECT nextval(...)` — concurrent orders can never draw the same number, and
the sequence is seeded above the highest suffix already stored so it can never
collide with pre-migration rows either.

**Verification:**

- `OrderNumberUniquenessConcurrencyTest` — written first, reproduced the bug
  deterministically (1,500 concurrent orders → `duplicate key value violates
  unique constraint "orders_order_number_key"`), and now passes: 1,500
  concurrent orders, all succeed, all numbers distinct.
- k6 re-run after the fix: the same steady profile that produced 5 and 14
  collisions now produces **0** across 768 orders (all 781 stored numbers
  distinct in the database), and the 50-way race still reads exactly
  1× `201` / 49× `409` / 0× `5xx`.

### 5.2 Generic 500 handler swallowed the root cause (minor, pre-existing — **fixed**)

`GlobalExceptionHandler.handleGenericException(Exception)` mapped *every*
unexpected exception to a clean RFC 7807 body **without logging it** — during
debugging, the backend log showed nothing at all for the order-number failures
above (they were only visible via Spring's `DispatcherServlet` DEBUG output).
The client-facing body was already right; the gap was purely server-side
visibility: no `ERROR` line, no stack trace, nothing to grep.

**Fix (TDD'd):** the handler now writes one `ERROR` line per unhandled failure
carrying the HTTP method, the request URI, and the exception with its **full
stack trace**. The `requestId` from `RequestIdFilter` is already in the console
log pattern via MDC, so each line correlates directly with the failed request
— and the client-facing body stays exactly as generic as before (no internal
details leak). Covered by
`GlobalExceptionHandlerTest.shouldLogUnexpectedExceptionWithRequestContext`,
written first (it asserted one captured `ERROR` event and failed with
`Expected size: 1 but was: 0`).

---

## 6. Tuning

- **More/less load:** edit the `stages` block for `steady` (VU targets and
  durations) in `k6/load-test.js`.
- **Different target:** `BASE_URL=http://host:port k6 run …` (defaults to
  `http://localhost:3000`).
- **Thresholds:** `create_order_ms p(95) < 500`, `list_products_ms p(95) < 800`,
  `http_req_failed < 1%` for steady; exact `1×201 / 49×409 / 0×5xx` for the
  race. The race thresholds are the invariant — do not loosen them; if they
  fail, that is a product bug, not a load problem.
- The race's parallel count (**50**) is the stated stress parameter; change
  `vus` in the `race` scenario (and the `49` in the threshold) together.
