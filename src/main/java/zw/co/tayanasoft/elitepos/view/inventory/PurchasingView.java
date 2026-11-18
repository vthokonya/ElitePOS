package zw.co.tayanasoft.elitepos.view.inventory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import zw.co.tayanasoft.elitepos.entity.Currency;
import zw.co.tayanasoft.elitepos.entity.GoodsReceiptLine;
import zw.co.tayanasoft.elitepos.entity.PurchaseOrder;
import zw.co.tayanasoft.elitepos.entity.PurchaseOrderLine;
import zw.co.tayanasoft.elitepos.entity.Store;
import zw.co.tayanasoft.elitepos.entity.StoreProduct;
import zw.co.tayanasoft.elitepos.entity.Supplier;
import zw.co.tayanasoft.elitepos.entity.SupplierInvoice;
import zw.co.tayanasoft.elitepos.service.PosService;
import zw.co.tayanasoft.elitepos.view.main.MainView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Route(value = "purchasing", layout = MainView.class)
@ViewController(id = "PurchasingView")
@ViewDescriptor(path = "purchasing-view.xml")
public class PurchasingView extends StandardView {
    @ViewComponent
    private Div content;
    @Autowired
    private PosService posService;
    @Autowired
    private DataManager dataManager;

    private ComboBox<Store> orderStore;
    private ComboBox<Supplier> orderSupplier;
    private ComboBox<StoreProduct> orderProduct;
    private BigDecimalField orderQuantity;
    private BigDecimalField orderCost;
    private Grid<PendingOrderLine> pendingOrderGrid;
    private final List<PosService.PurchaseLineInput> pendingOrderLines = new ArrayList<>();

    private ComboBox<PurchaseOrder> receiptOrder;
    private ComboBox<PurchaseOrderLine> receiptOrderLine;
    private BigDecimalField receivedQuantity;
    private TextField receiptReference;
    private Grid<PendingReceiptLine> pendingReceiptGrid;
    private final Map<UUID, BigDecimal> pendingReceiptLines = new LinkedHashMap<>();

    private ComboBox<Store> invoiceStore;
    private ComboBox<Supplier> invoiceSupplier;
    private ComboBox<Currency> invoiceCurrency;
    private TextField invoiceNumber;
    private BigDecimalField invoiceTotal;
    private ComboBox<SupplierInvoice> matchInvoice;
    private ComboBox<GoodsReceiptLine> matchReceiptLine;
    private BigDecimalField matchQuantity;
    private BigDecimalField matchUnitCost;
    private Grid<PendingMatch> pendingMatchGrid;
    private final List<PosService.InvoiceMatchInput> pendingMatches = new ArrayList<>();

    private ComboBox<SupplierInvoice> paymentInvoice;
    private BigDecimalField paymentAmount;
    private TextField paymentReference;
    private Grid<PurchaseOrder> purchaseOrderGrid;
    private Grid<SupplierInvoice> matchInvoiceGrid;
    private Grid<SupplierInvoice> paymentInvoiceGrid;

    @Subscribe
    public void onReady(ReadyEvent event) {
        buildScreen();
        refreshData();
    }

    private void buildScreen() {
        content.removeAll();
        content.setWidthFull();
        content.add(new H2("Purchasing & Payables"));

        Tabs tabs = new Tabs(new Tab("Purchase orders"), new Tab("Receiving"),
                new Tab("Supplier invoices"), new Tab("Payments"));
        List<Component> pages = List.of(buildOrdersPage(), buildReceivingPage(), buildInvoicePage(), buildPaymentsPage());
        Div pageContent = new Div(pages.get(0));
        pageContent.setWidthFull();
        tabs.addSelectedChangeListener(event -> {
            pageContent.removeAll();
            pageContent.add(pages.get(tabs.getSelectedIndex()));
        });
        content.add(tabs, pageContent);
    }

    private Component buildOrdersPage() {
        VerticalLayout page = page();
        orderStore = new ComboBox<>("Store");
        orderStore.setItemLabelGenerator(Store::getName);
        orderSupplier = new ComboBox<>("Supplier");
        orderSupplier.setItemLabelGenerator(Supplier::getName);
        orderProduct = new ComboBox<>("Store product");
        orderProduct.setItemLabelGenerator(item -> item.getProduct().getName() + " (" + item.getStore().getName() + ")");
        orderQuantity = numberField("Quantity", BigDecimal.ONE);
        orderCost = numberField("Unit cost", BigDecimal.ZERO);
        pendingOrderGrid = new Grid<>();
        pendingOrderGrid.addColumn(PendingOrderLine::product).setHeader("Product");
        pendingOrderGrid.addColumn(line -> line.quantity().stripTrailingZeros().toPlainString()).setHeader("Qty");
        pendingOrderGrid.addColumn(line -> line.unitCost().stripTrailingZeros().toPlainString()).setHeader("Unit cost");
        pendingOrderGrid.addColumn(line -> line.total().stripTrailingZeros().toPlainString()).setHeader("Line total");
        pendingOrderGrid.setHeight("10em");
        Button addOrderLine = new Button("Add line", event -> run(() -> {
            StoreProduct selected = orderProduct.getValue();
            if (selected == null) throw new IllegalArgumentException("Select a product.");
            if (value(orderQuantity).signum() <= 0 || value(orderCost).signum() < 0) {
                throw new IllegalArgumentException("Enter a positive quantity and non-negative cost.");
            }
            pendingOrderLines.add(new PosService.PurchaseLineInput(selected.getId(), value(orderQuantity), value(orderCost)));
            refreshPendingOrder();
        }));
        Button createOrder = new Button("Place purchase order", event -> run(() -> {
            if (orderStore.getValue() == null || orderSupplier.getValue() == null) {
                throw new IllegalArgumentException("Select a store and supplier.");
            }
            PurchaseOrder order = posService.createPurchaseOrder(orderStore.getValue().getId(),
                    orderSupplier.getValue().getId(), pendingOrderLines);
            pendingOrderLines.clear();
            refreshPendingOrder();
            notifyUser("Purchase order " + order.getOrderNumber() + " placed.");
            refreshData();
        }));
        orderStore.addValueChangeListener(event -> {
            pendingOrderLines.clear();
            refreshPendingOrder();
            updateOrderProducts();
        });
        orderSupplier.addValueChangeListener(event -> {
            pendingOrderLines.clear();
            refreshPendingOrder();
            updateOrderProducts();
        });
        purchaseOrderGrid = new Grid<>();
        purchaseOrderGrid.addColumn(PurchaseOrder::getOrderNumber).setHeader("Order");
        purchaseOrderGrid.addColumn(order -> order.getStore().getName()).setHeader("Store");
        purchaseOrderGrid.addColumn(order -> order.getSupplier().getName()).setHeader("Supplier");
        purchaseOrderGrid.addColumn(order -> order.getTotal().stripTrailingZeros().toPlainString() + " " + order.getCurrency().getCode()).setHeader("Total");
        purchaseOrderGrid.addColumn(PurchaseOrder::getStatus).setHeader("Status");
        purchaseOrderGrid.setHeight("16em");
        page.add(new H3("Create purchase order"),
                row(orderStore, orderSupplier, orderProduct, orderQuantity, orderCost, addOrderLine),
                pendingOrderGrid, createOrder, new H3("Orders"), purchaseOrderGrid);
        return page;
    }

    private Component buildReceivingPage() {
        VerticalLayout page = page();
        receiptOrder = new ComboBox<>("Open purchase order");
        receiptOrder.setItemLabelGenerator(PurchaseOrder::getOrderNumber);
        receiptOrderLine = new ComboBox<>("Order line");
        receiptOrderLine.setItemLabelGenerator(line -> line.getStoreProduct().getProduct().getName()
                + " — remaining " + line.getOrderedQuantity().subtract(line.getReceivedQuantity()).stripTrailingZeros().toPlainString());
        receivedQuantity = numberField("Quantity received", BigDecimal.ONE);
        receiptReference = new TextField("Delivery note / receipt reference");
        Button addReceiptLine = new Button("Add received line", event -> run(() -> {
            PurchaseOrderLine line = receiptOrderLine.getValue();
            BigDecimal quantity = value(receivedQuantity);
            if (line == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("Select an order line and enter a positive received quantity.");
            }
            BigDecimal remaining = line.getOrderedQuantity().subtract(line.getReceivedQuantity())
                    .subtract(pendingReceiptLines.getOrDefault(line.getId(), BigDecimal.ZERO));
            if (quantity.compareTo(remaining) > 0) {
                throw new IllegalArgumentException("Received quantity exceeds the order line's remaining quantity.");
            }
            pendingReceiptLines.merge(line.getId(), quantity, BigDecimal::add);
            refreshPendingReceipt();
        }));
        pendingReceiptGrid = new Grid<>();
        pendingReceiptGrid.addColumn(PendingReceiptLine::product).setHeader("Product");
        pendingReceiptGrid.addColumn(line -> line.quantity().stripTrailingZeros().toPlainString()).setHeader("Receiving");
        pendingReceiptGrid.setHeight("10em");
        Button receive = new Button("Post receipt to stock", event -> run(() -> {
            if (receiptOrder.getValue() == null || receiptReference.getValue() == null || receiptReference.getValue().isBlank()) {
                throw new IllegalArgumentException("Select an order and enter a receipt reference.");
            }
            posService.receivePurchaseOrder(receiptOrder.getValue().getId(), receiptReference.getValue(), pendingReceiptLines);
            pendingReceiptLines.clear();
            refreshPendingReceipt();
            notifyUser("Receipt posted and store stock updated.");
            refreshData();
        }));
        receiptOrder.addValueChangeListener(event -> {
            pendingReceiptLines.clear();
            refreshPendingReceipt();
            updateReceiptLines();
        });
        page.add(new H3("Receive a delivery"), row(receiptOrder, receiptOrderLine, receivedQuantity, addReceiptLine),
                pendingReceiptGrid, row(receiptReference, receive));
        return page;
    }

    private Component buildInvoicePage() {
        VerticalLayout page = page();
        invoiceStore = new ComboBox<>("Store");
        invoiceStore.setItemLabelGenerator(Store::getName);
        invoiceSupplier = new ComboBox<>("Supplier");
        invoiceSupplier.setItemLabelGenerator(Supplier::getName);
        invoiceCurrency = new ComboBox<>("Invoice currency");
        invoiceCurrency.setItemLabelGenerator(Currency::getCode);
        invoiceNumber = new TextField("Supplier invoice number");
        invoiceTotal = numberField("Invoice total", BigDecimal.ZERO);
        Button registerInvoice = new Button("Register invoice", event -> run(() -> {
            SupplierInvoice invoice = posService.registerSupplierInvoice(id(invoiceStore.getValue()),
                    id(invoiceSupplier.getValue()), id(invoiceCurrency.getValue()), invoiceNumber.getValue(),
                    value(invoiceTotal), null);
            invoiceNumber.clear();
            invoiceTotal.setValue(BigDecimal.ZERO);
            notifyUser("Invoice " + invoice.getInvoiceNumber() + " registered for matching.");
            refreshData();
        }));
        invoiceStore.addValueChangeListener(event -> {
            if (event.getValue() != null) invoiceCurrency.setValue(event.getValue().getTradingCurrency());
        });

        matchInvoice = new ComboBox<>("Invoice to match");
        matchInvoice.setItemLabelGenerator(invoice -> invoice.getInvoiceNumber() + " — "
                + invoice.getMatchedTotal().stripTrailingZeros().toPlainString() + " / "
                + invoice.getTotal().stripTrailingZeros().toPlainString());
        matchReceiptLine = new ComboBox<>("Received product");
        matchReceiptLine.setItemLabelGenerator(line -> line.getGoodsReceipt().getReference() + " — "
                + line.getPurchaseOrderLine().getStoreProduct().getProduct().getName() + " ("
                + line.getQuantity().stripTrailingZeros().toPlainString() + ")");
        matchQuantity = numberField("Matched quantity", BigDecimal.ONE);
        matchUnitCost = numberField("Invoiced unit cost", BigDecimal.ZERO);
        Button addMatch = new Button("Add match line", event -> run(() -> {
            GoodsReceiptLine line = matchReceiptLine.getValue();
            if (line == null || value(matchQuantity).signum() <= 0 || value(matchUnitCost).signum() < 0) {
                throw new IllegalArgumentException("Select a received product and enter valid quantity and unit cost.");
            }
            pendingMatches.add(new PosService.InvoiceMatchInput(line.getId(), value(matchQuantity), value(matchUnitCost)));
            refreshPendingMatches();
        }));
        pendingMatchGrid = new Grid<>();
        pendingMatchGrid.addColumn(PendingMatch::product).setHeader("Received product");
        pendingMatchGrid.addColumn(line -> line.quantity().stripTrailingZeros().toPlainString()).setHeader("Qty");
        pendingMatchGrid.addColumn(line -> line.unitCost().stripTrailingZeros().toPlainString()).setHeader("Unit cost");
        pendingMatchGrid.setHeight("10em");
        Button postMatches = new Button("Match invoice to receipts", event -> run(() -> {
            SupplierInvoice invoice = matchInvoice.getValue();
            if (invoice == null) throw new IllegalArgumentException("Select a supplier invoice.");
            SupplierInvoice updated = posService.matchSupplierInvoice(invoice.getId(), pendingMatches);
            pendingMatches.clear();
            refreshPendingMatches();
            notifyUser("Invoice matching status: " + updated.getStatus() + ".");
            refreshData();
        }));
        matchInvoice.addValueChangeListener(event -> {
            pendingMatches.clear();
            refreshPendingMatches();
            updateMatchReceiptLines();
        });
        matchInvoiceGrid = new Grid<>();
        configureInvoiceGrid(matchInvoiceGrid);
        page.add(new H3("Register supplier invoice"), row(invoiceStore, invoiceSupplier, invoiceCurrency, invoiceNumber, invoiceTotal, registerInvoice),
                new H3("Match invoice lines against received goods"),
                row(matchInvoice, matchReceiptLine, matchQuantity, matchUnitCost, addMatch),
                pendingMatchGrid, postMatches, new H3("Invoice status"), matchInvoiceGrid);
        return page;
    }

    private Component buildPaymentsPage() {
        VerticalLayout page = page();
        paymentInvoice = new ComboBox<>("Matched invoice");
        paymentInvoice.setItemLabelGenerator(invoice -> invoice.getInvoiceNumber() + " — outstanding "
                + invoice.getTotal().subtract(invoice.getPaidTotal()).stripTrailingZeros().toPlainString()
                + " " + invoice.getCurrency().getCode());
        paymentAmount = numberField("Payment amount", BigDecimal.ZERO);
        paymentReference = new TextField("Payment reference");
        Button pay = new Button("Record payment", event -> run(() -> {
            if (paymentInvoice.getValue() == null) throw new IllegalArgumentException("Select an invoice.");
            posService.paySupplierInvoice(paymentInvoice.getValue().getId(), value(paymentAmount), paymentReference.getValue());
            paymentAmount.setValue(BigDecimal.ZERO);
            paymentReference.clear();
            notifyUser("Supplier payment recorded.");
            refreshData();
        }));
        paymentInvoiceGrid = new Grid<>();
        configureInvoiceGrid(paymentInvoiceGrid);
        page.add(new H3("Record partial or full invoice payment"), row(paymentInvoice, paymentAmount, paymentReference, pay),
                new H3("Invoice balances"), paymentInvoiceGrid);
        return page;
    }

    private void configureInvoiceGrid(Grid<SupplierInvoice> grid) {
        grid.addColumn(SupplierInvoice::getInvoiceNumber).setHeader("Invoice");
        grid.addColumn(invoice -> invoice.getSupplier().getName()).setHeader("Supplier");
        grid.addColumn(invoice -> invoice.getStore().getName()).setHeader("Store");
        grid.addColumn(invoice -> invoice.getTotal().stripTrailingZeros().toPlainString() + " " + invoice.getCurrency().getCode()).setHeader("Total");
        grid.addColumn(invoice -> invoice.getMatchedTotal().stripTrailingZeros().toPlainString()).setHeader("Matched");
        grid.addColumn(invoice -> invoice.getPaidTotal().stripTrailingZeros().toPlainString()).setHeader("Paid");
        grid.addColumn(SupplierInvoice::getStatus).setHeader("Status");
        grid.setHeight("16em");
    }

    private void refreshData() {
        List<Store> stores = dataManager.load(Store.class).query("select s from Store s where s.active = true order by s.name").list();
        List<Supplier> suppliers = posService.getActiveSuppliers();
        List<Currency> currencies = posService.getActiveCurrencies();
        List<PurchaseOrder> orders = dataManager.load(PurchaseOrder.class)
                .query("select o from PurchaseOrder o order by o.orderDate desc").list();
        List<PurchaseOrder> openOrders = orders.stream()
                .filter(order -> "OPEN".equals(order.getStatus()) || "PARTIALLY_RECEIVED".equals(order.getStatus()))
                .toList();
        List<SupplierInvoice> invoices = dataManager.load(SupplierInvoice.class)
                .query("select i from SupplierInvoice i order by i.invoiceDate desc").list();
        if (orderStore != null) orderStore.setItems(stores);
        if (orderSupplier != null) orderSupplier.setItems(suppliers);
        updateOrderProducts();
        if (receiptOrder != null) receiptOrder.setItems(openOrders);
        if (invoiceStore != null) invoiceStore.setItems(stores);
        if (invoiceSupplier != null) invoiceSupplier.setItems(suppliers);
        if (invoiceCurrency != null) invoiceCurrency.setItems(currencies);
        if (matchInvoice != null) matchInvoice.setItems(invoices.stream()
                .filter(invoice -> !"PAID".equals(invoice.getStatus()) && !"PARTIALLY_PAID".equals(invoice.getStatus())).toList());
        if (paymentInvoice != null) paymentInvoice.setItems(invoices.stream()
                .filter(invoice -> "MATCHED".equals(invoice.getStatus()) || "PARTIALLY_PAID".equals(invoice.getStatus())).toList());
        if (purchaseOrderGrid != null) purchaseOrderGrid.setItems(orders);
        if (matchInvoiceGrid != null) matchInvoiceGrid.setItems(invoices);
        if (paymentInvoiceGrid != null) paymentInvoiceGrid.setItems(invoices);
        updateReceiptLines();
        updateMatchReceiptLines();
    }

    private void updateOrderProducts() {
        if (orderProduct != null && orderStore != null && orderStore.getValue() != null
                && orderSupplier != null && orderSupplier.getValue() != null) {
            orderProduct.setItems(posService.getSupplierProducts(orderStore.getValue().getId(), orderSupplier.getValue().getId()));
        } else if (orderProduct != null) {
            orderProduct.clear();
        }
    }

    private void updateReceiptLines() {
        if (receiptOrderLine == null || receiptOrder == null || receiptOrder.getValue() == null) return;
        List<PurchaseOrderLine> lines = dataManager.load(PurchaseOrderLine.class)
                .query("select l from PurchaseOrderLine l where l.purchaseOrder.id = :orderId order by l.id")
                .parameter("orderId", receiptOrder.getValue().getId()).list();
        receiptOrderLine.setItems(lines.stream()
                .filter(line -> line.getReceivedQuantity().compareTo(line.getOrderedQuantity()) < 0).toList());
    }

    private void updateMatchReceiptLines() {
        if (matchReceiptLine == null || matchInvoice == null || matchInvoice.getValue() == null) return;
        SupplierInvoice invoice = matchInvoice.getValue();
        List<GoodsReceiptLine> lines = dataManager.load(GoodsReceiptLine.class)
                .query("select l from GoodsReceiptLine l where l.goodsReceipt.purchaseOrder.supplier.id = :supplierId and l.goodsReceipt.purchaseOrder.store.id = :storeId order by l.goodsReceipt.receivedDate desc")
                .parameter("supplierId", invoice.getSupplier().getId())
                .parameter("storeId", invoice.getStore().getId()).list();
        matchReceiptLine.setItems(lines);
    }

    private void refreshPendingOrder() {
        List<PendingOrderLine> rows = new ArrayList<>();
        for (PosService.PurchaseLineInput line : pendingOrderLines) {
            StoreProduct item = dataManager.load(StoreProduct.class).id(line.storeProductId()).one();
            rows.add(new PendingOrderLine(item.getProduct().getName(), line.quantity(), line.unitCost(),
                    line.quantity().multiply(line.unitCost())));
        }
        pendingOrderGrid.setItems(rows);
    }

    private void refreshPendingReceipt() {
        List<PendingReceiptLine> rows = new ArrayList<>();
        pendingReceiptLines.forEach((lineId, quantity) -> {
            PurchaseOrderLine line = dataManager.load(PurchaseOrderLine.class).id(lineId).one();
            rows.add(new PendingReceiptLine(line.getStoreProduct().getProduct().getName(), quantity));
        });
        pendingReceiptGrid.setItems(rows);
    }

    private void refreshPendingMatches() {
        List<PendingMatch> rows = new ArrayList<>();
        for (PosService.InvoiceMatchInput input : pendingMatches) {
            GoodsReceiptLine line = dataManager.load(GoodsReceiptLine.class).id(input.receiptLineId()).one();
            rows.add(new PendingMatch(line.getPurchaseOrderLine().getStoreProduct().getProduct().getName(),
                    input.quantity(), input.unitCost()));
        }
        pendingMatchGrid.setItems(rows);
    }

    private void run(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException ex) {
            notifyUser(ex.getMessage());
        }
    }

    private static BigDecimalField numberField(String label, BigDecimal defaultValue) {
        BigDecimalField field = new BigDecimalField(label);
        field.setValue(defaultValue);
        field.setWidth("12em");
        return field;
    }

    private static BigDecimal value(BigDecimalField field) {
        return field.getValue() == null ? BigDecimal.ZERO : field.getValue();
    }

    private static UUID id(Store store) {
        if (store == null) throw new IllegalArgumentException("Select a store.");
        return store.getId();
    }

    private static UUID id(Supplier supplier) {
        if (supplier == null) throw new IllegalArgumentException("Select a supplier.");
        return supplier.getId();
    }

    private static UUID id(Currency currency) {
        if (currency == null) throw new IllegalArgumentException("Select a currency.");
        return currency.getId();
    }

    private static VerticalLayout page() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setWidthFull();
        return layout;
    }

    private static HorizontalLayout row(Component... components) {
        HorizontalLayout layout = new HorizontalLayout(components);
        layout.setWidthFull();
        layout.setAlignItems(HorizontalLayout.Alignment.END);
        return layout;
    }

    private static void notifyUser(String message) {
        Notification.show(message == null ? "Operation failed." : message, 4000, Notification.Position.BOTTOM_START);
    }

    private record PendingOrderLine(String product, BigDecimal quantity, BigDecimal unitCost, BigDecimal total) {}
    private record PendingReceiptLine(String product, BigDecimal quantity) {}
    private record PendingMatch(String product, BigDecimal quantity, BigDecimal unitCost) {}
}
