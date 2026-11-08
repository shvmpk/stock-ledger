export type Role = 'OWNER' | 'STAFF';

export type OrderStatus =
  | 'PENDING'
  | 'WAITING_FOR_STOCK'
  | 'READY_TO_SHIP'
  | 'SHIPPED'
  | 'RETURNED'
  | 'PARTIALLY_RETURNED'
  | 'CANCELLED';

export type OrderSource = 'MANUAL' | 'CSV' | 'WEBHOOK' | 'PLATFORM_SYNC';

export type StockMovementReason =
  | 'RECEIVE'
  | 'ORDER_RESERVED'
  | 'ORDER_SHIPPED'
  | 'ORDER_CANCELLED'
  | 'RETURN_RESTOCK'
  | 'RETURN_DAMAGED'
  | 'AUDIT_CORRECTION';

export type Carrier = 'DHL' | 'FEDEX' | 'UPS' | 'ROYAL_MAIL';
export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';
export type ItemCondition = 'SELLABLE' | 'DAMAGED';
export type StockCountStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type PlatformType =
  | 'SHOPIFY'
  | 'WOOCOMMERCE'
  | 'ETSY'
  | 'TIKTOK_SHOP'
  | 'AMAZON'
  | 'SQUARE'
  | 'CUSTOM_WEBHOOK';
export type PlatformSyncStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'RUNNING';
export type PlatformConnectionChangeType =
  | 'CREATED'
  | 'UPDATED'
  | 'PAUSED'
  | 'RESUMED'
  | 'TESTED';
export type PlatformTestOutcome =
  | 'SUCCESS'
  | 'AUTHENTICATION_FAILED'
  | 'FAILED'
  | 'UNREACHABLE'
  | 'AWAITING_INBOUND'
  | 'NOT_CONFIGURED';

export type PlatformCredentialFieldKey = 'accessToken' | 'consumerKey' | 'consumerSecret';

export interface PlatformCredentialField {
  key: PlatformCredentialFieldKey;
  label: string;
  secret: boolean;
}

export interface PlatformCatalogEntry {
  platformType: PlatformType;
  displayName: string;
  supported: boolean;
  unsupportedReason: string | null;
  outboundSync: boolean;
  webhookPath: string;
  apiEndpointExample: string;
  credentialFields: PlatformCredentialField[];
  signatureHeaders: string[];
}

export type MoneyValue = string | number;

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  mustChangePassword: boolean;
}

export type AuthEventType =
  | 'LOGIN_SUCCEEDED'
  | 'LOGIN_FAILED'
  | 'LOGOUT'
  | 'PASSWORD_CHANGED'
  | 'PASSWORD_RESET'
  | 'USER_CREATED'
  | 'USER_DEACTIVATED'
  | 'USER_REACTIVATED'
  | 'ROLE_CHANGED';

export type AuthFailureReason = 'INVALID_PASSWORD' | 'UNKNOWN_EMAIL' | 'INACTIVE_ACCOUNT';

export interface AuthEvent {
  id: string;
  eventType: AuthEventType;
  failureReason: AuthFailureReason | null;
  actorUserId: string | null;
  actorEmail: string | null;
  subjectUserId: string | null;
  subjectEmail: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  requestId: string | null;
  details: Record<string, string>;
  occurredAt: string;
}

export interface AuthEventFilters {
  page?: number;
  size?: number;
  eventType?: AuthEventType | AuthEventType[];
  actorEmail?: string;
  from?: string;
  to?: string;
}

export interface PaginationMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

export interface ResponseMeta {
  timestamp: string;
  requestId?: string;
}

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  pagination?: PaginationMeta;
  meta: ResponseMeta;
}

export interface InvalidParam {
  field: string;
  rejectedValue?: unknown;
  message: string;
}

export type StockActionCode =
  | 'RECEIVE_STOCK'
  | 'REDUCE_QUANTITY'
  | 'VIEW_MOVEMENTS'
  | 'VIEW_RESERVATIONS'
  | 'VIEW_RETIRED_PRODUCTS';

export interface StockAction {
  code: StockActionCode | string;
  label: string;
}

export interface ProblemReservationHolder {
  orderId: string;
  orderNumber: string;
  reservedQuantity: number;
  orderDate: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  timestamp?: string;
  invalidParams?: InvalidParam[];
  sku?: string;
  requestedQuantity?: number;
  availableQuantity?: number;
  onHandQuantity?: number;
  reservedQuantity?: number;
  reservedBy?: ProblemReservationHolder[];
  actions?: StockAction[];
}

export interface ProductVariant {
  id: string;
  sku: string;
  barcode: string;
  optionName: string;
  optionValue: string;
  active: boolean;
  costPrice?: MoneyValue | null;
  sellingPrice: MoneyValue;
  weightGrams: number | null;
  minStockThreshold: number | null;
  onHand: number;
  reserved: number;
  available: number;
  isLowStock?: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string;
  category: string;
  active: boolean;
  variants: ProductVariant[];
}

export interface ProductVariantInput {
  id?: string;
  sku: string;
  barcode?: string;
  optionName?: string;
  optionValue?: string;
  costPrice?: string;
  sellingPrice: string;
  weightGrams?: number;
  minStockThreshold?: number;
}

export interface ProductInput {
  name: string;
  description?: string;
  category?: string;
  variants: ProductVariantInput[];
}

export type ReorderHistoryStatus = 'SUFFICIENT' | 'INSUFFICIENT';

export interface ReorderItem {
  variantId: string;
  productId: string;
  productName: string;
  optionName: string;
  optionValue: string;
  sku: string;
  barcode: string;
  onHand: number;
  reserved: number;
  available: number;
  reorderLevel: number | null;
  unitsSold: number;
  ordersCount: number;
  firstSoldAt: string | null;
  daysOfCover: number | null;
  suggestedQuantity: number | null;
  historyStatus: ReorderHistoryStatus;
}

export interface ReorderList {
  salesWindowDays: number;
  leadTimeDays: number;
  targetCoverDays: number;
  minimumHistoryDays: number;
  minimumUnitsSold: number;
  generatedAt: string;
  items: ReorderItem[];
}

export interface ReorderSaleLine {
  orderId: string;
  orderNumber: string;
  shippedAt: string;
  quantity: number;
  status: OrderStatus;
}

export interface ReorderReturnLine {
  orderId: string;
  orderNumber: string;
  processedAt: string;
  quantity: number;
  condition: ItemCondition;
}

export interface ReorderDetail {
  variantId: string;
  productName: string;
  optionName: string;
  optionValue: string;
  sku: string;
  salesWindowDays: number;
  windowStart: string;
  windowEnd: string;
  unitsShipped: number;
  unitsReturned: number;
  netUnitsSold: number;
  sales: ReorderSaleLine[];
  returns: ReorderReturnLine[];
}

export interface ReorderSettings {
  leadTimeDays: number;
  updatedAt: string;
}

export interface UpdateReorderSettingsInput {
  leadTimeDays: number;
}

export interface StockLevel {
  id: string;
  productVariantId: string;
  sku: string;
  onHandQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
}

export interface ReceiveStockResult {
  stockLevel: StockLevel;
  allocatedOrderNumbers: string[];
}

export interface StockMovement {
  id: string;
  productVariantId: string;
  sku: string;
  productName: string;
  deltaOnHand: number;
  deltaReserved: number;
  reason: StockMovementReason;
  referenceType: string | null;
  referenceId: string | null;
  notes: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface StockExplanationReservation {
  orderId: string;
  orderNumber: string;
  status: OrderStatus;
  reservedQuantity: number;
  orderedAt: string;
}

export interface StockMovementSummary {
  reason: StockMovementReason;
  label: string;
  movementCount: number;
  netOnHand: number;
  netReserved: number;
  latestAt: string;
}

export interface StockExplanation {
  productVariantId: string;
  sku: string;
  productName: string;
  optionName: string;
  optionValue: string;
  onHandQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  reorderLevel: number | null;
  sellable: boolean;
  movementWindowDays: number;
  windowStart: string;
  reservations: StockExplanationReservation[];
  recentMovements: StockMovementSummary[];
  sentences: string[];
  generatedAt: string;
}

export interface StockCountOperator {
  id: string;
  fullName: string;
  email: string;
}

export interface StockCountItem {
  id: string;
  productVariantId: string;
  sku: string;
  productName: string;
  optionName: string;
  optionValue: string;
  expectedOnHand: number;
  countedOnHand: number;
  variance: number;
  notes: string | null;
  hasReservationDeficit: boolean;
  reservationDeficit: number;
}

export interface StockCountSummary {
  id: string;
  status: StockCountStatus;
  conductedBy: StockCountOperator | null;
  startedAt: string;
  completedAt: string | null;
  itemCount: number;
  totalVariance: number;
  hasReservationDeficit: boolean;
  reservationDeficit: number;
}

export interface StockCount extends StockCountSummary {
  items: StockCountItem[];
}

export interface OrderItem {
  id: string;
  sku: string;
  quantity: number;
  unitPrice: MoneyValue;
}

export interface Order {
  id: string;
  orderNumber: string;
  source: OrderSource;
  status: OrderStatus;
  customerName: string;
  customerEmail: string | null;
  shippingAddress: string;
  customerShippingCharge: MoneyValue;
  actualShippingCost: MoneyValue | null;
  carrier: Carrier | null;
  parcelSize: ParcelSize | null;
  trackingNumber: string | null;
  trackingUrl: string | null;
  shippedAt: string | null;
  returnedAt: string | null;
  items: OrderItem[];
  createdAt: string;
}

export interface OrderStatusSummary {
  status: OrderStatus;
  count: number;
}

export interface CsvRowError {
  rowNumber: number;
  orderNumber: string | null;
  sku: string | null;
  error: string;
}

export interface CsvImportResult {
  totalRows: number;
  importedOrders: number;
  waitingForStockOrders: number;
  failedRows: number;
  errors: CsvRowError[];
}

export interface ReturnItem {
  id: string;
  orderItemId: string;
  sku: string;
  quantity: number;
  condition: ItemCondition;
  restocked: boolean;
}

export interface ReturnRecord {
  id: string;
  orderId: string;
  returnReason: string | null;
  operatorNotes: string | null;
  processedBy: StockCountOperator | null;
  items: ReturnItem[];
  processedAt: string;
}

export interface ShippingRate {
  id: string;
  carrier: Carrier;
  parcelSize: ParcelSize;
  cost: MoneyValue;
  active: boolean;
}

export interface ShippingSummary {
  period: string;
  totalOrdersShipped: number;
  totalShippingCharged: MoneyValue;
  totalShippingCost: MoneyValue;
  netMargin: MoneyValue;
}

export interface PlatformConnection {
  id: string;
  version: number;
  platformType: PlatformType;
  displayName: string;
  slug: string;
  apiEndpoint: string;
  webhookPath: string;
  syncStatus: PlatformSyncStatus;
  syncSupported: boolean;
  lastSyncAt: string | null;
  lastErrorMessage: string | null;
  active: boolean;
  webhookConfigured: boolean;
  credentialsConfigured: boolean;
  createdAt: string;
  updatedAt: string;
  deactivatedAt: string | null;
  pauseReason: string | null;
  lastTestedAt: string | null;
  lastTestOutcome: PlatformTestOutcome | null;
  lastTestHttpStatus: number | null;
  lastTestDetail: string | null;
  lastInboundOrderAt: string | null;
}

export interface PlatformConnectionEvent {
  id: string;
  platformConnectionId: string;
  platformType: PlatformType;
  changeType: PlatformConnectionChangeType;
  actorUserId: string | null;
  actorEmail: string | null;
  fields: string[];
  reason: string | null;
  outcome: PlatformTestOutcome | null;
  detail: string | null;
  occurredAt: string;
}

export interface PlatformTestResult {
  connectionId: string;
  outcome: PlatformTestOutcome;
  httpStatus: number | null;
  message: string;
  testedAt: string | null;
  lastInboundOrderAt: string | null;
}

export interface CreatePlatformInput {
  platformType: PlatformType;
  displayName?: string;
  slug?: string;
  apiEndpoint: string;
  webhookSecret: string;
  accessToken?: string;
  consumerKey?: string;
  consumerSecret?: string;
}

export interface UpdatePlatformInput {
  version: number;
  displayName?: string;
  apiEndpoint?: string;
  webhookSecret?: string;
  accessToken?: string;
  consumerKey?: string;
  consumerSecret?: string;
  active?: boolean;
  pauseReason?: string;
}

export interface PlatformSyncResult {
  connectionId: string;
  status: PlatformSyncStatus;
  lastSyncAt: string | null;
  lastErrorMessage: string | null;
}

export interface CreateOrderItemInput {
  sku: string;
  quantity: number;
  unitPrice: string;
}

export interface CreateOrderInput {
  customerName: string;
  customerEmail?: string;
  shippingAddress: string;
  customerShippingCharge: string;
  items: CreateOrderItemInput[];
}

export interface ShipOrderInput {
  carrier: Carrier;
  parcelSize: ParcelSize;
  trackingNumber: string;
  actualShippingCostOverride?: string | null;
}

export interface ProcessReturnInput {
  reason?: string;
  operatorNotes?: string;
  items: Array<{
    orderItemId: string;
    quantity: number;
    condition: ItemCondition;
  }>;
}

export interface CreateUserInput {
  email: string;
  fullName: string;
  password: string;
  role: Role;
}

export interface UpdateUserInput {
  fullName?: string;
  role?: Role;
  active?: boolean;
}

export interface UpdateShippingRateInput {
  carrier: Carrier;
  parcelSize: ParcelSize;
  cost: string;
  active: boolean;
}

export interface MovementFilters {
  page?: number;
  size?: number;
  sku?: string;
  reason?: StockMovementReason | '';
  from?: string;
  to?: string;
}
