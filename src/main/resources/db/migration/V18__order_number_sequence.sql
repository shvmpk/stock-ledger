-- Manual order numbers used a random five-digit suffix, whose collision
-- probability grows with the store's lifetime order count (the unique
-- index on orders.order_number spans all history). A database sequence
-- hands out the suffix atomically instead: concurrent orders can never
-- draw the same number, and rolled-back transactions merely burn a gap.
CREATE SEQUENCE IF NOT EXISTS order_number_seq AS BIGINT;

-- Seed above the highest suffix already stored so new numbers can never
-- collide with rows written before this migration. The lookup is scoped
-- to the application's own ORD-<year>-<digits> shape; CSV-imported
-- external order numbers may use arbitrary references and are skipped.
SELECT setval(
    'order_number_seq',
    GREATEST(
        (SELECT COALESCE(MAX(substring(order_number FROM '[0-9]+$')::bigint), 0)
         FROM orders
         WHERE order_number ~ '^ORD-[0-9]{4}-[0-9]+$'),
        89999
    )
);
