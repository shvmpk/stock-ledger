// k6/load-test.js
//
// Two modes, selected with `-e MODE=race` (default `steady`):
//
//   steady — 10 virtual users browse the catalog and place one-unit
//            orders against a dedicated 10,000-unit variant. Measures
//            the sustained read path (GET /api/v1/products) and write
//            path (POST /api/v1/orders → atomic reservation +
//            stock_movements append) under continuous load.
//
//   race   — 50 virtual users fire one order each, simultaneously, at a
//            variant holding exactly ONE unit. The zero-oversell
//            invariant demands exactly 1× 201, 49× 409
//            INSUFFICIENT_STOCK and 0× 5xx; thresholds fail the run if
//            that ever breaks.
//
// Traffic goes through the nginx edge (http://localhost:3000) — the same
// path a real browser takes: SPA origin → nginx → Spring → PostgreSQL.
//
// setup() provisions its own product (LOAD-GEN-*) with realistic retail
// attributes so the seeded demo catalog is never touched, and
// teardown() retires it (retire, never delete).
//
// Runbook + recorded numbers: docs/pages/LOAD-TESTING.md

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const MODE = (__ENV.MODE ?? 'steady').toLowerCase();
const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:3000';

// Owner account seeded by DevDataSeeder.
const EMAIL = 'owner@stockledger.io';
const PASSWORD = 'OwnerPass2026!';

const PRODUCT_NAME_BASE = 'Load-Gen Cotton Canvas Tote 2026';

const createOrderMs = new Trend('create_order_ms');
const listProductsMs = new Trend('list_products_ms');
const raceOrderMs = new Trend('race_order_ms');

const ordersCreated = new Counter('orders_created');
const ordersRejected409 = new Counter('orders_rejected_409');

const raceAccepted201 = new Counter('race_accepted_201');
const raceRejected409 = new Counter('race_rejected_409');
const raceFailedOther = new Counter('race_failed_other');

export const options =
  MODE === 'race'
    ? {
        // per-vu-iterations: all 50 VUs spawn at once and each fires a
        // single order — as close to simultaneous as k6 executors get.
        scenarios: {
          race: {
            executor: 'per-vu-iterations',
            vus: 50,
            iterations: 1,
            maxDuration: '1m',
          },
        },
        thresholds: {
          // The zero-oversell invariant, verbatim.
          race_accepted_201: ['count == 1'],
          race_rejected_409: ['count == 49'],
          race_failed_other: ['count == 0'],
          checks: ['rate == 1'],
        },
      }
    : {
        scenarios: {
          steady: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
              { duration: '30s', target: 10 },
              { duration: '60s', target: 10 },
              { duration: '10s', target: 0 },
            ],
            gracefulRampDown: '5s',
          },
        },
        thresholds: {
          http_req_failed: ['rate < 0.01'],
          checks: ['rate == 1'],
          create_order_ms: ['p(95) < 500'],
          list_products_ms: ['p(95) < 800'],
        },
      };

// High-fidelity fixtures per the repo data mandate: authentic names,
// addresses, and postcodes — no placeholder garbage.
const customers = [
  { name: 'Eleanor Whitfield', email: 'eleanor.whitfield@brightmail.co.uk', address: '18 Larkspur Close, Guildford, GU2 7XN, United Kingdom', shipping: 4.99 },
  { name: 'Mateus Oliveira', email: 'mateus.oliveira@postbox.pt', address: 'Rua da Prata 142, Lisbon, 1100-052, Portugal', shipping: 7.5 },
  { name: 'Ingrid Sørensen', email: 'ingrid.sorensen@nordpost.no', address: 'Storgata 24B, Oslo, 0155, Norway', shipping: 9.0 },
  { name: 'Charlotte Beaumont', email: 'charlotte.beaumont@messagerie.fr', address: '45 Rue des Rosiers, Paris, 75004, France', shipping: 6.25 },
  { name: 'Henrik Larsson', email: 'henrik.larsson@postkop.se', address: 'Kungsgatan 8, Stockholm, 111 35, Sweden', shipping: 8.4 },
  { name: 'Aoife Murphy', email: 'aoife.murphy@eirmail.ie', address: '22 Ranelagh Road, Dublin, D06 Y3P7, Ireland', shipping: 5.75 },
  { name: 'Thomas Kessler', email: 'thomas.kessler@briefkasten.ch', address: 'Bahnhofstrasse 11, Zürich, 8001, Switzerland', shipping: 11.2 },
  { name: 'Fiona Campbell', email: 'fiona.campbell@albapost.uk', address: '7 Dean Terrace, Edinburgh, EH4 3DA, United Kingdom', shipping: 4.99 },
];

function jsonHeaders(cookie) {
  // k6 params must nest headers under `headers`; a flat object's keys are
  // silently ignored (which makes k6 default string bodies to
  // application/octet-stream and the backend rejects the request).
  const headers = { 'Content-Type': 'application/json' };
  if (cookie) headers.Cookie = cookie;
  return { headers };
}

function captureSession(res) {
  // k6 parses Set-Cookie per-VU; the setup VU's jar is NOT shared with
  // the worker VUs, so the session must be forwarded explicitly.
  for (const name of Object.keys(res.cookies ?? {})) {
    const first = res.cookies[name][0];
    if (first && first.value) return `${name}=${first.value}`;
  }
  return null;
}

function loginUntilReady() {
  // The throwaway stack starts empty, so DevDataSeeder seeds its demo
  // dataset after boot; poll a real login until the backend is ready.
  const deadline = Date.now() + 180_000;
  let lastStatus = 0;
  while (Date.now() < deadline) {
    const res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ email: EMAIL, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
    );
    if (res.status === 200) {
      const session = captureSession(res);
      if (session) return session;
    }
    lastStatus = res.status;
    sleep(2);
  }
  throw new Error(
    `Backend never became ready (last login HTTP ${lastStatus}). ` +
      'Check `docker compose -p stockledger-loadtest logs backend`.',
  );
}

export function setup() {
  const cookie = loginUntilReady();

  // SKUs, barcode, and name are unique per run: two modes (and repeat
  // runs) can share one booted stack without colliding, and teardown
  // retires each run's product afterwards. The tag is generated here —
  // k6's top-level init runs once per VU, so it must not live there.
  const runTag = Math.random().toString(36).slice(2, 6).toUpperCase();
  const trafficSku = `LOAD-GEN-BLK-OS-${runTag}`; // 10,000 units — steady traffic
  const raceSku = `LOAD-GEN-LTD-${runTag}`; // exactly 1 unit — oversell race
  const trafficBarcode = `50601234${String(Date.now()).slice(-5)}`;
  const raceBarcode = `50601235${String(Date.now()).slice(-5)}`;

  const res = http.post(
    `${BASE_URL}/api/v1/products`,
    JSON.stringify({
      name: `${PRODUCT_NAME_BASE} · Run ${runTag}`,
      description: 'Dedicated load-test product provisioned by k6; retired in teardown.',
      category: 'Load Test',
      variants: [
        {
          sku: trafficSku,
          barcode: trafficBarcode,
          optionName: 'Color',
          optionValue: 'Black',
          costPrice: '7.50',
          sellingPrice: '18.00',
          weightGrams: 220,
          minStockThreshold: 50,
        },
        {
          sku: raceSku,
          barcode: raceBarcode,
          optionName: 'Edition',
          optionValue: 'Limited',
          costPrice: '28.00',
          sellingPrice: '65.00',
          weightGrams: 480,
          minStockThreshold: 1,
        },
      ],
    }),
    jsonHeaders(cookie),
  );
  if (res.status < 200 || res.status >= 300) {
    throw new Error(
      `Could not provision the load-gen product (HTTP ${res.status}): ${res.body}.`,
    );
  }

  const product = res.json('data');
  const trafficVariant = product.variants.find((v) => v.sku === trafficSku);
  const raceVariant = product.variants.find((v) => v.sku === raceSku);

  receiveStock(cookie, trafficVariant.id, 10000, 'k6 steady seed: opening inventory');
  receiveStock(cookie, raceVariant.id, 1, 'k6 race seed: single unit for the oversell race');

  return { cookie, productId: product.id, trafficSku, raceSku };
}

function receiveStock(cookie, productVariantId, quantity, notes) {
  const res = http.post(
    `${BASE_URL}/api/v1/stock/receive`,
    JSON.stringify({ productVariantId, quantity, notes }),
    jsonHeaders(cookie),
  );
  check(res, { 'stock received': (r) => r.status >= 200 && r.status < 300 });
  if (res.status < 200 || res.status >= 300) {
    throw new Error(`Stock receive failed (HTTP ${res.status}): ${res.body}`);
  }
}

export default function (data) {
  if (MODE === 'race') {
    raceOrder(data);
    return;
  }
  steadyIteration(data);
}

function steadyIteration(data) {
  const list = http.get(`${BASE_URL}/api/v1/products`, {
    headers: { Cookie: data.cookie },
    tags: { name: 'list-products' },
  });
  listProductsMs.add(list.timings.duration);
  check(list, { 'catalog 200': (r) => r.status === 200 });

  const customer = customers[Math.floor(Math.random() * customers.length)];
  const order = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      customerName: customer.name,
      customerEmail: customer.email,
      shippingAddress: customer.address,
      customerShippingCharge: customer.shipping,
      items: [{ sku: data.trafficSku, quantity: 1, unitPrice: '18.00' }],
    }),
    jsonHeaders(data.cookie),
  );
  createOrderMs.add(order.timings.duration);
  if (order.status === 201) ordersCreated.add(1);
  else if (order.status === 409) ordersRejected409.add(1);
  check(order, {
    // 409 is a legitimate outcome (insufficient stock) but should never
    // happen in steady mode with a 10,000-unit variant; it is counted
    // separately so a burst of them is visible in the summary.
    'order accepted or correctly rejected': (r) => r.status === 201 || r.status === 409,
  });

  sleep(1);
}

function raceOrder(data) {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      customerName: 'Priya Raghunathan',
      customerEmail: 'priya.raghunathan@meridianpost.uk',
      shippingAddress: '3B Wharfinger Lane, Bristol, BS1 4QA, United Kingdom',
      customerShippingCharge: 4.99,
      items: [{ sku: data.raceSku, quantity: 1, unitPrice: '65.00' }],
    }),
    jsonHeaders(data.cookie),
  );
  raceOrderMs.add(res.timings.duration);
  if (res.status === 201) raceAccepted201.add(1);
  else if (res.status === 409) raceRejected409.add(1);
  else raceFailedOther.add(1);
  check(res, { 'race: 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

export function teardown(data) {
  // Retirement, never deletion — past movements and orders survive.
  const res = http.post(
    `${BASE_URL}/api/v1/products/${data.productId}/retire`,
    null,
    { headers: { Cookie: data.cookie }, tags: { name: 'retire-product' } },
  );
  check(res, { 'product retired': (r) => r.status >= 200 && r.status < 300 });
}
