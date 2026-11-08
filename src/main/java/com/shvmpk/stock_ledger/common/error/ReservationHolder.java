package com.shvmpk.stock_ledger.common.error;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One open order line that is holding reserved stock for a variant.
 */
public record ReservationHolder(
        UUID orderId,
        String orderNumber,
        int reservedQuantity,
        OffsetDateTime orderDate
) {
}
