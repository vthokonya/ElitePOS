# ElitePOS

This is a web application based on the [Jmix](https://www.jmix.io) framework.

## Getting Started

The newly created project requires Java 21 and uses the embedded HSQL database.

Use the following resources to learn more about Jmix:
* [Jmix Documentation](https://docs.jmix.io)
* [Online Demo Applications](https://www.jmix.io/live-demo)
* [Jmix AI Assistant](https://ai-assistant.jmix.io) (also available in the **Jmix AI** tool window of IntelliJ IDEA)

## Development

- [Setup](https://docs.jmix.io/jmix/setup.html) your development environment.
- [Open](https://docs.jmix.io/jmix/studio/project.html#opening-existing-project) the project in the IDE.
- If you want to use AI agents to develop the application, check out the [Jmix AI Agent Guidelines](https://github.com/jmix-framework/jmix-agent-guidelines) repository.

## Running

To start the application, use the **ElitePOS Jmix Application** run configuration in your IDE, or run the following command in the project root directory:

```bash
./gradlew bootRun
```

The application will be available at <http://localhost:8080>.

## POS and inventory workflows

- In **Stores, Products & Suppliers**, create currencies, stores (with one trading currency each), products with barcode and product-level tax rate, store-specific prices and opening stock, suppliers, supplier-product links, and manual exchange rates.
- In **Point of Sale**, select a store and payment currency. Scan a barcode (USB scanners that type into the focused field work) to add one item; scan it again to increase its quantity. Checkout validates stock, applies product tax, uses the current configured exchange rate, records the sale, and deducts stock.
- POS users have separate checkout accounts created under **Stores, Products & Suppliers → Checkout users**. Checkout users sign in with a four-digit numeric PIN (stored as a hash). A cashier can sell only when a supervisor has opened and assigned that cashier a shift. Cashiers select cash, card, or other tender; product and transaction cancellations require a supervisor's separate username/PIN and a reason.
- Supervisors open shifts with an opening float, close selected cashier shifts with counted cash (the system records expected cash and variance), and close the trading day only after all store shifts are closed. Day close records cash and non-cash sales totals.
- In **Purchasing & Payables**, create purchase orders for products linked to the selected supplier, in the store's trading currency; receive deliveries partially or in full; register supplier invoices; match invoice quantities and unit costs against received goods; and record partial or final payments.
- Inventory is maintained per store. Receipts, sales, opening stock, and manual adjustments are recorded as stock movements. Exchange rates are entered as target currency units per one source currency unit and saved with effective times.

The default user credentials are:
* Login: `admin`
* Password: `admin`

**WARNING**: Change admin password and remove `ui.login.defaultUsername` and `ui.login.defaultPassword` application properties when deploying the application to production.
