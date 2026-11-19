package zw.co.tayanasoft.elitepos.service;

import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import zw.co.tayanasoft.elitepos.entity.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PosService {
    private static final int MONEY_SCALE = 4;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final DataManager dataManager;
    private final PasswordEncoder passwordEncoder;

    public PosService(DataManager dataManager, PasswordEncoder passwordEncoder) {
        this.dataManager = dataManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Store> getActiveStores() {
        return dataManager.load(Store.class)
                .query("select s from Store s where s.active = true order by s.name")
                .list();
    }

    @Transactional(readOnly = true)
    public List<Currency> getActiveCurrencies() {
        return dataManager.load(Currency.class)
                .query("select c from Currency c where c.active = true order by c.code")
                .list();
    }

    @Transactional(readOnly = true)
    public List<Supplier> getActiveSuppliers() {
        return dataManager.load(Supplier.class)
                .query("select s from Supplier s where s.active = true order by s.name")
                .list();
    }

    @Transactional(readOnly = true)
    public List<StoreProduct> getSupplierProducts(UUID storeId, UUID supplierId) {
        return dataManager.load(StoreProduct.class)
                .query("select item from StoreProduct item where item.store.id = :storeId and item.active = true "
                        + "and item.product.active = true and exists (select link.id from SupplierProduct link "
                        + "where link.product.id = item.product.id and link.supplier.id = :supplierId and link.active = true) "
                        + "order by item.product.name")
                .parameter("storeId", storeId)
                .parameter("supplierId", supplierId)
                .list();
    }

    @Transactional(readOnly = true)
    public ProductLookup scanProduct(UUID storeId, String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("Scan or enter a product barcode.");
        }
        Store store = require(Store.class, storeId, "Store");
        if (!Boolean.TRUE.equals(store.getActive())) {
            throw new IllegalArgumentException("The selected store is inactive.");
        }
        StoreProduct item = dataManager.load(StoreProduct.class)
                .query("select sp from StoreProduct sp where sp.store.id = :storeId and sp.product.barcode = :barcode and sp.active = true and sp.product.active = true")
                .parameter("storeId", storeId)
                .parameter("barcode", barcode.trim())
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("No active product with barcode " + barcode + " is stocked at this store."));
        return new ProductLookup(item.getId(), item.getProduct().getBarcode(), item.getProduct().getName(),
                item.getSellPrice(), item.getProduct().getTaxRate(), item.getQuantity());
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentPaymentRate(UUID storeId, UUID paymentCurrencyId) {
        Store store = require(Store.class, storeId, "Store");
        Currency paymentCurrency = require(Currency.class, paymentCurrencyId, "Currency");
        if (!Boolean.TRUE.equals(store.getActive())) {
            throw new IllegalArgumentException("The selected store is inactive.");
        }
        if (Objects.equals(store.getTradingCurrency().getId(), paymentCurrency.getId())) {
            return ONE;
        }
        return findRate(store.getTradingCurrency(), paymentCurrency, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("No current exchange rate is configured from "
                        + store.getTradingCurrency().getCode() + " to " + paymentCurrency.getCode() + "."));
    }

    @Transactional
    public Sale recordSale(UUID storeId, UUID paymentCurrencyId, BigDecimal tendered, String paymentMethod,
                           UUID shiftId, UUID cashierId,
                           List<CheckoutLine> checkoutLines) {
        if (checkoutLines == null || checkoutLines.isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one product.");
        }
        Store store = require(Store.class, storeId, "Store");
        Currency paymentCurrency = require(Currency.class, paymentCurrencyId, "Currency");
        PosShift shift = require(PosShift.class, shiftId, "Shift");
        CheckoutUser cashier = require(CheckoutUser.class, cashierId, "Checkout user");
        if (!"OPEN".equals(shift.getStatus()) || !LocalDate.now().equals(shift.getBusinessDate())
                || !Objects.equals(shift.getStore().getId(), storeId)
                || !Objects.equals(shift.getCashier().getId(), cashierId)) {
            throw new IllegalArgumentException("The cashier must have an open shift at the selected store.");
        }
        if (!"CASHIER".equals(cashier.getCheckoutRole()) || !Boolean.TRUE.equals(cashier.getActive())) {
            throw new IllegalArgumentException("An active cashier account is required to complete sales.");
        }
        if (paymentMethod == null || !List.of("CASH", "CARD", "OTHER").contains(paymentMethod)) {
            throw new IllegalArgumentException("Select cash, card, or other as the payment method.");
        }
        if (!Boolean.TRUE.equals(paymentCurrency.getActive())) {
            throw new IllegalArgumentException("The selected payment currency is inactive.");
        }
        BigDecimal paymentRate = getCurrentPaymentRate(storeId, paymentCurrencyId);
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        for (CheckoutLine line : checkoutLines) {
            requirePositive(line.quantity(), "Sale quantity");
            quantities.merge(line.storeProductId(), line.quantity(), BigDecimal::add);
        }

        List<SaleLine> saleLines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> entry : quantities.entrySet()) {
            StoreProduct item = require(StoreProduct.class, entry.getKey(), "Store product");
            if (!Objects.equals(item.getStore().getId(), storeId) || !Boolean.TRUE.equals(item.getActive())
                    || !Boolean.TRUE.equals(item.getProduct().getActive())) {
                throw new IllegalArgumentException("A cart item is not active at the selected store.");
            }
            BigDecimal quantity = entry.getValue();
            if (item.getQuantity().compareTo(quantity) < 0) {
                throw new IllegalArgumentException("Insufficient stock for " + item.getProduct().getName()
                        + ": available " + item.getQuantity() + ", requested " + quantity + ".");
            }
            BigDecimal lineSubtotal = money(item.getSellPrice().multiply(quantity));
            BigDecimal taxRate = item.getProduct().getTaxRate();
            if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Tax rate for " + item.getProduct().getName() + " must be between 0 and 100 percent.");
            }
            BigDecimal taxAmount = money(lineSubtotal.multiply(taxRate).movePointLeft(2));
            SaleLine saleLine = dataManager.create(SaleLine.class);
            saleLine.setStoreProduct(item);
            saleLine.setQuantity(quantity);
            saleLine.setUnitPrice(item.getSellPrice());
            saleLine.setTaxRate(taxRate);
            saleLine.setLineSubtotal(lineSubtotal);
            saleLine.setTaxAmount(taxAmount);
            saleLine.setLineTotal(lineSubtotal.add(taxAmount));
            saleLines.add(saleLine);
            subtotal = subtotal.add(lineSubtotal);
            taxTotal = taxTotal.add(taxAmount);
        }
        subtotal = money(subtotal);
        taxTotal = money(taxTotal);
        BigDecimal total = money(subtotal.add(taxTotal));
        BigDecimal amountDue = money(total.multiply(paymentRate));
        if (tendered == null || tendered.compareTo(amountDue) < 0) {
            throw new IllegalArgumentException("Tendered amount must be at least " + amountDue + " " + paymentCurrency.getCode() + ".");
        }

        Sale sale = dataManager.create(Sale.class);
        sale.setSaleNumber("S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        sale.setStore(store);
        sale.setPaymentCurrency(paymentCurrency);
        sale.setShift(shift);
        sale.setCashier(cashier);
        sale.setPaymentMethod(paymentMethod);
        sale.setStatus("COMPLETED");
        sale.setPaymentRate(paymentRate);
        sale.setSubtotal(subtotal);
        sale.setTaxTotal(taxTotal);
        sale.setTotal(total);
        sale.setAmountDue(amountDue);
        sale.setTendered(money(tendered));
        sale.setChangeAmount(money(tendered.subtract(amountDue)));
        sale.setSoldAt(LocalDateTime.now());
        dataManager.save(sale);

        List<Object> entitiesToSave = new ArrayList<>();
        for (SaleLine line : saleLines) {
            line.setSale(sale);
            entitiesToSave.add(line);

            StoreProduct item = line.getStoreProduct();
            item.setQuantity(item.getQuantity().subtract(line.getQuantity()));
            entitiesToSave.add(item);
            entitiesToSave.add(stockMovement(item, line.getQuantity().negate(), "SALE", sale.getSaleNumber()));
        }
        dataManager.save(entitiesToSave.toArray());
        return sale;
    }

    @Transactional(readOnly = true)
    public List<CheckoutUser> getActiveCashiers() {
        return dataManager.load(CheckoutUser.class)
                .query("select u from CheckoutUser u where u.active = true and u.checkoutRole = 'CASHIER' order by u.displayName")
                .list();
    }

    @Transactional(readOnly = true)
    public List<PosShift> getOpenShifts(UUID storeId) {
        return dataManager.load(PosShift.class)
                .query("select s from PosShift s where s.store.id = :storeId and s.status = 'OPEN' order by s.openedAt")
                .parameter("storeId", storeId)
                .list();
    }

    @Transactional(readOnly = true)
    public List<Sale> getShiftSales(UUID shiftId) {
        return dataManager.load(Sale.class)
                .query("select s from Sale s where s.shift.id = :shiftId order by s.soldAt desc")
                .parameter("shiftId", shiftId)
                .list();
    }

    @Transactional
    public CheckoutUser createCheckoutUser(String username, String displayName, String password, String role) {
        if (username == null || username.isBlank() || displayName == null || displayName.isBlank()
                || !isCheckoutPin(password)) {
            throw new IllegalArgumentException("Enter a username, display name, and a four-digit numeric PIN.");
        }
        if (role == null || !List.of("CASHIER", "SUPERVISOR").contains(role)) {
            throw new IllegalArgumentException("Checkout role must be Cashier or Supervisor.");
        }
        CheckoutUser user = dataManager.create(CheckoutUser.class);
        user.setUsername(username.trim());
        user.setDisplayName(displayName.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCheckoutRole(role);
        user.setActive(true);
        return dataManager.save(user);
    }

    @Transactional
    public CheckoutUser setCheckoutUserActive(UUID userId, boolean active) {
        CheckoutUser user = require(CheckoutUser.class, userId, "Checkout user");
        user.setActive(active);
        return dataManager.save(user);
    }

    @Transactional
    public CheckoutUser resetCheckoutPassword(UUID userId, String password) {
        if (!isCheckoutPin(password)) {
            throw new IllegalArgumentException("Checkout PINs must contain exactly four digits.");
        }
        CheckoutUser user = require(CheckoutUser.class, userId, "Checkout user");
        user.setPasswordHash(passwordEncoder.encode(password));
        return dataManager.save(user);
    }

    @Transactional(readOnly = true)
    public CheckoutUser authenticateCheckoutUser(String username, String password) {
        if (username == null || username.isBlank() || !isCheckoutPin(password)) {
            throw new IllegalArgumentException("Enter your checkout username and four-digit PIN.");
        }
        CheckoutUser user = dataManager.unconstrained().load(CheckoutUser.class)
                .query("select u from CheckoutUser u where u.username = :username and u.active = true")
                .parameter("username", username.trim())
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Checkout login failed."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Checkout login failed.");
        }
        return user;
    }

    private static boolean isCheckoutPin(String pin) {
        return pin != null && pin.matches("[0-9]{4}");
    }

    @Transactional(readOnly = true)
    public PosShift getOpenShiftForCashier(UUID storeId, UUID cashierId) {
        return dataManager.load(PosShift.class)
                .query("select s from PosShift s where s.store.id = :storeId and s.cashier.id = :cashierId "
                        + "and s.status = 'OPEN' and s.businessDate = :businessDate")
                .parameter("storeId", storeId)
                .parameter("cashierId", cashierId)
                .parameter("businessDate", LocalDate.now())
                .optional()
                .orElse(null);
    }

    @Transactional
    public PosShift openShift(UUID supervisorId, String supervisorPassword, UUID storeId,
                              UUID cashierId, BigDecimal openingFloat) {
        CheckoutUser supervisor = requireSupervisor(supervisorId, supervisorPassword);
        requireNonNegative(openingFloat, "Opening float");
        Store store = require(Store.class, storeId, "Store");
        CheckoutUser cashier = requireCashier(require(CheckoutUser.class, cashierId, "Cashier"));
        if (!Boolean.TRUE.equals(store.getActive()) || !Boolean.TRUE.equals(cashier.getActive())
                || !"CASHIER".equals(cashier.getCheckoutRole())) {
            throw new IllegalArgumentException("Select an active store and active cashier.");
        }
        LocalDate businessDate = LocalDate.now();
        if (dayAlreadyClosed(storeId, businessDate)) {
            throw new IllegalArgumentException("The trading day has already been closed for this store.");
        }
        boolean cashierHasOpenShift = dataManager.load(PosShift.class)
                .query("select s from PosShift s where s.store.id = :storeId and s.cashier.id = :cashierId and s.status = 'OPEN'")
                .parameter("storeId", storeId)
                .parameter("cashierId", cashierId)
                .optional()
                .isPresent();
        if (cashierHasOpenShift) {
            throw new IllegalArgumentException("This cashier already has an open shift at this store.");
        }
        PosShift shift = dataManager.create(PosShift.class);
        shift.setStore(store);
        shift.setCashier(cashier);
        shift.setOpenedBy(supervisor);
        shift.setBusinessDate(businessDate);
        shift.setOpenedAt(LocalDateTime.now());
        shift.setOpeningFloat(money(openingFloat));
        shift.setStatus("OPEN");
        return dataManager.save(shift);
    }

    @Transactional
    public PosShift closeShift(UUID supervisorId, String supervisorPassword, UUID shiftId,
                               BigDecimal countedCash) {
        CheckoutUser supervisor = requireSupervisor(supervisorId, supervisorPassword);
        requireNonNegative(countedCash, "Counted cash");
        PosShift shift = require(PosShift.class, shiftId, "Shift");
        if (!"OPEN".equals(shift.getStatus())) {
            throw new IllegalArgumentException("This shift is already closed.");
        }
        BigDecimal cashSales = getShiftSales(shiftId).stream()
                .filter(sale -> "COMPLETED".equals(sale.getStatus()) && "CASH".equals(sale.getPaymentMethod()))
                .map(Sale::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = money(shift.getOpeningFloat().add(cashSales));
        shift.setExpectedCash(expected);
        shift.setCountedCash(money(countedCash));
        shift.setCashVariance(money(countedCash.subtract(expected)));
        shift.setClosedBy(supervisor);
        shift.setClosedAt(LocalDateTime.now());
        shift.setStatus("CLOSED");
        return dataManager.save(shift);
    }

    @Transactional
    public PosDayClose closeDay(UUID supervisorId, String supervisorPassword, UUID storeId) {
        CheckoutUser supervisor = requireSupervisor(supervisorId, supervisorPassword);
        Store store = require(Store.class, storeId, "Store");
        LocalDate businessDate = LocalDate.now();
        if (dayAlreadyClosed(storeId, businessDate)) {
            throw new IllegalArgumentException("The trading day has already been closed for this store.");
        }
        long openShifts = dataManager.loadValue(
                        "select count(s) from PosShift s where s.store.id = :storeId and s.status = 'OPEN'",
                        Long.class)
                .parameter("storeId", storeId)
                .one();
        if (openShifts > 0) {
            throw new IllegalArgumentException("Close every open shift for this store before closing the day.");
        }
        List<Sale> sales = dataManager.load(Sale.class)
                .query("select s from Sale s where s.store.id = :storeId and s.shift.businessDate = :businessDate and s.status = 'COMPLETED'")
                .parameter("storeId", storeId)
                .parameter("businessDate", businessDate)
                .list();
        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal nonCashSales = BigDecimal.ZERO;
        for (Sale sale : sales) {
            if ("CASH".equals(sale.getPaymentMethod())) cashSales = cashSales.add(sale.getTotal());
            else nonCashSales = nonCashSales.add(sale.getTotal());
        }
        PosDayClose dayClose = dataManager.create(PosDayClose.class);
        dayClose.setStore(store);
        dayClose.setBusinessDate(businessDate);
        dayClose.setClosedBy(supervisor);
        dayClose.setClosedAt(LocalDateTime.now());
        dayClose.setCashSales(money(cashSales));
        dayClose.setNonCashSales(money(nonCashSales));
        return dataManager.save(dayClose);
    }

    @Transactional
    public PosVoid authorizeProductCancellation(UUID cashierId, UUID shiftId, UUID storeProductId,
                                                BigDecimal quantity, String reason,
                                                String supervisorUsername, String supervisorPassword) {
        CheckoutUser cashier = requireCashier(require(CheckoutUser.class, cashierId, "Cashier"));
        CheckoutUser supervisor = authenticateCheckoutUser(supervisorUsername, supervisorPassword);
        requireSupervisorRole(supervisor);
        requirePositive(quantity, "Cancellation quantity");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter a reason for cancelling the product.");
        }
        PosShift shift = requireOpenCashierShift(cashier, shiftId);
        StoreProduct item = require(StoreProduct.class, storeProductId, "Store product");
        if (!Objects.equals(item.getStore().getId(), shift.getStore().getId())) {
            throw new IllegalArgumentException("The product is not part of this shift's store.");
        }
        return saveVoid(shift, null, item, cashier, supervisor, "PRODUCT", quantity, reason);
    }

    @Transactional
    public PosVoid cancelTransaction(UUID cashierId, UUID saleId, String reason,
                                     String supervisorUsername, String supervisorPassword) {
        CheckoutUser cashier = requireCashier(require(CheckoutUser.class, cashierId, "Cashier"));
        CheckoutUser supervisor = authenticateCheckoutUser(supervisorUsername, supervisorPassword);
        requireSupervisorRole(supervisor);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter a reason for cancelling the transaction.");
        }
        Sale sale = require(Sale.class, saleId, "Sale");
        if (sale.getShift() == null) {
            throw new IllegalArgumentException("This transaction is not associated with a checkout shift.");
        }
        PosShift shift = requireOpenCashierShift(cashier, sale.getShift().getId());
        if (!Objects.equals(sale.getCashier().getId(), cashier.getId()) || !"COMPLETED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Only a completed transaction from the active cashier shift can be cancelled.");
        }
        List<SaleLine> lines = dataManager.load(SaleLine.class)
                .query("select l from SaleLine l where l.sale.id = :saleId")
                .parameter("saleId", saleId)
                .list();
        List<Object> entities = new ArrayList<>();
        for (SaleLine line : lines) {
            StoreProduct item = line.getStoreProduct();
            item.setQuantity(item.getQuantity().add(line.getQuantity()));
            entities.add(item);
            entities.add(stockMovement(item, line.getQuantity(), "SALE_CANCEL", sale.getSaleNumber()));
        }
        sale.setStatus("CANCELLED");
        sale.setCancelledBy(supervisor);
        sale.setCancelledAt(LocalDateTime.now());
        sale.setCancellationReason(reason.trim());
        entities.add(sale);
        PosVoid posVoid = saveVoid(shift, sale, null, cashier, supervisor, "TRANSACTION", null, reason);
        entities.add(posVoid);
        dataManager.save(entities.toArray());
        return posVoid;
    }

    private CheckoutUser requireSupervisor(UUID supervisorId, String password) {
        CheckoutUser supervisor = authenticateCheckoutUser(
                require(CheckoutUser.class, supervisorId, "Supervisor").getUsername(), password);
        requireSupervisorRole(supervisor);
        return supervisor;
    }

    private static void requireSupervisorRole(CheckoutUser user) {
        if (!"SUPERVISOR".equals(user.getCheckoutRole())) {
            throw new IllegalArgumentException("Supervisor authorization is required.");
        }
    }

    private static CheckoutUser requireCashier(CheckoutUser user) {
        if (!Boolean.TRUE.equals(user.getActive()) || !"CASHIER".equals(user.getCheckoutRole())) {
            throw new IllegalArgumentException("An active cashier account is required.");
        }
        return user;
    }

    private PosShift requireOpenCashierShift(CheckoutUser cashier, UUID shiftId) {
        PosShift shift = require(PosShift.class, shiftId, "Shift");
        if (!"OPEN".equals(shift.getStatus()) || !Objects.equals(shift.getCashier().getId(), cashier.getId())
                || !LocalDate.now().equals(shift.getBusinessDate())) {
            throw new IllegalArgumentException("The cashier must have an open shift for today.");
        }
        return shift;
    }

    private boolean dayAlreadyClosed(UUID storeId, LocalDate businessDate) {
        return dataManager.load(PosDayClose.class)
                .query("select d from PosDayClose d where d.store.id = :storeId and d.businessDate = :businessDate")
                .parameter("storeId", storeId)
                .parameter("businessDate", businessDate)
                .optional().isPresent();
    }

    private PosVoid saveVoid(PosShift shift, Sale sale, StoreProduct item, CheckoutUser cashier,
                             CheckoutUser supervisor, String type, BigDecimal quantity, String reason) {
        PosVoid posVoid = dataManager.create(PosVoid.class);
        posVoid.setShift(shift);
        posVoid.setSale(sale);
        posVoid.setStoreProduct(item);
        posVoid.setCashier(cashier);
        posVoid.setSupervisor(supervisor);
        posVoid.setVoidType(type);
        posVoid.setQuantity(quantity);
        posVoid.setReason(reason.trim());
        posVoid.setVoidedAt(LocalDateTime.now());
        return dataManager.save(posVoid);
    }

    @Transactional
    public ExchangeRate saveExchangeRate(UUID fromCurrencyId, UUID toCurrencyId, BigDecimal rate,
                                         LocalDateTime effectiveAt) {
        requirePositive(rate, "Exchange rate");
        if (effectiveAt == null || effectiveAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Exchange rate effective time cannot be in the future.");
        }
        Currency from = require(Currency.class, fromCurrencyId, "Source currency");
        Currency to = require(Currency.class, toCurrencyId, "Target currency");
        if (Objects.equals(from.getId(), to.getId())) {
            throw new IllegalArgumentException("Source and target currencies must be different.");
        }
        ExchangeRate exchangeRate = dataManager.create(ExchangeRate.class);
        exchangeRate.setFromCurrency(from);
        exchangeRate.setToCurrency(to);
        exchangeRate.setRate(rate);
        exchangeRate.setEffectiveAt(effectiveAt);
        return dataManager.save(exchangeRate);
    }

    @Transactional
    public Store createStore(String name, UUID tradingCurrencyId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Store name is required.");
        }
        Currency currency = require(Currency.class, tradingCurrencyId, "Trading currency");
        if (!Boolean.TRUE.equals(currency.getActive())) {
            throw new IllegalArgumentException("Trading currency must be active.");
        }
        Store store = dataManager.create(Store.class);
        store.setName(name.trim());
        store.setTradingCurrency(currency);
        store.setActive(true);
        return dataManager.save(store);
    }

    @Transactional
    public Currency createCurrency(String code, String name) {
        if (code == null || !code.trim().matches("[A-Za-z]{3}") || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a three-letter currency code and currency name.");
        }
        Currency currency = dataManager.create(Currency.class);
        currency.setCode(code.trim());
        currency.setName(name.trim());
        currency.setActive(true);
        return dataManager.save(currency);
    }

    @Transactional
    public Supplier createSupplier(String name, String phone, String email, String address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name is required.");
        }
        Supplier supplier = dataManager.create(Supplier.class);
        supplier.setName(name.trim());
        supplier.setPhone(phone);
        supplier.setEmail(email);
        supplier.setAddress(address);
        supplier.setActive(true);
        return dataManager.save(supplier);
    }

    @Transactional
    public SupplierProduct addSupplierProduct(UUID supplierId, UUID productId, String supplierSku,
                                              BigDecimal lastUnitCost) {
        requireNonNegative(lastUnitCost, "Supplier unit cost");
        Supplier supplier = require(Supplier.class, supplierId, "Supplier");
        Product product = require(Product.class, productId, "Product");
        if (!Boolean.TRUE.equals(supplier.getActive()) || !Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException("The selected supplier and product must be active.");
        }
        SupplierProduct supplierProduct = dataManager.create(SupplierProduct.class);
        supplierProduct.setSupplier(supplier);
        supplierProduct.setProduct(product);
        supplierProduct.setSupplierSku(supplierSku == null || supplierSku.isBlank() ? null : supplierSku.trim());
        supplierProduct.setLastUnitCost(money(lastUnitCost));
        supplierProduct.setActive(true);
        return dataManager.save(supplierProduct);
    }

    @Transactional
    public Product createProduct(String name, String barcode, String sku, BigDecimal taxRate) {
        if (name == null || name.isBlank() || barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("Product name and barcode are required.");
        }
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100 percent.");
        }
        Product product = dataManager.create(Product.class);
        product.setName(name.trim());
        product.setBarcode(barcode.trim());
        product.setSku(sku == null || sku.isBlank() ? null : sku.trim());
        product.setTaxRate(taxRate);
        product.setActive(true);
        return dataManager.save(product);
    }

    @Transactional
    public StoreProduct addProductToStore(UUID storeId, UUID productId, BigDecimal sellPrice,
                                          BigDecimal initialQuantity, BigDecimal reorderLevel) {
        requireNonNegative(sellPrice, "Selling price");
        requireNonNegative(initialQuantity, "Initial quantity");
        requireNonNegative(reorderLevel, "Reorder level");
        Store store = require(Store.class, storeId, "Store");
        Product product = require(Product.class, productId, "Product");
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException("Product must be active.");
        }
        StoreProduct item = dataManager.create(StoreProduct.class);
        item.setStore(store);
        item.setProduct(product);
        item.setSellPrice(money(sellPrice));
        item.setQuantity(initialQuantity);
        item.setReorderLevel(reorderLevel);
        item.setActive(true);
        item = dataManager.save(item);
        if (initialQuantity.signum() > 0) {
            dataManager.save(stockMovement(item, initialQuantity, "OPENING", "Opening stock"));
        }
        return item;
    }

    @Transactional
    public Product updateProduct(UUID productId, String name, String barcode, String sku, BigDecimal taxRate) {
        if (name == null || name.isBlank() || barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("Product name and barcode are required.");
        }
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100 percent.");
        }
        Product product = require(Product.class, productId, "Product");
        product.setName(name.trim());
        product.setBarcode(barcode.trim());
        product.setSku(sku == null || sku.isBlank() ? null : sku.trim());
        product.setTaxRate(taxRate);
        return dataManager.save(product);
    }

    @Transactional
    public Supplier updateSupplier(UUID supplierId, String name, String phone, String email, String address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name is required.");
        }
        Supplier supplier = require(Supplier.class, supplierId, "Supplier");
        supplier.setName(name.trim());
        supplier.setPhone(phone);
        supplier.setEmail(email);
        supplier.setAddress(address);
        return dataManager.save(supplier);
    }

    @Transactional
    public StoreProduct updateStoreProduct(UUID storeProductId, BigDecimal sellPrice, BigDecimal reorderLevel,
                                          BigDecimal quantityChange, String adjustmentReason) {
        requireNonNegative(sellPrice, "Selling price");
        requireNonNegative(reorderLevel, "Reorder level");
        StoreProduct item = require(StoreProduct.class, storeProductId, "Store product");
        item.setSellPrice(money(sellPrice));
        item.setReorderLevel(reorderLevel);
        if (quantityChange != null && quantityChange.signum() != 0) {
            if (adjustmentReason == null || adjustmentReason.isBlank()) {
                throw new IllegalArgumentException("Enter a reason for the stock adjustment.");
            }
            BigDecimal newQuantity = item.getQuantity().add(quantityChange);
            if (newQuantity.signum() < 0) {
                throw new IllegalArgumentException("Stock adjustment cannot make inventory negative.");
            }
            item.setQuantity(newQuantity);
        }
        item = dataManager.save(item);
        if (quantityChange != null && quantityChange.signum() != 0) {
            dataManager.save(stockMovement(item, quantityChange, "ADJUSTMENT", adjustmentReason.trim()));
        }
        return item;
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(UUID storeId, UUID supplierId, List<PurchaseLineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A purchase order must contain at least one product.");
        }
        Store store = require(Store.class, storeId, "Store");
        Supplier supplier = require(Supplier.class, supplierId, "Supplier");
        if (!Boolean.TRUE.equals(store.getActive()) || !Boolean.TRUE.equals(supplier.getActive())) {
            throw new IllegalArgumentException("The selected store and supplier must be active.");
        }
        PurchaseOrder order = dataManager.create(PurchaseOrder.class);
        order.setOrderNumber("PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        order.setStore(store);
        order.setSupplier(supplier);
        order.setCurrency(store.getTradingCurrency());
        order.setOrderDate(LocalDate.now());
        order.setStatus("OPEN");
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderLine> orderLines = new ArrayList<>();
        for (PurchaseLineInput input : lines) {
            requirePositive(input.quantity(), "Order quantity");
            if (input.unitCost() == null || input.unitCost().signum() < 0) {
                throw new IllegalArgumentException("Unit cost cannot be negative.");
            }
            StoreProduct item = require(StoreProduct.class, input.storeProductId(), "Store product");
            if (!Objects.equals(item.getStore().getId(), storeId) || !Boolean.TRUE.equals(item.getActive())) {
                throw new IllegalArgumentException("Every ordered product must be active at the selected store.");
            }
            SupplierProduct supplierProduct = dataManager.load(SupplierProduct.class)
                    .query("select sp from SupplierProduct sp where sp.supplier.id = :supplierId and sp.product.id = :productId and sp.active = true")
                    .parameter("supplierId", supplierId)
                    .parameter("productId", item.getProduct().getId())
                    .optional()
                    .orElseThrow(() -> new IllegalArgumentException(item.getProduct().getName()
                            + " is not linked to the selected supplier."));
            PurchaseOrderLine line = dataManager.create(PurchaseOrderLine.class);
            line.setPurchaseOrder(order);
            line.setStoreProduct(item);
            line.setSupplierProduct(supplierProduct);
            line.setOrderedQuantity(input.quantity());
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setUnitCost(input.unitCost());
            supplierProduct.setLastUnitCost(money(input.unitCost()));
            dataManager.save(supplierProduct);
            orderLines.add(line);
            total = total.add(input.quantity().multiply(input.unitCost()));
        }
        order.setTotal(money(total));
        dataManager.save(order);
        dataManager.save(orderLines.toArray());
        return order;
    }

    @Transactional
    public GoodsReceipt receivePurchaseOrder(UUID orderId, String reference,
                                            Map<UUID, BigDecimal> receivedQuantities) {
        if (reference == null || reference.isBlank() || receivedQuantities == null || receivedQuantities.isEmpty()) {
            throw new IllegalArgumentException("A receipt reference and at least one received quantity are required.");
        }
        PurchaseOrder order = require(PurchaseOrder.class, orderId, "Purchase order");
        if ("CLOSED".equals(order.getStatus()) || "RECEIVED".equals(order.getStatus())) {
            throw new IllegalArgumentException("This purchase order is already fully received.");
        }
        GoodsReceipt receipt = dataManager.create(GoodsReceipt.class);
        receipt.setReference(reference.trim());
        receipt.setPurchaseOrder(order);
        receipt.setReceivedDate(LocalDate.now());
        dataManager.save(receipt);

        List<Object> entitiesToSave = new ArrayList<>();
        Map<UUID, BigDecimal> resultingReceivedQuantities = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> entry : receivedQuantities.entrySet()) {
            requirePositive(entry.getValue(), "Received quantity");
            PurchaseOrderLine line = require(PurchaseOrderLine.class, entry.getKey(), "Purchase order line");
            if (!Objects.equals(line.getPurchaseOrder().getId(), orderId)) {
                throw new IllegalArgumentException("A receipt line does not belong to this purchase order.");
            }
            BigDecimal remaining = line.getOrderedQuantity().subtract(line.getReceivedQuantity());
            if (entry.getValue().compareTo(remaining) > 0) {
                throw new IllegalArgumentException("Cannot receive more than the remaining quantity for "
                        + line.getStoreProduct().getProduct().getName() + ".");
            }
            BigDecimal resultingReceived = line.getReceivedQuantity().add(entry.getValue());
            line.setReceivedQuantity(resultingReceived);
            resultingReceivedQuantities.put(line.getId(), resultingReceived);
            StoreProduct item = line.getStoreProduct();
            item.setQuantity(item.getQuantity().add(entry.getValue()));

            GoodsReceiptLine receiptLine = dataManager.create(GoodsReceiptLine.class);
            receiptLine.setGoodsReceipt(receipt);
            receiptLine.setPurchaseOrderLine(line);
            receiptLine.setQuantity(entry.getValue());
            entitiesToSave.add(line);
            entitiesToSave.add(item);
            entitiesToSave.add(receiptLine);
            entitiesToSave.add(stockMovement(item, entry.getValue(), "RECEIPT", reference.trim()));
        }
        List<PurchaseOrderLine> allLines = orderLines(orderId);
        boolean fullyReceived = true;
        for (PurchaseOrderLine line : allLines) {
            BigDecimal received = resultingReceivedQuantities.getOrDefault(line.getId(), line.getReceivedQuantity());
            if (received.compareTo(line.getOrderedQuantity()) < 0) {
                fullyReceived = false;
            }
        }
        order.setStatus(fullyReceived ? "RECEIVED" : "PARTIALLY_RECEIVED");
        entitiesToSave.add(order);
        dataManager.save(entitiesToSave.toArray());
        return receipt;
    }

    @Transactional
    public SupplierInvoice registerSupplierInvoice(UUID storeId, UUID supplierId, UUID currencyId,
                                                    String invoiceNumber, BigDecimal total,
                                                    LocalDate invoiceDate) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Supplier invoice number is required.");
        }
        requirePositive(total, "Invoice total");
        Store store = require(Store.class, storeId, "Store");
        Supplier supplier = require(Supplier.class, supplierId, "Supplier");
        Currency currency = require(Currency.class, currencyId, "Invoice currency");
        if (!Boolean.TRUE.equals(store.getActive()) || !Boolean.TRUE.equals(supplier.getActive())) {
            throw new IllegalArgumentException("The selected store and supplier must be active.");
        }
        if (!Objects.equals(currency.getId(), store.getTradingCurrency().getId())) {
            throw new IllegalArgumentException("Supplier invoices must use the store's trading currency.");
        }
        SupplierInvoice invoice = dataManager.create(SupplierInvoice.class);
        invoice.setStore(store);
        invoice.setSupplier(supplier);
        invoice.setCurrency(currency);
        invoice.setInvoiceNumber(invoiceNumber.trim());
        invoice.setInvoiceDate(invoiceDate == null ? LocalDate.now() : invoiceDate);
        invoice.setTotal(money(total));
        invoice.setMatchedTotal(BigDecimal.ZERO);
        invoice.setPaidTotal(BigDecimal.ZERO);
        invoice.setStatus("UNMATCHED");
        return dataManager.save(invoice);
    }

    @Transactional
    public SupplierInvoice matchSupplierInvoice(UUID invoiceId, List<InvoiceMatchInput> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Select at least one received product to match.");
        }
        SupplierInvoice invoice = require(SupplierInvoice.class, invoiceId, "Supplier invoice");
        if (invoice.getPaidTotal().signum() > 0) {
            throw new IllegalArgumentException("A paid invoice cannot be changed.");
        }
        BigDecimal newMatchedTotal = invoice.getMatchedTotal();
        List<SupplierInvoiceMatch> newMatches = new ArrayList<>();
        Map<UUID, BigDecimal> pendingQuantities = new HashMap<>();
        for (InvoiceMatchInput input : matches) {
            requirePositive(input.quantity(), "Matched quantity");
            if (input.unitCost() == null || input.unitCost().signum() < 0) {
                throw new IllegalArgumentException("Matched unit cost cannot be negative.");
            }
            GoodsReceiptLine receiptLine = require(GoodsReceiptLine.class, input.receiptLineId(), "Receipt line");
            PurchaseOrder order = receiptLine.getGoodsReceipt().getPurchaseOrder();
            if (!Objects.equals(order.getSupplier().getId(), invoice.getSupplier().getId())
                    || !Objects.equals(order.getStore().getId(), invoice.getStore().getId())
                    || !Objects.equals(order.getCurrency().getId(), invoice.getCurrency().getId())) {
                throw new IllegalArgumentException("The received product does not match this invoice's supplier, store, or currency.");
            }
            BigDecimal alreadyMatched = matchedReceiptQuantity(receiptLine.getId())
                    .add(pendingQuantities.getOrDefault(receiptLine.getId(), BigDecimal.ZERO));
            if (alreadyMatched.add(input.quantity()).compareTo(receiptLine.getQuantity()) > 0) {
                throw new IllegalArgumentException("The matched quantity exceeds the unbilled quantity received.");
            }
            pendingQuantities.merge(receiptLine.getId(), input.quantity(), BigDecimal::add);
            SupplierInvoiceMatch match = dataManager.create(SupplierInvoiceMatch.class);
            match.setInvoice(invoice);
            match.setReceiptLine(receiptLine);
            match.setQuantity(input.quantity());
            match.setUnitCost(input.unitCost());
            newMatches.add(match);
            newMatchedTotal = newMatchedTotal.add(input.quantity().multiply(input.unitCost()));
        }
        newMatchedTotal = money(newMatchedTotal);
        if (newMatchedTotal.compareTo(invoice.getTotal()) > 0) {
            throw new IllegalArgumentException("Matched products exceed the supplier invoice total.");
        }
        invoice.setMatchedTotal(newMatchedTotal);
        invoice.setStatus(newMatchedTotal.compareTo(invoice.getTotal()) == 0 ? "MATCHED" : "PARTIALLY_MATCHED");
        dataManager.save(invoice);
        dataManager.save(newMatches.toArray());
        return invoice;
    }

    @Transactional
    public SupplierPayment paySupplierInvoice(UUID invoiceId, BigDecimal amount, String reference) {
        requirePositive(amount, "Payment amount");
        SupplierInvoice invoice = require(SupplierInvoice.class, invoiceId, "Supplier invoice");
        if (!"MATCHED".equals(invoice.getStatus()) && !"PARTIALLY_PAID".equals(invoice.getStatus())) {
            throw new IllegalArgumentException("An invoice must be fully matched to receipts before it can be paid.");
        }
        BigDecimal outstanding = invoice.getTotal().subtract(invoice.getPaidTotal());
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("Payment exceeds the invoice's outstanding balance of " + outstanding + ".");
        }
        SupplierPayment payment = dataManager.create(SupplierPayment.class);
        payment.setInvoice(invoice);
        payment.setAmount(money(amount));
        payment.setPaidAt(LocalDateTime.now());
        payment.setReference(reference);
        invoice.setPaidTotal(money(invoice.getPaidTotal().add(amount)));
        invoice.setStatus(invoice.getPaidTotal().compareTo(invoice.getTotal()) == 0 ? "PAID" : "PARTIALLY_PAID");
        dataManager.save(invoice);
        return dataManager.save(payment);
    }

    private List<PurchaseOrderLine> orderLines(UUID orderId) {
        return dataManager.load(PurchaseOrderLine.class)
                .query("select l from PurchaseOrderLine l where l.purchaseOrder.id = :orderId")
                .parameter("orderId", orderId)
                .list();
    }

    private BigDecimal matchedReceiptQuantity(UUID receiptLineId) {
        List<BigDecimal> quantities = dataManager.loadValue(
                        "select m.quantity from SupplierInvoiceMatch m where m.receiptLine.id = :receiptLineId",
                        BigDecimal.class)
                .parameter("receiptLineId", receiptLineId)
                .list();
        return quantities.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<BigDecimal> findRate(Currency from, Currency to, LocalDateTime asOf) {
        List<ExchangeRate> direct = dataManager.load(ExchangeRate.class)
                .query("select r from ExchangeRate r where r.fromCurrency.id = :fromId and r.toCurrency.id = :toId and r.effectiveAt <= :asOf order by r.effectiveAt desc")
                .parameter("fromId", from.getId())
                .parameter("toId", to.getId())
                .parameter("asOf", asOf)
                .maxResults(1)
                .list();
        if (!direct.isEmpty()) {
            return Optional.of(direct.get(0).getRate());
        }
        List<ExchangeRate> inverse = dataManager.load(ExchangeRate.class)
                .query("select r from ExchangeRate r where r.fromCurrency.id = :fromId and r.toCurrency.id = :toId and r.effectiveAt <= :asOf order by r.effectiveAt desc")
                .parameter("fromId", to.getId())
                .parameter("toId", from.getId())
                .parameter("asOf", asOf)
                .maxResults(1)
                .list();
        return inverse.stream().findFirst().map(rate -> ONE.divide(rate.getRate(), 8, RoundingMode.HALF_UP));
    }

    private StockMovement stockMovement(StoreProduct item, BigDecimal change, String type, String reference) {
        StockMovement movement = dataManager.create(StockMovement.class);
        movement.setStoreProduct(item);
        movement.setQuantityChange(change);
        movement.setMovementType(type);
        movement.setReference(reference);
        movement.setMovedAt(LocalDateTime.now());
        return movement;
    }

    private <T> T require(Class<T> entityClass, UUID id, String label) {
        if (id == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return dataManager.load(entityClass).id(id).optional()
                .orElseThrow(() -> new IllegalArgumentException(label + " was not found."));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
    }

    public record ProductLookup(UUID storeProductId, String barcode, String name, BigDecimal sellPrice,
                                BigDecimal taxRate, BigDecimal availableQuantity) {}
    public record CheckoutLine(UUID storeProductId, BigDecimal quantity) {}
    public record PurchaseLineInput(UUID storeProductId, BigDecimal quantity, BigDecimal unitCost) {}
    public record InvoiceMatchInput(UUID receiptLineId, BigDecimal quantity, BigDecimal unitCost) {}
}
