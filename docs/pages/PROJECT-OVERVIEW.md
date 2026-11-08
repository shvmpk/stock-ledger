# StockLedger

A simple app for small online shops. It keeps your orders and stock in one place, so you always know what you have, what is reserved for customers, and what needs to be shipped.

## The problem

Many small shops manage orders and stock using spreadsheets or separate tools. It's easy to:

- Sell something that is already out of stock.
- Lose track of the stock actually available on the shelf.
- Forget which orders are waiting, packed, shipped or returned.
- Miss products that need restocking.
- Lose track of shipping costs and stock changes.

Big inventory tools can solve these problems, but they often have too many features and menus for a small shop.

## What StockLedger does

1. **Orders come in.** StockLedger supports four ways to receive orders: connect an online shop or supported platform for automatic syncing, receive orders through webhooks, import orders from a CSV file, or enter them manually. StockLedger checks available stock and reserves items for each order.
2. **You pack and ship orders.** Pack the items and send them using your preferred carrier, such as DHL. You continue to handle packing and posting yourself.
3. **You mark orders as shipped.** Enter the tracking number and select the parcel size. StockLedger calculates the shipping cost using a price list you configure. Stock quantities update automatically, and a **Track parcel** button opens the carrier's tracking page.
4. **You receive new stock.** Enter the quantity received. StockLedger updates inventory and makes the newly available stock usable for orders that were waiting.
5. **You handle returns.** When a parcel comes back, mark the order as returned. Check the item's condition before deciding whether to add it back to sellable stock.
6. **You know when stock is running low.** Set a minimum quantity for each product and receive a warning when stock falls below that level.

## Other useful features

- **Stock history:** See every stock change and why it happened.
- **Product variants:** Track different sizes, colours or other variations separately.
- **Order sync status:** See whether connected platforms are syncing properly and when the last successful sync occurred.
- **Shipping records:** Track what shipping costs you and what customers paid for shipping.
- **Monthly shipping summary:** Compare shipping costs with shipping charges collected.
- **Stock counting:** Count items on your shelf and correct differences in StockLedger.
- **Data export:** Export your data for backup or use elsewhere.

## The screens

The app has four areas: three for daily work, plus owner settings for configuration.

- **Stock:**
  - All Products
  - Reorder (what to buy next, and how much)
  - Stock Count
- **Orders:** every order sits in one of five queues —
  - New
  - Waiting for Stock
  - Ready to Ship
  - Shipped
  - Returns & Cancellations
- **History:**
  - Stock Movements
  - Shipping Summary (owners only)
- **Owner Settings (owners only):**
  - Shipping Rates
  - Platforms
  - Users
  - Security Log
  - Exports

## What StockLedger does not do

StockLedger does not send parcels, print shipping labels, track parcels in real time, create invoices, process payments or manage accounting.

The Track parcel button simply opens the carrier's tracking page. Automatic delivery updates are not included.

StockLedger focuses on one job: helping small online shops manage orders and stock accurately without complicated software.

## Roles


| Action                                     | Owner | Staff |
| ------------------------------------------ | ----- | ----- |
| Orders, packing, shipping                  | Yes   | Yes   |
| Receive stock, counts, view history        | Yes   | Yes   |
| Add, edit, retire or restore products      | Yes   | No    |
| Export data, shipping cost summary         | Yes   | No    |
| Settings (price list, platform connection) | Yes   | No    |
| Read the security log                      | Yes   | No    |
| Add or remove users                        | Yes   | No    |


## Tech Stack

- **Java 21 + Spring Boot:** Backend and business logic.
- **Spring Data JPA:** Database access, with native SQL for atomic stock updates.
- **Gradle:** Backend build tool.
- **PostgreSQL (postgres:18-alpine):** Store products, orders and inventory data.
- **Flyway:** Manage database schema changes.
- **Spring Security:** Login and access control.
- **Spring Boot Actuator:** Health check and basic monitoring.
- **React + TypeScript + Vite:** Frontend user interface.
- **Mantine UI:** Component library for the interface.
- **Bun:** Package manager for the frontend.
- **Docker:** Package the app so it runs the same everywhere.
- **JUnit 5 + Testcontainers:** Automated tests against a real database.
- **GitHub Actions:** Run the tests automatically on every change.

---