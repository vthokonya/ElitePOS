package zw.co.tayanasoft.elitepos.view.pos;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import zw.co.tayanasoft.elitepos.entity.CheckoutUser;
import zw.co.tayanasoft.elitepos.entity.Currency;
import zw.co.tayanasoft.elitepos.entity.PosShift;
import zw.co.tayanasoft.elitepos.entity.Sale;
import zw.co.tayanasoft.elitepos.entity.Store;
import zw.co.tayanasoft.elitepos.view.main.MainView;
import zw.co.tayanasoft.elitepos.service.PosService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@Route(value = "pos", layout = MainView.class)
@ViewController(id = "PosView")
@ViewDescriptor(path = "pos-view.xml")
public class PosView extends StandardView {
    @ViewComponent
    private Div content;
    @Autowired
    private PosService posService;

    private final Map<UUID, CartRow> cart = new LinkedHashMap<>();
    private CheckoutUser signedInUser;
    private PosShift cashierShift;
    private Store currentStore;
    private ComboBox<Store> storeField;
    private ComboBox<Currency> currencyField;
    private ComboBox<String> paymentMethodField;
    private TextField barcodeField;
    private BigDecimalField tenderedField;
    private Grid<CartRow> cartGrid;
    private Grid<Sale> salesGrid;
    private Grid<PosShift> shiftsGrid;
    private Span totalLabel;
    private Span rateLabel;
    private Span shiftLabel;
    private CartRow selectedCartRow;
    private Sale selectedSale;
    private PosShift selectedShift;

    @Subscribe
    public void onReady(ReadyEvent event) {
        buildLoginScreen();
    }

    private void buildLoginScreen() {
        content.removeAll();
        signedInUser = null;
        cashierShift = null;
        currentStore = null;
        cart.clear();
        TextField username = new TextField("Checkout username");
        PasswordField password = new PasswordField("Password");
        Button login = new Button("Sign in", event -> {
            try {
                signedInUser = posService.authenticateCheckoutUser(username.getValue(), password.getValue());
                if (!List.of("CASHIER", "SUPERVISOR").contains(signedInUser.getCheckoutRole())) {
                    signedInUser = null;
                    throw new IllegalArgumentException("This account cannot sign in to checkout.");
                }
                buildCheckoutScreen();
            } catch (IllegalArgumentException ex) {
                notifyUser(ex.getMessage());
            }
        });
        password.addKeyPressListener(Key.ENTER, event -> login.click());
        VerticalLayout form = new VerticalLayout(new H2("Checkout sign in"), username, password, login);
        form.setMaxWidth("28em");
        form.setWidthFull();
        content.removeAll();
        content.add(form);
        username.focus();
    }

    private void buildCheckoutScreen() {
        content.removeAll();
        cart.clear();
        if ("SUPERVISOR".equals(signedInUser.getCheckoutRole())) {
            buildSupervisorScreen();
        } else {
            buildCashierScreen();
        }
    }

    private void buildCashierScreen() {
        HorizontalLayout header = new HorizontalLayout(new H2("Point of Sale"));
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        Span identity = new Span(signedInUser.getDisplayName() + " (Cashier)");
        Button signOut = new Button("Sign out", event -> buildLoginScreen());
        header.add(identity, signOut);

        storeField = new ComboBox<>("Store");
        storeField.setItems(posService.getActiveStores());
        storeField.setItemLabelGenerator(Store::getName);
        storeField.setWidth("16em");
        shiftLabel = new Span("Select your store to check for an assigned open shift.");
        storeField.addValueChangeListener(event -> attachCashierShift(event.getValue()));

        currencyField = new ComboBox<>("Payment currency");
        currencyField.setItems(posService.getActiveCurrencies());
        currencyField.setItemLabelGenerator(Currency::getCode);
        currencyField.setWidth("12em");
        currencyField.addValueChangeListener(event -> updateRateLabel());
        paymentMethodField = new ComboBox<>("Tender type");
        paymentMethodField.setItems("CASH", "CARD", "OTHER");
        paymentMethodField.setValue("CASH");
        paymentMethodField.setWidth("10em");

        barcodeField = new TextField("Scan barcode");
        barcodeField.setPlaceholder("Scan or type a barcode");
        barcodeField.setClearButtonVisible(true);
        barcodeField.setWidth("22em");
        barcodeField.addKeyPressListener(Key.ENTER, event -> scanBarcode());
        Button scanButton = new Button("Add item", event -> scanBarcode());
        rateLabel = new Span("Select an open shift and payment currency.");
        HorizontalLayout selectors = new HorizontalLayout(storeField, shiftLabel, currencyField,
                paymentMethodField, barcodeField, scanButton);
        selectors.setAlignItems(HorizontalLayout.Alignment.END);

        cartGrid = new Grid<>();
        cartGrid.setWidthFull();
        cartGrid.addColumn(CartRow::name).setHeader("Product").setAutoWidth(true);
        cartGrid.addColumn(CartRow::barcode).setHeader("Barcode");
        cartGrid.addColumn(CartRow::quantity).setHeader("Qty");
        cartGrid.addColumn(row -> format(row.unitPrice())).setHeader("Unit price");
        cartGrid.addColumn(row -> row.taxRate().stripTrailingZeros().toPlainString() + "%").setHeader("Tax");
        cartGrid.addColumn(row -> format(row.lineTotal())).setHeader("Line total");
        cartGrid.asSingleSelect().addValueChangeListener(event -> selectedCartRow = event.getValue());

        totalLabel = new Span("Total: 0.0000");
        totalLabel.getStyle().set("font-size", "1.4em").set("font-weight", "700");
        tenderedField = new BigDecimalField("Amount tendered");
        tenderedField.setWidth("14em");
        Button checkoutButton = new Button("Tender payment", event -> completeSale());
        Button cancelProductButton = new Button("Cancel selected product", event ->
                authorizeProductCancellation());
        HorizontalLayout checkout = new HorizontalLayout(totalLabel, rateLabel, tenderedField,
                checkoutButton, cancelProductButton);
        checkout.setAlignItems(HorizontalLayout.Alignment.END);

        salesGrid = new Grid<>();
        salesGrid.addColumn(Sale::getSaleNumber).setHeader("Transaction");
        salesGrid.addColumn(Sale::getSoldAt).setHeader("Time");
        salesGrid.addColumn(sale -> format(sale.getTotal()) + " " + sale.getStore().getTradingCurrency().getCode()).setHeader("Total");
        salesGrid.addColumn(Sale::getPaymentMethod).setHeader("Tender");
        salesGrid.addColumn(Sale::getStatus).setHeader("Status");
        salesGrid.setHeight("12em");
        salesGrid.asSingleSelect().addValueChangeListener(event -> selectedSale = event.getValue());
        Button cancelSaleButton = new Button("Cancel selected transaction", event -> authorizeTransactionCancellation());
        content.add(header, selectors, cartGrid, checkout,
                new H3("Current shift transactions"), salesGrid, cancelSaleButton);

        List<Store> stores = posService.getActiveStores();
        if (!stores.isEmpty()) {
            storeField.setValue(stores.get(0));
        }
        refreshCart();
    }

    private void buildSupervisorScreen() {
        HorizontalLayout header = new HorizontalLayout(new H2("Checkout Supervisor"));
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.add(new Span(signedInUser.getDisplayName() + " (Supervisor)"),
                new Button("Sign out", event -> buildLoginScreen()));

        ComboBox<Store> storeSelector = new ComboBox<>("Store");
        storeSelector.setItems(posService.getActiveStores());
        storeSelector.setItemLabelGenerator(Store::getName);
        ComboBox<CheckoutUser> cashierSelector = new ComboBox<>("Cashier");
        cashierSelector.setItems(posService.getActiveCashiers());
        cashierSelector.setItemLabelGenerator(CheckoutUser::getDisplayName);
        BigDecimalField openingFloat = numberField("Opening float", BigDecimal.ZERO);
        Button openShift = new Button("Open shift", event -> {
            if (storeSelector.getValue() == null || cashierSelector.getValue() == null) {
                notifyUser("Select a store and cashier.");
                return;
            }
            requestPassword("Authorize shift opening", password -> run(() -> {
                PosShift shift = posService.openShift(signedInUser.getId(), password,
                        storeSelector.getValue().getId(), cashierSelector.getValue().getId(), value(openingFloat));
                notifyUser("Shift opened for " + shift.getCashier().getDisplayName() + ".");
                refreshSupervisorShifts(storeSelector.getValue());
            }));
        });

        shiftsGrid = new Grid<>();
        shiftsGrid.addColumn(shift -> shift.getCashier().getDisplayName()).setHeader("Cashier");
        shiftsGrid.addColumn(shift -> shift.getStore().getName()).setHeader("Store");
        shiftsGrid.addColumn(PosShift::getOpenedAt).setHeader("Opened");
        shiftsGrid.addColumn(shift -> format(shift.getOpeningFloat())).setHeader("Opening float");
        shiftsGrid.setHeight("13em");
        shiftsGrid.asSingleSelect().addValueChangeListener(event -> selectedShift = event.getValue());

        BigDecimalField countedCash = numberField("Counted cash", BigDecimal.ZERO);
        Button closeShift = new Button("Close selected shift", event -> {
            if (selectedShift == null) {
                notifyUser("Select an open shift to close.");
                return;
            }
            requestPassword("Authorize shift close", password -> run(() -> {
                PosShift closed = posService.closeShift(signedInUser.getId(), password,
                        selectedShift.getId(), value(countedCash));
                notifyUser("Shift closed. Expected " + format(closed.getExpectedCash())
                        + ", variance " + format(closed.getCashVariance()) + ".");
                refreshSupervisorShifts(storeSelector.getValue());
            }));
        });
        Button closeDay = new Button("Close trading day", event -> {
            if (storeSelector.getValue() == null) {
                notifyUser("Select a store.");
                return;
            }
            requestPassword("Authorize end-of-day close", password -> run(() -> {
                var close = posService.closeDay(signedInUser.getId(), password, storeSelector.getValue().getId());
                notifyUser("Day closed. Cash sales " + format(close.getCashSales())
                        + ", non-cash sales " + format(close.getNonCashSales()) + ".");
                refreshSupervisorShifts(storeSelector.getValue());
            }));
        });
        storeSelector.addValueChangeListener(event -> refreshSupervisorShifts(event.getValue()));
        HorizontalLayout openForm = row(storeSelector, cashierSelector, openingFloat, openShift);
        HorizontalLayout closeForm = row(countedCash, closeShift, closeDay);
        content.add(header, new H3("Open cashier shift"), openForm, new H3("Open shifts"), shiftsGrid,
                closeForm, new Span("All shifts at a store must be closed before its trading day can be closed."));
        List<Store> stores = posService.getActiveStores();
        if (!stores.isEmpty()) storeSelector.setValue(stores.get(0));
    }

    private void attachCashierShift(Store store) {
        cashierShift = null;
        currentStore = store;
        cart.clear();
        if (store == null) {
            shiftLabel.setText("Select your store.");
            salesGrid.setItems(List.of());
            refreshCart();
            return;
        }
        cashierShift = posService.getOpenShiftForCashier(store.getId(), signedInUser.getId());
        if (cashierShift == null) {
            shiftLabel.setText("No open shift assigned. Ask a supervisor to open one.");
            notifyUser("A supervisor must open and assign your shift before checkout.");
            salesGrid.setItems(List.of());
            refreshCart();
            return;
        }
        shiftLabel.setText("Shift opened " + cashierShift.getOpenedAt());
        currencyField.setValue(store.getTradingCurrency());
        refreshShiftSales();
        barcodeField.focus();
        refreshCart();
    }

    private void scanBarcode() {
        if (cashierShift == null || currentStore == null) {
            notifyUser("You need an open shift before scanning products.");
            return;
        }
        try {
            PosService.ProductLookup product = posService.scanProduct(currentStore.getId(), barcodeField.getValue());
            CartRow row = cart.get(product.storeProductId());
            BigDecimal nextQuantity = row == null ? BigDecimal.ONE : row.quantity().add(BigDecimal.ONE);
            if (nextQuantity.compareTo(product.availableQuantity()) > 0) {
                notifyUser("Only " + product.availableQuantity().stripTrailingZeros().toPlainString()
                        + " " + product.name() + " available.");
                return;
            }
            cart.put(product.storeProductId(), new CartRow(product.storeProductId(), product.name(),
                    product.barcode(), nextQuantity, product.sellPrice(), product.taxRate()));
            refreshCart();
            barcodeField.clear();
            barcodeField.focus();
        } catch (IllegalArgumentException ex) {
            notifyUser(ex.getMessage());
            barcodeField.focus();
        }
    }

    private void completeSale() {
        if (cashierShift == null || currentStore == null || currencyField.getValue() == null
                || paymentMethodField.getValue() == null) {
            notifyUser("Select an active shift, payment currency, and tender type.");
            return;
        }
        try {
            List<PosService.CheckoutLine> lines = new ArrayList<>();
            cart.values().forEach(row -> lines.add(new PosService.CheckoutLine(row.storeProductId(), row.quantity())));
            Sale sale = posService.recordSale(currentStore.getId(), currencyField.getValue().getId(),
                    tenderedField.getValue(), paymentMethodField.getValue(), cashierShift.getId(),
                    signedInUser.getId(), lines);
            notifyUser("Transaction " + sale.getSaleNumber() + " completed. Change: "
                    + format(sale.getChangeAmount()) + " " + sale.getPaymentCurrency().getCode());
            cart.clear();
            tenderedField.clear();
            selectedCartRow = null;
            refreshCart();
            refreshShiftSales();
            barcodeField.focus();
        } catch (IllegalArgumentException ex) {
            notifyUser(ex.getMessage());
        }
    }

    private void authorizeProductCancellation() {
        if (selectedCartRow == null || cashierShift == null) {
            notifyUser("Select a product from the current cart.");
            return;
        }
        CartRow row = selectedCartRow;
        requestSupervisorAuthorization("Authorize product cancellation", reason ->
                (username, password) -> run(() -> {
                    posService.authorizeProductCancellation(signedInUser.getId(), cashierShift.getId(),
                            row.storeProductId(), row.quantity(), reason, username, password);
                    cart.remove(row.storeProductId());
                    selectedCartRow = null;
                    refreshCart();
                    notifyUser("Product cancellation authorized and recorded.");
                }));
    }

    private void authorizeTransactionCancellation() {
        if (selectedSale == null || cashierShift == null) {
            notifyUser("Select a transaction from the current shift.");
            return;
        }
        Sale sale = selectedSale;
        requestSupervisorAuthorization("Authorize transaction cancellation", reason ->
                (username, password) -> run(() -> {
                    posService.cancelTransaction(signedInUser.getId(), sale.getId(), reason, username, password);
                    notifyUser("Transaction cancelled and inventory restored.");
                    selectedSale = null;
                    refreshShiftSales();
                }));
    }

    private void requestSupervisorAuthorization(String title, Function<String, SupervisorAuthorization> afterReason) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        TextField supervisorUsername = new TextField("Supervisor username");
        PasswordField supervisorPassword = new PasswordField("Supervisor password");
        TextField reason = new TextField("Reason");
        reason.setWidthFull();
        Button authorize = new Button("Authorize", event -> {
            if (reason.getValue() == null || reason.getValue().isBlank()) {
                notifyUser("Enter a cancellation reason.");
                return;
            }
            dialog.close();
            afterReason.apply(reason.getValue()).authorize(supervisorUsername.getValue(), supervisorPassword.getValue());
        });
        Button cancel = new Button("Back", event -> dialog.close());
        dialog.add(new VerticalLayout(supervisorUsername, supervisorPassword, reason));
        dialog.getFooter().add(cancel, authorize);
        dialog.open();
        supervisorUsername.focus();
    }

    private void requestPassword(String title, Consumer<String> action) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        PasswordField password = new PasswordField("Confirm supervisor password");
        Button authorize = new Button("Confirm", event -> {
            String value = password.getValue();
            dialog.close();
            action.accept(value);
        });
        dialog.add(password);
        dialog.getFooter().add(new Button("Cancel", event -> dialog.close()), authorize);
        dialog.open();
        password.focus();
    }

    private void refreshSupervisorShifts(Store store) {
        if (shiftsGrid != null) {
            shiftsGrid.setItems(store == null ? List.of() : posService.getOpenShifts(store.getId()));
            selectedShift = null;
        }
    }

    private void refreshShiftSales() {
        if (salesGrid != null) {
            salesGrid.setItems(cashierShift == null ? List.of() : posService.getShiftSales(cashierShift.getId()));
            selectedSale = null;
        }
    }

    private void updateRateLabel() {
        if (currentStore == null || currencyField == null || currencyField.getValue() == null) {
            if (rateLabel != null) rateLabel.setText("Select an open shift and payment currency.");
            return;
        }
        try {
            BigDecimal rate = posService.getCurrentPaymentRate(currentStore.getId(), currencyField.getValue().getId());
            rateLabel.setText("Rate: 1 " + currentStore.getTradingCurrency().getCode()
                    + " = " + rate.stripTrailingZeros().toPlainString() + " " + currencyField.getValue().getCode());
            totalLabel.setText("Total: " + format(cartTotal()) + " " + currentStore.getTradingCurrency().getCode()
                    + " | Due: " + format(cartTotal().multiply(rate)) + " " + currencyField.getValue().getCode());
        } catch (IllegalArgumentException ex) {
            rateLabel.setText(ex.getMessage());
            totalLabel.setText("Total: " + format(cartTotal()) + " "
                    + currentStore.getTradingCurrency().getCode() + " | Exchange rate unavailable");
        }
    }

    private void refreshCart() {
        if (cartGrid == null) return;
        cartGrid.setItems(cart.values());
        totalLabel.setText("Total: " + format(cartTotal()) + " "
                + (currentStore == null ? "" : currentStore.getTradingCurrency().getCode()));
        updateRateLabel();
    }

    private BigDecimal cartTotal() {
        BigDecimal subtotal = cart.values().stream()
                .map(row -> row.unitPrice().multiply(row.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = cart.values().stream()
                .map(row -> row.unitPrice().multiply(row.quantity()).multiply(row.taxRate()).movePointLeft(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return subtotal.add(tax).setScale(4, RoundingMode.HALF_UP);
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

    private static HorizontalLayout row(Component... components) {
        HorizontalLayout layout = new HorizontalLayout(components);
        layout.setWidthFull();
        layout.setAlignItems(HorizontalLayout.Alignment.END);
        return layout;
    }

    private static String format(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static void notifyUser(String message) {
        Notification.show(message == null ? "Operation failed." : message, 4000, Notification.Position.BOTTOM_START);
    }

    private record CartRow(UUID storeProductId, String name, String barcode, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal taxRate) {
        BigDecimal lineTotal() {
            BigDecimal subtotal = unitPrice.multiply(quantity);
            return subtotal.add(subtotal.multiply(taxRate).movePointLeft(2)).setScale(4, RoundingMode.HALF_UP);
        }
    }

    @FunctionalInterface
    private interface SupervisorAuthorization {
        void authorize(String username, String password);
    }
}
