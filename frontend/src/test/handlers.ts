import { http, HttpResponse } from 'msw';
import type {
  AuthEvent,
  CreatePlatformInput,
  CsvImportResult,
  Order,
  OrderStatus,
  PlatformConnection,
  PlatformConnectionEvent,
  PlatformSyncResult,
  Product,
  ProductInput,
  ProductVariant,
  ReorderSettings,
  ShippingRate,
  StockCount,
  StockCountItem,
  StockExplanation,
  UpdatePlatformInput,
  User,
} from '../lib/api/types';
import {
  authEvents,
  cancelledOrder,
  completedCount,
  inProgressCount,
  movements,
  orderSummary,
  orders,
  ownerUser,
  pendingOrder,
  platformCatalog,
  platformEvents,
  platforms,
  products,
  readyOrder,
  reorderDetails,
  reorderList,
  reorderSettings,
  retiredProduct,
  returns,
  shippedOrder,
  shippingRates,
  shippingSummary,
  staffUser,
  stockCountSummaries,
  stockExplanation,
  users,
  waitingOrder,
} from './fixtures';

const uuid = (suffix: string) => `00000000-0000-4000-8000-${suffix.padStart(12, '0')}`;

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

let runtimeOrders: Order[] = [...orders];
const countSessions = new Map<string, StockCount>([
  [inProgressCount.id, inProgressCount],
  [completedCount.id, completedCount],
]);
let runtimeRates: ShippingRate[] = [...shippingRates];
let runtimeUsers: User[] = [...users];
let runtimePlatforms = [...platforms];
let runtimePlatformEvents = [...platformEvents];
let runtimePlatformCreationKeys = new Map<string, PlatformConnection>();
let runtimeAuthEvents: AuthEvent[] = [...authEvents];
let runtimeSessionUser: User | null = ownerUser;
let runtimeProducts: Product[] = [...products];
let runtimeProductSequence = 0;
let runtimeReorderSettings: ReorderSettings = { ...reorderSettings };

export function resetMockData() {
  runtimeOrders = [...orders];
  countSessions.clear();
  countSessions.set(inProgressCount.id, inProgressCount);
  countSessions.set(completedCount.id, completedCount);
  runtimeRates = [...shippingRates];
  runtimeUsers = [...users];
  runtimePlatforms = [...platforms];
  runtimePlatformEvents = [...platformEvents];
  runtimePlatformCreationKeys = new Map();
  runtimeAuthEvents = [...authEvents];
  runtimeSessionUser = ownerUser;
  runtimeProducts = [...products, retiredProduct];
  runtimeProductSequence = 0;
  runtimeReorderSettings = { ...reorderSettings };
}

function envelope<T>(data: T, pagination?: Record<string, unknown>) {
  return {
    success: true,
    data,
    ...(pagination ? { pagination } : {}),
    meta: { timestamp: new Date().toISOString() },
  };
}

function ok<T>(data: T, pagination?: Record<string, unknown>) {
  return HttpResponse.json(envelope(data, pagination));
}

function problem(
  status: number,
  title: string,
  detail: string,
  code: string,
  invalidParams: Array<{ field: string; message: string }> = [],
) {
  return HttpResponse.json(
    {
      type: `https://api.stockledger.io/errors/${code.toLowerCase().replaceAll('_', '-')}`,
      title,
      status,
      detail,
      code,
      timestamp: new Date().toISOString(),
      invalidParams,
    },
    { status },
  );
}

function notFound(resource: string) {
  return problem(404, 'Not Found', `${resource} was not found.`, 'NOT_FOUND');
}

function paginate<T>(items: T[], request: Request, defaultSize = 20) {
  const url = new URL(request.url);
  const page = Math.max(0, Number(url.searchParams.get('page') ?? 0));
  const size = Math.max(1, Number(url.searchParams.get('size') ?? defaultSize));
  const start = page * size;
  return {
    data: items.slice(start, start + size),
    pagination: {
      page,
      size,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
      hasMore: start + size < items.length,
    },
  };
}

function findVariant(variantId: string): { product: Product; variant: ProductVariant } | null {
  for (const product of runtimeProducts) {
    const variant = product.variants.find((entry) => entry.id === variantId);
    if (variant) return { product, variant };
  }
  return null;
}

function buildExplanation(product: Product, variant: ProductVariant): StockExplanation {
  if (variant.sku === stockExplanation.sku) {
    return { ...stockExplanation, productVariantId: variant.id };
  }

  const reservations =
    variant.sku === stockExplanation.sku
      ? stockExplanation.reservations
      : [];
  const related = movements.filter((movement) => movement.sku === variant.sku);
  const grouped = new Map<string, StockExplanation['recentMovements'][number]>();
  for (const movement of related) {
    const existing = grouped.get(movement.reason);
    if (existing) {
      existing.movementCount += 1;
      existing.netOnHand += movement.deltaOnHand;
      existing.netReserved += movement.deltaReserved;
      if (movement.createdAt > existing.latestAt) existing.latestAt = movement.createdAt;
    } else {
      grouped.set(movement.reason, {
        reason: movement.reason,
        label: movement.reason.replaceAll('_', ' ').toLowerCase(),
        movementCount: 1,
        netOnHand: movement.deltaOnHand,
        netReserved: movement.deltaReserved,
        latestAt: movement.createdAt,
      });
    }
  }

  return {
    productVariantId: variant.id,
    sku: variant.sku,
    productName: product.name,
    optionName: variant.optionName,
    optionValue: variant.optionValue,
    onHandQuantity: variant.onHand,
    reservedQuantity: variant.reserved,
    availableQuantity: variant.available,
    reorderLevel: variant.minStockThreshold,
    sellable: product.active && variant.active,
    movementWindowDays: 90,
    windowStart: '2026-08-04T09:00:00Z',
    reservations,
    recentMovements: [...grouped.values()],
    sentences: [
      `There are ${variant.onHand} units on the shelf, ${variant.reserved} reserved for open orders, leaving ${variant.available} available to sell.`,
    ],
    generatedAt: '2026-11-02T09:00:00Z',
  };
}

function productFromInput(id: string, input: ProductInput, existing?: Product): Product {
  return {
    id,
    name: input.name,
    description: input.description ?? '',
    category: input.category ?? '',
    active: existing?.active ?? true,
    variants: input.variants.map((variant, index) => {
      runtimeProductSequence += 1;
      const previous = existing?.variants.find((entry) => entry.id === variant.id);
      return {
        id: variant.id ?? uuid(`9${String(runtimeProductSequence).padStart(3, '0')}${index}`),
        sku: variant.sku,
        barcode: variant.barcode ?? '',
        optionName: variant.optionName ?? '',
        optionValue: variant.optionValue ?? '',
        active: previous?.active ?? true,
        costPrice: variant.costPrice,
        sellingPrice: variant.sellingPrice,
        weightGrams: variant.weightGrams ?? null,
        minStockThreshold: variant.minStockThreshold ?? null,
        onHand: previous?.onHand ?? 0,
        reserved: previous?.reserved ?? 0,
        available: previous?.available ?? 0,
      };
    }),
  };
}

function updatedOrder(order: Order, patch: Partial<Order>): Order {
  const next = { ...order, ...patch };
  runtimeOrders = runtimeOrders.map((entry) => (entry.id === order.id ? next : entry));
  return next;
}

function updateProduct(productId: string, update: (product: Product) => Product) {
  const existing = runtimeProducts.find((entry) => entry.id === productId);
  if (!existing) return notFound('Product');
  const updated = update(existing);
  runtimeProducts = runtimeProducts.map((entry) =>
    entry.id === productId ? updated : entry,
  );
  return ok(updated);
}

function updateVariant(
  productId: string,
  variantId: string,
  update: (variant: ProductVariant) => ProductVariant,
) {
  const existing = runtimeProducts.find((entry) => entry.id === productId);
  if (!existing) return notFound('Product');
  let found = false;
  const variants = existing.variants.map((variant) => {
    if (variant.id !== variantId) return variant;
    found = true;
    return update(variant);
  });
  if (!found) return notFound('ProductVariant');
  const updated = { ...existing, variants };
  runtimeProducts = runtimeProducts.map((entry) =>
    entry.id === productId ? updated : entry,
  );
  return ok(updated);
}

function orderById(id: string) {
  return runtimeOrders.find((order) => order.id === id) ?? null;
}

function countById(id: string) {
  return countSessions.get(id) ?? null;
}

function storeUser(updated: User): User {
  runtimeUsers = runtimeUsers.map((user) => (user.id === updated.id ? updated : user));
  if (runtimeSessionUser?.id === updated.id) {
    runtimeSessionUser = updated;
  }
  return updated;
}

export const handlers = [
  http.get('/api/v1/auth/me', () =>
    runtimeSessionUser ? ok(runtimeSessionUser) : problem(
      401,
      'Unauthorized',
      'Authentication is required.',
      'UNAUTHORIZED',
    ),
  ),
  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    if (body.email === staffUser.email && body.password === 'StaffPass2026!') {
      runtimeSessionUser = runtimeUsers.find((user) => user.id === staffUser.id) ?? staffUser;
      return ok(runtimeSessionUser);
    }
    if (body.email === ownerUser.email && body.password === 'OwnerPass2026!') {
      runtimeSessionUser = runtimeUsers.find((user) => user.id === ownerUser.id) ?? ownerUser;
      return ok(runtimeSessionUser);
    }
    return problem(401, 'Unauthorized', 'The email or password is incorrect.', 'UNAUTHORIZED');
  }),
  http.post('/api/v1/auth/logout', () => ok({ message: 'Signed out.' })),
  http.post('/api/v1/auth/password', async ({ request }) => {
    const body = (await request.json()) as { currentPassword?: string; newPassword?: string };
    if (!runtimeSessionUser) {
      return problem(401, 'Unauthorized', 'Authentication is required.', 'UNAUTHORIZED');
    }
    if (!body.newPassword || body.newPassword.length < 12) {
      return problem(400, 'Weak Password', 'The new password is too weak.', 'WEAK_PASSWORD', [
        { field: 'newPassword', message: 'Password must be at least 12 characters.' },
      ]);
    }
    const updated = storeUser({ ...runtimeSessionUser, mustChangePassword: false });
    return ok(updated);
  }),

  http.get('/api/v1/products', ({ request }) => {
    const includeRetired = new URL(request.url).searchParams.get('includeRetired') === 'true';
    const visible = runtimeProducts
      .filter((product) => includeRetired || product.active)
      .map((product) => ({
        ...product,
        variants: product.variants.filter((variant) => includeRetired || variant.active),
      }));
    return ok(visible);
  }),
  http.get('/api/v1/products/:productId', ({ params }) => {
    const product = runtimeProducts.find((entry) => entry.id === params.productId);
    return product ? ok(product) : notFound('Product');
  }),
  http.post('/api/v1/products', async ({ request }) => {
    const body = (await request.json()) as ProductInput;
    runtimeProductSequence += 1;
    const product = productFromInput(
      uuid(`8${String(runtimeProductSequence).padStart(3, '0')}`),
      body,
    );
    runtimeProducts = [...runtimeProducts, product];
    return HttpResponse.json(envelope(product), { status: 201 });
  }),
  http.put('/api/v1/products/:productId', async ({ params, request }) => {
    const body = (await request.json()) as ProductInput;
    const existing = runtimeProducts.find((entry) => entry.id === params.productId);
    if (!existing) return notFound('Product');
    const updated = productFromInput(existing.id, body, existing);
    runtimeProducts = runtimeProducts.map((entry) =>
      entry.id === updated.id ? updated : entry,
    );
    return ok(updated);
  }),
  http.post('/api/v1/products/:productId/retire', ({ params }) =>
    updateProduct(String(params.productId), (product) => ({ ...product, active: false })),
  ),
  http.post('/api/v1/products/:productId/restore', ({ params }) =>
    updateProduct(String(params.productId), (product) => ({ ...product, active: true })),
  ),
  http.post('/api/v1/products/:productId/variants/:variantId/retire', ({ params }) =>
    updateVariant(String(params.productId), String(params.variantId), (variant) => ({
      ...variant,
      active: false,
    })),
  ),
  http.post('/api/v1/products/:productId/variants/:variantId/restore', ({ params }) =>
    updateVariant(String(params.productId), String(params.variantId), (variant) => ({
      ...variant,
      active: true,
    })),
  ),

  http.get('/api/v1/stock/movements', ({ request }) => {
    const url = new URL(request.url);
    const sku = url.searchParams.get('sku');
    const reason = url.searchParams.get('reason');
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');
    const filtered = movements.filter((movement) => {
      if (sku && !movement.sku.toLowerCase().includes(sku.toLowerCase())) return false;
      if (reason && movement.reason !== reason) return false;
      if (from && movement.createdAt < from) return false;
      if (to && movement.createdAt > to) return false;
      return true;
    });
    const result = paginate(filtered, request);
    return ok(result.data, result.pagination);
  }),

  http.get('/api/v1/stock/variants/:variantId/explanation', ({ params }) => {
    const found = findVariant(String(params.variantId));
    return found ? ok(buildExplanation(found.product, found.variant)) : notFound('Product variant');
  }),

  http.post('/api/v1/stock/receive', async ({ request }) => {
    const body = (await request.json()) as { productVariantId: string; quantity: number; notes?: string };
    const found = findVariant(body.productVariantId);
    if (!found) return notFound('Product variant');
    const onHand = found.variant.onHand + body.quantity;
    const allocated =
      found.variant.sku === 'COF-ETH-1KG' ? [waitingOrder.orderNumber] : [];
    return ok({
      stockLevel: {
        id: uuid('901'),
        productVariantId: found.variant.id,
        sku: found.variant.sku,
        onHandQuantity: onHand,
        reservedQuantity: found.variant.reserved,
        availableQuantity: onHand - found.variant.reserved,
      },
      allocatedOrderNumbers: allocated,
    });
  }),

  http.get('/api/v1/stock/counts', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const filtered = stockCountSummaries.filter((session) => !status || session.status === status);
    const result = paginate(filtered, request, 10);
    return ok(result.data, result.pagination);
  }),
  http.post('/api/v1/stock/counts', () => {
    const created: StockCount = {
      id: uuid('499'),
      status: 'IN_PROGRESS',
      conductedBy: ownerUser,
      startedAt: new Date().toISOString(),
      completedAt: null,
      itemCount: 0,
      totalVariance: 0,
      hasReservationDeficit: false,
      reservationDeficit: 0,
      items: [],
    };
    countSessions.set(created.id, created);
    return ok(created);
  }),
  http.get('/api/v1/stock/counts/:sessionId', ({ params }) => {
    const session = countById(String(params.sessionId));
    return session ? ok(session) : notFound('Stock count session');
  }),
  http.post('/api/v1/stock/counts/:sessionId/items', async ({ params, request }) => {
    const session = countById(String(params.sessionId));
    if (!session) return notFound('Stock count session');
    const body = (await request.json()) as {
      productVariantId: string;
      countedOnHand: number;
      notes?: string;
    };
    const found = findVariant(body.productVariantId);
    if (!found) return notFound('Product variant');
    const item: StockCountItem = {
      id: uuid(`88${session.items.length + 1}`),
      productVariantId: found.variant.id,
      sku: found.variant.sku,
      productName: found.product.name,
      optionName: found.variant.optionName,
      optionValue: found.variant.optionValue,
      expectedOnHand: found.variant.onHand,
      countedOnHand: body.countedOnHand,
      variance: body.countedOnHand - found.variant.onHand,
      notes: body.notes ?? null,
      hasReservationDeficit: body.countedOnHand < found.variant.reserved,
      reservationDeficit: Math.max(0, found.variant.reserved - body.countedOnHand),
    };
    const updated = { ...session, items: [...session.items, item], itemCount: session.itemCount + 1 };
    countSessions.set(updated.id, updated);
    return ok(updated);
  }),
  http.post('/api/v1/stock/counts/:sessionId/complete', ({ params }) => {
    const session = countById(String(params.sessionId));
    if (!session) return notFound('Stock count session');
    const totalVariance = session.items.reduce((sum, item) => sum + item.variance, 0);
    const deficit = session.items.reduce((sum, item) => sum + item.reservationDeficit, 0);
    const completed: StockCount = {
      ...session,
      status: 'COMPLETED',
      completedAt: new Date().toISOString(),
      totalVariance,
      hasReservationDeficit: deficit > 0,
      reservationDeficit: deficit,
    };
    countSessions.set(completed.id, completed);
    return ok(completed);
  }),
  http.post('/api/v1/stock/counts/:sessionId/cancel', ({ params }) => {
    const session = countById(String(params.sessionId));
    if (!session) return notFound('Stock count session');
    const cancelled: StockCount = {
      ...session,
      status: 'CANCELLED',
      completedAt: new Date().toISOString(),
    };
    countSessions.set(cancelled.id, cancelled);
    return ok(cancelled);
  }),

  http.get('/api/v1/orders/summary', () => ok(orderSummary)),
  http.get('/api/v1/orders', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status') as OrderStatus | null;
    const filtered = runtimeOrders.filter((order) => !status || order.status === status);
    const result = paginate(filtered, request);
    return ok(result.data, result.pagination);
  }),
  http.post('/api/v1/orders/import-csv', () => {
    const result: CsvImportResult = {
      totalRows: 3,
      importedOrders: 2,
      waitingForStockOrders: 1,
      failedRows: 1,
      errors: [
        {
          rowNumber: 4,
          orderNumber: 'ORD-2026-0113',
          sku: 'HOOD-NVY-XL',
          error: 'Quantity must be greater than zero.',
        },
      ],
    };
    return ok(result);
  }),
  http.post('/api/v1/orders', async ({ request }) => {
    const body = (await request.json()) as {
      customerName: string;
      customerEmail?: string;
      shippingAddress: string;
      customerShippingCharge: string;
      items: Array<{ sku: string; quantity: number; unitPrice: string }>;
    };
    const created: Order = {
      ...pendingOrder,
      id: uuid('299'),
      orderNumber: 'ORD-2026-0114',
      customerName: body.customerName,
      customerEmail: body.customerEmail ?? null,
      shippingAddress: body.shippingAddress,
      customerShippingCharge: body.customerShippingCharge,
      items: body.items.map((item, index) => ({
        id: uuid(`299${index + 1}`),
        sku: item.sku,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      })),
      createdAt: new Date().toISOString(),
    };
    runtimeOrders = [created, ...runtimeOrders];
    return ok(created);
  }),
  http.get('/api/v1/orders/:orderId/returns', ({ params }) => {
    const orderReturns = returns.filter((record) => record.orderId === params.orderId);
    return ok(orderReturns);
  }),
  http.post('/api/v1/orders/:orderId/returns', async ({ params, request }) => {
    const order = orderById(String(params.orderId));
    if (!order) return notFound('Order');
    const body = (await request.json()) as {
      reason?: string;
      operatorNotes?: string;
      items: Array<{ orderItemId: string; quantity: number; condition: 'SELLABLE' | 'DAMAGED' }>;
    };
    const record = {
      id: uuid('399'),
      orderId: order.id,
      returnReason: body.reason ?? null,
      operatorNotes: body.operatorNotes ?? null,
      processedBy: ownerUser,
      processedAt: new Date().toISOString(),
      items: body.items.map((item, index) => ({
        id: uuid(`399${index + 1}`),
        orderItemId: item.orderItemId,
        sku: order.items.find((entry) => entry.id === item.orderItemId)?.sku ?? 'UNKNOWN',
        quantity: item.quantity,
        condition: item.condition,
        restocked: item.condition === 'SELLABLE',
      })),
    };
    return ok(record);
  }),
  http.post('/api/v1/orders/:orderId/pack', ({ params }) => {
    const order = orderById(String(params.orderId));
    if (!order) return notFound('Order');
    return ok(updatedOrder(order, { status: 'READY_TO_SHIP' }));
  }),
  http.post('/api/v1/orders/:orderId/ship', async ({ params, request }) => {
    const order = orderById(String(params.orderId));
    if (!order) return notFound('Order');
    const body = (await request.json()) as {
      carrier: Order['carrier'];
      parcelSize: Order['parcelSize'];
      trackingNumber: string;
      actualShippingCostOverride?: string | null;
    };
    return ok(
      updatedOrder(order, {
        status: 'SHIPPED',
        carrier: body.carrier,
        parcelSize: body.parcelSize,
        trackingNumber: body.trackingNumber,
        trackingUrl: `https://carrier.example/track/${body.trackingNumber}`,
        actualShippingCost: body.actualShippingCostOverride ?? '5.80',
        shippedAt: new Date().toISOString(),
      }),
    );
  }),
  http.post('/api/v1/orders/:orderId/cancel', ({ params }) => {
    const order = orderById(String(params.orderId));
    if (!order) return notFound('Order');
    return ok(updatedOrder(order, { status: 'CANCELLED' }));
  }),
  http.get('/api/v1/orders/:orderId', ({ params }) => {
    const order = orderById(String(params.orderId));
    return order ? ok(order) : notFound('Order');
  }),

  http.get('/api/v1/shipping/rates', () => ok(runtimeRates)),
  http.put('/api/v1/shipping/rates', async ({ request }) => {
    const body = (await request.json()) as ShippingRate;
    const existing = runtimeRates.find(
      (rate) => rate.carrier === body.carrier && rate.parcelSize === body.parcelSize,
    );
    const saved: ShippingRate = {
      id: existing?.id ?? uuid('799'),
      carrier: body.carrier,
      parcelSize: body.parcelSize,
      cost: body.cost,
      active: body.active,
    };
    runtimeRates = existing
      ? runtimeRates.map((rate) => (rate.id === existing.id ? saved : rate))
      : [...runtimeRates, saved];
    return ok(saved);
  }),
  http.get('/api/v1/analytics/shipping-summary', () => ok(shippingSummary)),
  http.get('/api/v1/analytics/reorder', () =>
    ok({ ...reorderList, leadTimeDays: runtimeReorderSettings.leadTimeDays }),
  ),
  http.get('/api/v1/analytics/reorder/:variantId', ({ params }) => {
    const detail = reorderDetails[String(params.variantId)];
    return detail ? ok(detail) : notFound('Reorder trace');
  }),
  http.get('/api/v1/settings/reorder', () => ok(runtimeReorderSettings)),
  http.put('/api/v1/settings/reorder', async ({ request }) => {
    const body = (await request.json()) as { leadTimeDays?: number };
    const leadTimeDays = body.leadTimeDays;
    if (
      typeof leadTimeDays !== 'number' ||
      !Number.isInteger(leadTimeDays) ||
      leadTimeDays < 1 ||
      leadTimeDays > 365
    ) {
      return problem(
        400,
        'Validation Failed',
        'The request contains invalid fields.',
        'VALIDATION_FAILED',
        [
          {
            field: 'leadTimeDays',
            message: 'Supplier lead time must be between 1 and 365 days.',
          },
        ],
      );
    }
    runtimeReorderSettings = {
      leadTimeDays,
      updatedAt: new Date().toISOString(),
    };
    return ok(runtimeReorderSettings);
  }),

  http.get('/api/v1/platforms', () => ok(runtimePlatforms)),
  http.get('/api/v1/platforms/catalog', () => ok(platformCatalog)),
  http.post('/api/v1/platforms', async ({ request }) => {
    const requestKey = request.headers.get('Idempotency-Key');
    if (requestKey) {
      const replay = runtimePlatformCreationKeys.get(requestKey);
      if (replay) return ok(replay);
    }

    const body = (await request.json()) as CreatePlatformInput;
    const catalogEntry = platformCatalog.find(
      (candidate) => candidate.platformType === body.platformType,
    );
    if (!catalogEntry) {
      return problem(
        400,
        'Unsupported Platform Type',
        `Platform type '${body.platformType}' is not supported.`,
        'UNSUPPORTED_PLATFORM_TYPE',
      );
    }

    const displayName = body.displayName?.trim() || catalogEntry.displayName;
    const defaultSlug =
      catalogEntry.webhookPath.split('/').filter(Boolean).pop() ??
      catalogEntry.platformType.toLowerCase();
    const slug = body.slug?.trim().toLowerCase() || slugify(displayName) || defaultSlug;
    const credentialsConfigured =
      catalogEntry.credentialFields.length === 0 ||
      catalogEntry.credentialFields.every((field) => Boolean(body[field.key]?.trim()));
    const now = new Date().toISOString();
    const connection: PlatformConnection = {
      id: crypto.randomUUID(),
      version: 0,
      platformType: body.platformType,
      displayName,
      slug,
      apiEndpoint: body.apiEndpoint.trim(),
      webhookPath: `/api/v1/webhooks/orders/${slug}`,
      syncStatus: 'PENDING',
      syncSupported: catalogEntry.outboundSync,
      lastSyncAt: null,
      lastErrorMessage: null,
      active: true,
      webhookConfigured: true,
      credentialsConfigured,
      createdAt: now,
      updatedAt: now,
      deactivatedAt: null,
      pauseReason: null,
      lastTestedAt: null,
      lastTestOutcome: null,
      lastTestHttpStatus: null,
      lastTestDetail: null,
      lastInboundOrderAt: null,
    };

    runtimePlatforms = [...runtimePlatforms, connection];
    if (requestKey) runtimePlatformCreationKeys.set(requestKey, connection);
    return HttpResponse.json(envelope(connection), { status: 201 });
  }),
  http.patch('/api/v1/platforms/:platformId', async ({ params, request }) => {
    const connection = runtimePlatforms.find((entry) => entry.id === params.platformId);
    if (!connection) return notFound('Platform connection');

    const body = (await request.json()) as UpdatePlatformInput;
    if (body.version !== connection.version) {
      return problem(
        409,
        'Concurrent Change',
        'This connection changed after you opened it. Reload it and apply your change again.',
        'CONCURRENT_CHANGE',
      );
    }
    if (body.active === false && !body.pauseReason?.trim()) {
      return problem(
        400,
        'Pause Reason Required',
        'Give a reason for pausing this connection. It is recorded in the connection history.',
        'PAUSE_REASON_REQUIRED',
      );
    }
    if (body.active === true) {
      const addressTaken = runtimePlatforms.some(
        (entry) => entry.id !== connection.id && entry.active && entry.slug === connection.slug,
      );
      if (addressTaken) {
        return problem(
          409,
          'Webhook Address In Use',
          `Another active connection already receives orders at ${connection.webhookPath}. Pause that connection first.`,
          'PLATFORM_WEBHOOK_ADDRESS_IN_USE',
        );
      }
    }

    const now = new Date().toISOString();
    const next: PlatformConnection = { ...connection };
    const changed: string[] = [];

    if (body.displayName !== undefined && body.displayName.trim() !== connection.displayName) {
      next.displayName = body.displayName.trim();
      changed.push('displayName');
    }
    if (body.apiEndpoint !== undefined && body.apiEndpoint.trim() !== connection.apiEndpoint) {
      next.apiEndpoint = body.apiEndpoint.trim();
      changed.push('apiEndpoint');
    }
    if (body.webhookSecret?.trim()) changed.push('webhookSecret');
    if (
      ['accessToken', 'consumerKey', 'consumerSecret'].some((key) =>
        Boolean(body[key as 'accessToken' | 'consumerKey' | 'consumerSecret']?.trim()),
      )
    ) {
      changed.push('apiCredentials');
    }
    if (body.active !== undefined && body.active !== connection.active) {
      next.active = body.active;
      if (body.active) {
        next.deactivatedAt = null;
        next.pauseReason = null;
      } else {
        next.deactivatedAt = now;
        next.pauseReason = body.pauseReason?.trim() ?? null;
      }
      changed.push('active');
    }

    if (changed.length === 0) {
      return ok(connection);
    }

    next.version = connection.version + 1;
    next.updatedAt = now;
    runtimePlatforms = runtimePlatforms.map((entry) =>
      entry.id === connection.id ? next : entry,
    );

    const onlyStatusChanged = changed.length === 1 && changed[0] === 'active';
    const changeType = onlyStatusChanged ? (next.active ? 'RESUMED' : 'PAUSED') : 'UPDATED';
    const event: PlatformConnectionEvent = {
      id: crypto.randomUUID(),
      platformConnectionId: connection.id,
      platformType: connection.platformType,
      changeType,
      actorUserId: ownerUser.id,
      actorEmail: ownerUser.email,
      fields: onlyStatusChanged ? ['active'] : changed,
      reason: changeType === 'PAUSED' ? (next.pauseReason ?? null) : null,
      outcome: null,
      detail: null,
      occurredAt: now,
    };
    runtimePlatformEvents = [event, ...runtimePlatformEvents];

    return ok(next);
  }),
  http.get('/api/v1/platforms/:platformId/events', ({ params, request }) => {
    const connection = runtimePlatforms.find((entry) => entry.id === params.platformId);
    if (!connection) return notFound('Platform connection');

    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');
    const all = runtimePlatformEvents.filter(
      (event) => event.platformConnectionId === connection.id,
    );
    const start = page * size;
    const totalPages = size > 0 ? Math.ceil(all.length / size) : 0;
    return ok(all.slice(start, start + size), {
      page,
      size,
      totalElements: all.length,
      totalPages,
      hasMore: page + 1 < totalPages,
    });
  }),
  http.post('/api/v1/platforms/:platformId/sync', ({ params }) => {
    const connection = runtimePlatforms.find((entry) => entry.id === params.platformId);
    if (!connection) return notFound('Platform connection');
    const result: PlatformSyncResult = {
      connectionId: connection.id,
      status: 'SUCCESS',
      lastSyncAt: new Date().toISOString(),
      lastErrorMessage: null,
    };
    runtimePlatforms = runtimePlatforms.map((entry) =>
      entry.id === connection.id
        ? {
            ...entry,
            syncStatus: result.status,
            lastSyncAt: result.lastSyncAt,
            lastErrorMessage: null,
          }
        : entry,
    );
    return ok(result);
  }),

  http.get('/api/v1/admin/users', () => ok(runtimeUsers)),
  http.post('/api/v1/admin/users', async ({ request }) => {
    const body = (await request.json()) as {
      email: string;
      fullName: string;
      role: 'OWNER' | 'STAFF';
    };
    if (runtimeUsers.some((user) => user.email === body.email)) {
      return problem(409, 'Duplicate User', 'A user with that email already exists.', 'DUPLICATE_RESOURCE');
    }
    const created: User = {
      id: uuid('399'),
      email: body.email,
      fullName: body.fullName,
      role: body.role,
      active: true,
      mustChangePassword: true,
    };
    runtimeUsers = [...runtimeUsers, created];
    return ok(created);
  }),
  http.patch('/api/v1/admin/users/:userId', async ({ params, request }) => {
    const body = (await request.json()) as Partial<Pick<User, 'fullName' | 'role' | 'active'>>;
    const existing = runtimeUsers.find((user) => user.id === params.userId);
    if (!existing) return notFound('User');
    if (existing.id === ownerUser.id && body.active === false) {
      return problem(
        409,
        'Last Owner',
        'You cannot deactivate your own owner account.',
        'LAST_ACTIVE_OWNER',
      );
    }
    const updated = { ...existing, ...body };
    runtimeUsers = runtimeUsers.map((user) => (user.id === updated.id ? updated : user));
    return ok(updated);
  }),
  http.delete('/api/v1/admin/users/:userId', ({ params }) => {
    const existing = runtimeUsers.find((user) => user.id === params.userId);
    if (!existing) return notFound('User');
    const updated = { ...existing, active: false };
    runtimeUsers = runtimeUsers.map((user) => (user.id === updated.id ? updated : user));
    return ok(updated);
  }),
  http.post('/api/v1/admin/users/:userId/password-reset', async ({ params, request }) => {
    const existing = runtimeUsers.find((user) => user.id === params.userId);
    if (!existing) return notFound('User');
    if (existing.id === ownerUser.id) {
      return problem(
        409,
        'Self Password Reset',
        'Use the account menu to change your own password.',
        'SELF_PASSWORD_RESET_NOT_ALLOWED',
      );
    }
    const body = (await request.json()) as { newPassword?: string };
    if (!body.newPassword || body.newPassword.length < 12) {
      return problem(400, 'Weak Password', 'The new password is too weak.', 'WEAK_PASSWORD', [
        { field: 'newPassword', message: 'Password must be at least 12 characters.' },
      ]);
    }
    const updated = { ...existing, mustChangePassword: true };
    runtimeUsers = runtimeUsers.map((user) => (user.id === updated.id ? updated : user));
    return ok(updated);
  }),

  http.get('/api/v1/admin/auth-events', ({ request }) => {
    const url = new URL(request.url);
    const eventTypes = url.searchParams.getAll('eventType');
    const actorEmail = url.searchParams.get('actorEmail')?.trim().toLowerCase();
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');
    const filtered = runtimeAuthEvents.filter((event) => {
      if (eventTypes.length > 0 && !eventTypes.includes(event.eventType)) return false;
      if (actorEmail && !(event.actorEmail ?? '').toLowerCase().includes(actorEmail)) {
        return false;
      }
      if (from && event.occurredAt < from) return false;
      if (to && event.occurredAt > to) return false;
      return true;
    });
    const result = paginate(filtered, request);
    return ok(result.data, result.pagination);
  }),

  http.get('/api/v1/export/:kind', ({ params }) => {
    const kind = String(params.kind);
    const filename =
      kind === 'catalog'
        ? 'catalog_export.csv'
        : kind === 'orders'
          ? 'orders_export.csv'
          : 'stock_history_export.csv';
    return new HttpResponse('sku,product\nHOOD-NVY-XL,Heavyweight Cotton Hoodie\n', {
      headers: {
        'Content-Type': 'text/csv;charset=UTF-8',
        'Content-Disposition': `attachment; filename="${filename}"`,
      },
    });
  }),
];

export const fixtureOrders = {
  cancelledOrder,
  pendingOrder,
  readyOrder,
  shippedOrder,
  waitingOrder,
};
