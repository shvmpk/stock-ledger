export const semantic = {
  success: 'var(--sl-color-success)',
  successSurface: 'var(--sl-color-success-surface)',
  warning: 'var(--sl-color-warning)',
  warningSurface: 'var(--sl-color-warning-surface)',
  danger: 'var(--sl-color-danger)',
  dangerSurface: 'var(--sl-color-danger-surface)',
  info: 'var(--sl-color-info)',
  infoSurface: 'var(--sl-color-info-surface)',
  border: 'var(--sl-color-border)',
  muted: 'var(--sl-color-muted)',
  surface: 'var(--sl-color-surface)',
  surfaceSubtle: 'var(--sl-color-surface-subtle)',
} as const;

export const carrierLabels = {
  DHL: 'DHL Express',
  FEDEX: 'FedEx',
  UPS: 'UPS',
  ROYAL_MAIL: 'Royal Mail',
} as const;

export const parcelLabels = {
  SMALL: 'Small',
  MEDIUM: 'Medium',
  LARGE: 'Large',
} as const;

export const statusLabels = {
  PENDING: 'Pending',
  WAITING_FOR_STOCK: 'Waiting for stock',
  READY_TO_SHIP: 'Ready to ship',
  SHIPPED: 'Shipped',
  RETURNED: 'Returned',
  PARTIALLY_RETURNED: 'Partially returned',
  CANCELLED: 'Cancelled',
} as const;

export const movementReasonLabels = {
  RECEIVE: 'Receive',
  ORDER_RESERVED: 'Order reserved',
  ORDER_SHIPPED: 'Order shipped',
  ORDER_CANCELLED: 'Order cancelled',
  RETURN_RESTOCK: 'Return restocked',
  RETURN_DAMAGED: 'Return damaged',
  AUDIT_CORRECTION: 'Audit correction',
} as const;
