package com.shvmpk.stock_ledger.unit.order;

import com.shvmpk.stock_ledger.common.error.InvalidOrderStateTransitionException;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order Backlog Domain Tests (Waiting Orders & Unreserved Items)")
class OrderBacklogDomainTest {

    @Test
    @DisplayName("Should create a waiting order with unreserved items")
    void shouldCreateWaitingOrderWithUnreservedItems() {
        ProductVariant variant = coffeeVariant();
        Order order = Order.createWaiting(
                "ORD-CSV-3101",
                OrderSource.CSV,
                "csv-row-3101",
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se",
                "Drottninggatan 12, 411 14 Gothenburg, Sweden",
                new BigDecimal("4.99")
        );

        OrderItem item = OrderItem.createUnreserved(
                order,
                variant,
                2,
                new BigDecimal("14.50")
        );
        order.addItem(item);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
        assertThat(order.getItems()).hasSize(1);
        assertThat(item.getReservedQuantity()).isZero();
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should mark a PENDING order as waiting for stock")
    void shouldMarkPendingOrderAsWaitingForStock() {
        Order order = Order.create(
                "ORD-CSV-3102",
                OrderSource.CSV,
                "csv-row-3102",
                "Elin Sundqvist",
                "elin.sundqvist@goteborg.se",
                "Avenyn 5, 411 36 Gothenburg, Sweden",
                new BigDecimal("3.50")
        );

        order.markWaitingForStock();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_FOR_STOCK);
    }

    @Test
    @DisplayName("Should reject waiting-for-stock transition after allocation")
    void shouldRejectWaitingForStockTransitionWhenNotPending() {
        Order order = Order.createWaiting(
                "ORD-CSV-3103",
                OrderSource.CSV,
                "csv-row-3103",
                "Astrid Lindqvist",
                "astrid.lindqvist@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("5.99")
        );

        assertThatThrownBy(order::markWaitingForStock)
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .hasMessageContaining("WAITING_FOR_STOCK");
    }

    private ProductVariant coffeeVariant() {
        Product coffee = Product.create(
                UUID.randomUUID(),
                "Ethiopia Yirgacheffe Washed Coffee",
                "Heirloom varietal, floral jasmine and bergamot notes",
                "Coffee"
        );
        return coffee.addVariant(
                UUID.randomUUID(),
                "COF-ETH-250G",
                "5060123456790",
                "Grind / Size",
                "Whole Bean / 250g",
                new BigDecimal("6.50"),
                new BigDecimal("14.50"),
                250,
                10
        );
    }
}
