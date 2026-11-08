-- V9__multiple_stores_per_platform.sql
-- A merchant may run several stores on the same platform (for example two
-- Shopify storefronts). V5 enforced one active connection per platform type;
-- V6 introduced slugs and their own partial unique index but left the older
-- type index in place, so the second store could be created in code and then
-- failed at the database with a constraint error that the service reported as
-- "an active connection already exists".
--
-- The webhook slug is the identity of a connection. Uniqueness among active
-- connections is already enforced by uq_platform_connections_active_slug.

DROP INDEX IF EXISTS uq_platform_connections_active_type;
