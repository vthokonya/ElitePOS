package zw.co.tayanasoft.elitepos.view.inventory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.PasswordField;
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
import zw.co.tayanasoft.elitepos.entity.CheckoutUser;
import zw.co.tayanasoft.elitepos.entity.ExchangeRate;
import zw.co.tayanasoft.elitepos.entity.Product;
import zw.co.tayanasoft.elitepos.entity.Store;
import zw.co.tayanasoft.elitepos.entity.StoreProduct;
import zw.co.tayanasoft.elitepos.entity.Supplier;
import zw.co.tayanasoft.elitepos.entity.SupplierProduct;
import zw.co.tayanasoft.elitepos.service.PosService;
import zw.co.tayanasoft.elitepos.view.main.MainView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Route(value = "inventory", layout = MainView.class)
@ViewController(id = "CatalogView")
@ViewDescriptor(path = "catalog-view.xml")
public class CatalogView extends StandardView {
    @ViewComponent
    private Div content;
    @Autowired
    private PosService posService;
    @Autowired
    private DataManager dataManager;

    private ComboBox<Currency> storeCurrency;
    private ComboBox<Store> addStore;
    private ComboBox<Product> existingProduct;
    private ComboBox<Currency> rateTarget;
    private ComboBox<Currency> rateFrom;
    private ComboBox<Currency> rateTo;
    private TextField productName;
    private TextField productBarcode;
    private TextField productSku;
    private BigDecimalField productTax;
    private Grid<Product> productGrid;
    private TextField editProductName;
    private TextField editProductBarcode;
    private TextField editProductSku;
    private BigDecimalField editProductTax;
    private Product selectedProduct;
    private Grid<Supplier> supplierGrid;
    private TextField supplierName;
    private TextField supplierPhone;
    private TextField supplierEmail;
    private TextField supplierAddress;
    private Supplier selectedSupplier;
    private ComboBox<Supplier> linkSupplier;
    private ComboBox<Product> linkProduct;
    private Grid<SupplierProduct> supplierProductGrid;
    private Grid<StoreProduct> stockGrid;
    private BigDecimalField editPrice;
    private BigDecimalField editReorder;
    private BigDecimalField stockAdjustment;
    private TextField adjustmentReason;
    private StoreProduct selectedStoreProduct;
    private Grid<Store> storeGrid;
    private Grid<Currency> currencyGrid;
    private Grid<ExchangeRate> rateGrid;
    private TextField checkoutUsername;
    private TextField checkoutDisplayName;
    private PasswordField checkoutPassword;
    private ComboBox<String> checkoutRole;
    private Grid<CheckoutUser> checkoutUserGrid;
    private CheckoutUser selectedCheckoutUser;

    @Subscribe
    public void onReady(ReadyEvent event) {
        buildScreen();
        refreshData();
    }

    private void buildScreen() {
        content.removeAll();
        content.setWidthFull();
        content.add(new H2("Inventory & Business Setup"));

        Tab setupTab = new Tab("Stores & currencies");
        Tab productTab = new Tab("Products & stock");
        Tab supplierTab = new Tab("Suppliers");
        Tab ratesTab = new Tab("Exchange rates");
        Tab checkoutUsersTab = new Tab("Checkout users");
        Tabs tabs = new Tabs(setupTab, productTab, supplierTab, ratesTab, checkoutUsersTab);
        Div pages = new Div();
        pages.setWidthFull();
        List<Component> pageContents = List.of(buildSetupPage(), buildProductPage(), buildSupplierPage(),
                buildRatesPage(), buildCheckoutUsersPage());
        pages.add(pageContents.get(0));
        tabs.addSelectedChangeListener(event -> {
            pages.removeAll();
            pages.add(pageContents.get(tabs.getSelectedIndex()));
        });
        content.add(tabs, pages);
    }

    private Component buildSetupPage() {
        VerticalLayout page = page();
        TextField currencyCode = new TextField("Code (ISO 3-letter)");
        TextField currencyName = new TextField("Currency name");
        Button addCurrency = new Button("Add currency", event -> run(() -> {
            posService.createCurrency(currencyCode.getValue(), currencyName.getValue());
            currencyCode.clear();
            currencyName.clear();
            refreshData();
        }));
        HorizontalLayout currencyForm = row(currencyCode, currencyName, addCurrency);
        currencyGrid = new Grid<>();
        currencyGrid.addColumn(Currency::getCode).setHeader("Code");
        currencyGrid.addColumn(Currency::getName).setHeader("Currency");
        currencyGrid.addColumn(currency -> Boolean.TRUE.equals(currency.getActive()) ? "Active" : "Inactive").setHeader("Status");
        currencyGrid.setHeight("12em");

        TextField storeName = new TextField("Store name");
        storeCurrency = new ComboBox<>("Trading currency");
        storeCurrency.setItemLabelGenerator(Currency::getCode);
        Button addStoreButton = new Button("Add store", event -> run(() -> {
            posService.createStore(storeName.getValue(), id(storeCurrency.getValue()));
            storeName.clear();
            refreshData();
        }));
        HorizontalLayout storeForm = row(storeName, storeCurrency, addStoreButton);
        storeGrid = new Grid<>();
        storeGrid.addColumn(Store::getName).setHeader("Store");
        storeGrid.addColumn(store -> store.getTradingCurrency().getCode()).setHeader("Trading currency");
        storeGrid.addColumn(store -> Boolean.TRUE.equals(store.getActive()) ? "Active" : "Inactive").setHeader("Status");
        storeGrid.setHeight("12em");

        page.add(new com.vaadin.flow.component.html.H3("Currencies"), currencyForm, currencyGrid,
                new com.vaadin.flow.component.html.H3("Stores"), storeForm, storeGrid);
        return page;
    }

    private Component buildProductPage() {
        VerticalLayout page = page();
        productName = new TextField("Product name");
        productBarcode = new TextField("Barcode");
        productSku = new TextField("SKU");
        productTax = numberField("Tax rate (%)", BigDecimal.ZERO);
        Button createProduct = new Button("Create product", event -> run(() -> {
            posService.createProduct(productName.getValue(), productBarcode.getValue(), productSku.getValue(),
                    value(productTax));
            productName.clear();
            productBarcode.clear();
            productSku.clear();
            refreshData();
        }));
        page.add(new com.vaadin.flow.component.html.H3("Add global product"),
                row(productName, productBarcode, productSku, productTax, createProduct));

        productGrid = new Grid<>();
        productGrid.addColumn(Product::getName).setHeader("Product");
        productGrid.addColumn(Product::getBarcode).setHeader("Barcode");
        productGrid.addColumn(Product::getSku).setHeader("SKU");
        productGrid.addColumn(product -> product.getTaxRate().stripTrailingZeros().toPlainString() + "%").setHeader("Tax");
        productGrid.setHeight("13em");
        editProductName = new TextField("Name");
        editProductBarcode = new TextField("Barcode");
        editProductSku = new TextField("SKU");
        editProductTax = numberField("Tax rate (%)", BigDecimal.ZERO);
        Button saveProduct = new Button("Save product changes", event -> run(() -> {
            if (selectedProduct == null) {
                throw new IllegalArgumentException("Select a product to edit.");
            }
            posService.updateProduct(selectedProduct.getId(), editProductName.getValue(), editProductBarcode.getValue(),
                    editProductSku.getValue(), value(editProductTax));
            refreshData();
        }));
        productGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedProduct = event.getValue();
            if (selectedProduct != null) {
                editProductName.setValue(selectedProduct.getName());
                editProductBarcode.setValue(selectedProduct.getBarcode());
                editProductSku.setValue(selectedProduct.getSku() == null ? "" : selectedProduct.getSku());
                editProductTax.setValue(selectedProduct.getTaxRate());
            }
        });
        page.add(new com.vaadin.flow.component.html.H3("Edit product"), productGrid,
                row(editProductName, editProductBarcode, editProductSku, editProductTax, saveProduct));

        existingProduct = new ComboBox<>("Product");
        existingProduct.setItemLabelGenerator(Product::getName);
        addStore = new ComboBox<>("Store");
        addStore.setItemLabelGenerator(Store::getName);
        BigDecimalField newStorePrice = numberField("Store selling price", BigDecimal.ZERO);
        BigDecimalField newStoreStock = numberField("Initial stock", BigDecimal.ZERO);
        BigDecimalField newStoreReorder = numberField("Reorder level", BigDecimal.ZERO);
        Button addProductToStore = new Button("Add product to store", event -> run(() -> {
            posService.addProductToStore(id(addStore.getValue()), id(existingProduct.getValue()), value(newStorePrice),
                    value(newStoreStock), value(newStoreReorder));
            refreshData();
        }));
        page.add(new com.vaadin.flow.component.html.H3("List product at a store"),
                row(existingProduct, addStore, newStorePrice, newStoreStock, newStoreReorder, addProductToStore));

        stockGrid = new Grid<>();
        stockGrid.addColumn(item -> item.getStore().getName()).setHeader("Store");
        stockGrid.addColumn(item -> item.getProduct().getName()).setHeader("Product");
        stockGrid.addColumn(item -> item.getQuantity().stripTrailingZeros().toPlainString()).setHeader("On hand");
        stockGrid.addColumn(item -> item.getSellPrice().stripTrailingZeros().toPlainString()).setHeader("Price");
        stockGrid.addColumn(item -> item.getReorderLevel().stripTrailingZeros().toPlainString()).setHeader("Reorder at");
        stockGrid.setHeight("13em");
        editPrice = numberField("Selling price", BigDecimal.ZERO);
        editReorder = numberField("Reorder level", BigDecimal.ZERO);
        stockAdjustment = numberField("Stock adjustment (+/-)", BigDecimal.ZERO);
        adjustmentReason = new TextField("Adjustment reason");
        Button updateInventory = new Button("Save price / stock", event -> run(() -> {
            if (selectedStoreProduct == null) {
                throw new IllegalArgumentException("Select store inventory to update.");
            }
            posService.updateStoreProduct(selectedStoreProduct.getId(), value(editPrice), value(editReorder),
                    value(stockAdjustment), adjustmentReason.getValue());
            stockAdjustment.setValue(BigDecimal.ZERO);
            adjustmentReason.clear();
            refreshData();
        }));
        stockGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedStoreProduct = event.getValue();
            if (selectedStoreProduct != null) {
                editPrice.setValue(selectedStoreProduct.getSellPrice());
                editReorder.setValue(selectedStoreProduct.getReorderLevel());
            }
        });
        page.add(new com.vaadin.flow.component.html.H3("Store stock & prices"), stockGrid,
                row(editPrice, editReorder, stockAdjustment, adjustmentReason, updateInventory));
        return page;
    }

    private Component buildSupplierPage() {
        VerticalLayout page = page();
        supplierName = new TextField("Supplier name");
        supplierPhone = new TextField("Phone");
        supplierEmail = new TextField("Email");
        supplierAddress = new TextField("Address");
        Button addSupplier = new Button("Add supplier", event -> run(() -> {
            if (selectedSupplier == null) {
                posService.createSupplier(supplierName.getValue(), supplierPhone.getValue(),
                        supplierEmail.getValue(), supplierAddress.getValue());
            } else {
                posService.updateSupplier(selectedSupplier.getId(), supplierName.getValue(), supplierPhone.getValue(),
                        supplierEmail.getValue(), supplierAddress.getValue());
                selectedSupplier = null;
                event.getSource().setText("Add supplier");
            }
            supplierName.clear();
            supplierPhone.clear();
            supplierEmail.clear();
            supplierAddress.clear();
            refreshData();
        }));
        supplierGrid = new Grid<>();
        supplierGrid.addColumn(Supplier::getName).setHeader("Supplier");
        supplierGrid.addColumn(Supplier::getPhone).setHeader("Phone");
        supplierGrid.addColumn(Supplier::getEmail).setHeader("Email");
        supplierGrid.setHeight("15em");
        supplierGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedSupplier = event.getValue();
            if (selectedSupplier != null) {
                supplierName.setValue(selectedSupplier.getName());
                supplierPhone.setValue(selectedSupplier.getPhone() == null ? "" : selectedSupplier.getPhone());
                supplierEmail.setValue(selectedSupplier.getEmail() == null ? "" : selectedSupplier.getEmail());
                supplierAddress.setValue(selectedSupplier.getAddress() == null ? "" : selectedSupplier.getAddress());
                addSupplier.setText("Save supplier changes");
            }
        });
        linkSupplier = new ComboBox<>("Supplier");
        linkSupplier.setItemLabelGenerator(Supplier::getName);
        linkProduct = new ComboBox<>("Product");
        linkProduct.setItemLabelGenerator(Product::getName);
        TextField supplierSku = new TextField("Supplier product code");
        BigDecimalField supplierUnitCost = numberField("Last unit cost", BigDecimal.ZERO);
        Button linkProductButton = new Button("Link product to supplier", event -> run(() -> {
            posService.addSupplierProduct(id(linkSupplier.getValue()), id(linkProduct.getValue()),
                    supplierSku.getValue(), value(supplierUnitCost));
            refreshData();
        }));
        supplierProductGrid = new Grid<>();
        supplierProductGrid.addColumn(item -> item.getSupplier().getName()).setHeader("Supplier");
        supplierProductGrid.addColumn(item -> item.getProduct().getName()).setHeader("Product");
        supplierProductGrid.addColumn(SupplierProduct::getSupplierSku).setHeader("Supplier code");
        supplierProductGrid.addColumn(item -> item.getLastUnitCost().stripTrailingZeros().toPlainString()).setHeader("Last unit cost");
        supplierProductGrid.setHeight("13em");
        page.add(new com.vaadin.flow.component.html.H3("Add or update supplier"),
                row(supplierName, supplierPhone, supplierEmail, supplierAddress, addSupplier), supplierGrid,
                new com.vaadin.flow.component.html.H3("Products supplied"), row(linkSupplier, linkProduct,
                        supplierSku, supplierUnitCost, linkProductButton), supplierProductGrid);
        return page;
    }

    private Component buildRatesPage() {
        VerticalLayout page = page();
        rateFrom = new ComboBox<>("From currency");
        rateFrom.setItemLabelGenerator(Currency::getCode);
        rateTo = new ComboBox<>("To currency");
        rateTo.setItemLabelGenerator(Currency::getCode);
        BigDecimalField rateValue = numberField("Target currency units per 1 source unit", BigDecimal.ONE);
        Button saveRate = new Button("Save exchange rate", event -> run(() -> {
            posService.saveExchangeRate(id(rateFrom.getValue()), id(rateTo.getValue()), value(rateValue), LocalDateTime.now());
            refreshData();
        }));
        rateGrid = new Grid<>();
        rateGrid.addColumn(rate -> rate.getFromCurrency().getCode()).setHeader("From");
        rateGrid.addColumn(rate -> rate.getToCurrency().getCode()).setHeader("To");
        rateGrid.addColumn(rate -> rate.getRate().stripTrailingZeros().toPlainString()).setHeader("Rate");
        rateGrid.addColumn(ExchangeRate::getEffectiveAt).setHeader("Effective at");
        rateGrid.setHeight("18em");
        page.add(new com.vaadin.flow.component.html.H3("Manual exchange rates"),
                row(rateFrom, rateTo, rateValue, saveRate), rateGrid);
        return page;
    }

    private Component buildCheckoutUsersPage() {
        VerticalLayout page = page();
        checkoutUsername = new TextField("Username");
        checkoutDisplayName = new TextField("Display name");
        checkoutPassword = checkoutPinField("PIN (4 digits)");
        checkoutRole = new ComboBox<>("Checkout role");
        checkoutRole.setItems("CASHIER", "SUPERVISOR");
        checkoutRole.setItemLabelGenerator(role -> "CASHIER".equals(role) ? "Cashier" : "Supervisor");
        checkoutRole.setValue("CASHIER");
        Button createUser = new Button("Create checkout user", event -> run(() -> {
            posService.createCheckoutUser(checkoutUsername.getValue(), checkoutDisplayName.getValue(),
                    checkoutPassword.getValue(), checkoutRole.getValue());
            checkoutUsername.clear();
            checkoutDisplayName.clear();
            checkoutPassword.clear();
            checkoutRole.setValue("CASHIER");
            refreshData();
        }));
        checkoutUserGrid = new Grid<>();
        checkoutUserGrid.addColumn(CheckoutUser::getUsername).setHeader("Username");
        checkoutUserGrid.addColumn(CheckoutUser::getDisplayName).setHeader("Name");
        checkoutUserGrid.addColumn(user -> "CASHIER".equals(user.getCheckoutRole()) ? "Cashier" : "Supervisor").setHeader("Role");
        checkoutUserGrid.addColumn(user -> Boolean.TRUE.equals(user.getActive()) ? "Active" : "Inactive").setHeader("Status");
        checkoutUserGrid.setHeight("16em");
        checkoutUserGrid.asSingleSelect().addValueChangeListener(event -> selectedCheckoutUser = event.getValue());
        PasswordField resetPassword = checkoutPinField("New PIN (4 digits)");
        Button resetUserPassword = new Button("Reset selected password", event -> run(() -> {
            if (selectedCheckoutUser == null) throw new IllegalArgumentException("Select a checkout user.");
            posService.resetCheckoutPassword(selectedCheckoutUser.getId(), resetPassword.getValue());
            resetPassword.clear();
            notifyUser("Checkout password reset.");
        }));
        Button toggleUser = new Button("Deactivate / reactivate selected", event -> run(() -> {
            if (selectedCheckoutUser == null) throw new IllegalArgumentException("Select a checkout user.");
            selectedCheckoutUser = posService.setCheckoutUserActive(selectedCheckoutUser.getId(),
                    !Boolean.TRUE.equals(selectedCheckoutUser.getActive()));
            refreshData();
        }));
        page.add(new com.vaadin.flow.component.html.H3("Create separate POS login accounts"),
                row(checkoutUsername, checkoutDisplayName, checkoutPassword, checkoutRole, createUser),
                checkoutUserGrid, row(resetPassword, resetUserPassword, toggleUser));
        return page;
    }

    private PasswordField checkoutPinField(String label) {
        PasswordField field = new PasswordField(label);
        field.setMaxLength(4);
        field.setPattern("[0-9]{4}");
        field.setAllowedCharPattern("[0-9]");
        field.getElement().setAttribute("inputmode", "numeric");
        return field;
    }

    private void refreshData() {
        List<Currency> currencies = dataManager.load(Currency.class).all().list();
        List<Store> stores = dataManager.load(Store.class).all().list();
        List<Product> products = dataManager.load(Product.class).all().list();
        List<StoreProduct> stock = dataManager.load(StoreProduct.class).all().list();
        List<Supplier> suppliers = dataManager.load(Supplier.class).all().list();
        List<SupplierProduct> suppliedProducts = dataManager.load(SupplierProduct.class).all().list();
        List<CheckoutUser> checkoutUsers = dataManager.load(CheckoutUser.class)
                .query("select u from CheckoutUser u order by u.displayName").list();
        if (storeCurrency != null) storeCurrency.setItems(currencies);
        if (addStore != null) addStore.setItems(stores);
        if (existingProduct != null) existingProduct.setItems(products);
        if (rateFrom != null) rateFrom.setItems(currencies);
        if (rateTo != null) rateTo.setItems(currencies);
        if (currencyGrid != null) currencyGrid.setItems(currencies);
        if (storeGrid != null) storeGrid.setItems(stores);
        if (productGrid != null) productGrid.setItems(products);
        if (stockGrid != null) stockGrid.setItems(stock);
        if (supplierGrid != null) supplierGrid.setItems(suppliers);
        if (linkSupplier != null) linkSupplier.setItems(suppliers);
        if (linkProduct != null) linkProduct.setItems(products);
        if (supplierProductGrid != null) supplierProductGrid.setItems(suppliedProducts);
        if (checkoutUserGrid != null) checkoutUserGrid.setItems(checkoutUsers);
        if (rateGrid != null) rateGrid.setItems(dataManager.load(ExchangeRate.class)
                .query("select r from ExchangeRate r order by r.effectiveAt desc").list());
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

    private static <T> java.util.UUID id(T entity) {
        if (entity instanceof Store store) return store.getId();
        if (entity instanceof Currency currency) return currency.getId();
        if (entity instanceof Product product) return product.getId();
        if (entity instanceof Supplier supplier) return supplier.getId();
        throw new IllegalArgumentException("Select a valid item.");
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
}
