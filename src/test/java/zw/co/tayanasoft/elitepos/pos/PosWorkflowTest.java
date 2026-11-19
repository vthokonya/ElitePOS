package zw.co.tayanasoft.elitepos.pos;

import io.jmix.core.DataManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.co.tayanasoft.elitepos.entity.*;
import zw.co.tayanasoft.elitepos.security.DatabaseUserRepository;
import zw.co.tayanasoft.elitepos.service.PosService;
import zw.co.tayanasoft.elitepos.test_support.AuthenticatedAsAdmin;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class PosWorkflowTest {
    @Autowired
    private DataManager dataManager;
    @Autowired
    private PosService posService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DatabaseUserRepository userRepository;

    @Test
    @Transactional
    void checkoutSessionCanAuthenticateAndReadPosResources() {
        Currency currency = posService.createCurrency("PST", "POS Test Currency");
        posService.setDefaultCurrency(currency.getId());
        Store store = posService.createStore("POS Access Test Store", currency.getId());
        CheckoutUser supervisor = posService.createCheckoutUser(
                "pos-access-supervisor", "POS Access Supervisor", "0123", "SUPERVISOR");
        CheckoutUser cashier = posService.createCheckoutUser(
                "pos-access-cashier", "POS Access Cashier", "4567", "CASHIER");
        Product product = posService.createProduct("POS Access Product", "pos-access-barcode", "POS-ACCESS",
                BigDecimal.ZERO, new BigDecimal("3"), new BigDecimal("5"));
        assertThat(dataManager.load(StoreProduct.class)
                .query("select sp from StoreProduct sp where sp.product.id = :productId")
                .parameter("productId", product.getId())
                .list()).isEmpty();
        posService.addProductToStore(store.getId(), product.getId(), null,
                new BigDecimal("3"), BigDecimal.ZERO);
        StoreProduct item = dataManager.load(StoreProduct.class)
                .query("select sp from StoreProduct sp where sp.store.id = :storeId and sp.product.id = :productId")
                .parameter("storeId", store.getId())
                .parameter("productId", product.getId())
                .one();

        var previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        var anonymousUser = userRepository.getAnonymousUser();
        var checkoutAuthentication = new UsernamePasswordAuthenticationToken(
                anonymousUser, null, anonymousUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(checkoutAuthentication);
        try {
            PosShift shift = posService.openShift(supervisor.getId(), "0123", store.getId(),
                    cashier.getId(), BigDecimal.ZERO);
            assertThat(posService.authenticateCheckoutUser("pos-access-cashier", "4567").getUsername())
                    .isEqualTo("pos-access-cashier");
            assertThat(posService.getActiveStores())
                    .extracting(Store::getId)
                    .contains(store.getId());
            assertThat(posService.scanProduct(store.getId(), "pos-access-barcode").name())
                    .isEqualTo("POS Access Product");
            assertThat(posService.scanProduct(store.getId(), "pos-access-barcode").sellPrice())
                    .isEqualByComparingTo("5");
            Sale sale = posService.recordSale(store.getId(), currency.getId(), new BigDecimal("5"),
                    "CASH", shift.getId(), cashier.getId(),
                    List.of(new PosService.CheckoutLine(item.getId(), BigDecimal.ONE)));
            assertThat(posService.getShiftSales(shift.getId()))
                    .extracting(Sale::getId)
                    .contains(sale.getId());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    @Test
    @Transactional
    void barcodeSaleReceivingInvoiceMatchingAndPayment() {
        Currency usd = posService.createCurrency("USD", "US Dollar");
        Currency zar = posService.createCurrency("ZAR", "South African Rand");
        posService.setDefaultCurrency(usd.getId());
        Store store = posService.createStore("Test Store", usd.getId());
        Supplier supplier = posService.createSupplier("Test Supplier", null, null, null, usd.getId());
        CheckoutUser supervisor = posService.createCheckoutUser("supervisor-test", "Test Supervisor",
                "0123", "SUPERVISOR");
        CheckoutUser cashier = posService.createCheckoutUser("cashier-test", "Test Cashier",
                "4567", "CASHIER");
        assertThat(passwordEncoder.matches("4567", cashier.getPasswordHash())).isTrue();
        assertThat(posService.authenticateCheckoutUser("cashier-test", "4567").getId())
                .isEqualTo(cashier.getId());
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.authenticateCheckoutUser("cashier-test", "wrong"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.createCheckoutUser("invalid-pin", "Invalid PIN", "12a4", "CASHIER"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.resetCheckoutPassword(cashier.getId(), "12345"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.openShift(cashier.getId(), "4567", store.getId(),
                        cashier.getId(), BigDecimal.ZERO));
        PosShift shift = posService.openShift(supervisor.getId(), "0123", store.getId(),
                cashier.getId(), new BigDecimal("20"));
        Product product = posService.createProduct("Coffee", "barcode-test-coffee", "COFFEE-01",
                new BigDecimal("15"), new BigDecimal("5"), new BigDecimal("12"));
        posService.addProductToStore(store.getId(), product.getId(), new BigDecimal("10"),
                new BigDecimal("5"), BigDecimal.ZERO);
        posService.addSupplierProduct(supplier.getId(), product.getId(), "SUP-COFFEE-01", new BigDecimal("5"));
        StoreProduct storeProduct = dataManager.load(StoreProduct.class)
                .query("select sp from StoreProduct sp where sp.store.id = :storeId and sp.product.id = :productId")
                .parameter("storeId", store.getId())
                .parameter("productId", product.getId())
                .one();

        assertThat(posService.scanProduct(store.getId(), product.getBarcode()).name()).isEqualTo("Coffee");
        assertThat(posService.scanProduct(store.getId(), product.getBarcode()).sellPrice())
                .isEqualByComparingTo("10");
        posService.saveExchangeRate(usd.getId(), zar.getId(), new BigDecimal("2"), LocalDateTime.now().minusMinutes(1));
        Store zarStore = posService.createStore("ZAR Store", zar.getId());
        StoreProduct zarProduct = posService.addProductToStore(zarStore.getId(), product.getId(), null,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(posService.getStoreSellPrice(zarProduct.getId())).isEqualByComparingTo("24.0000");
        assertThat(posService.getSupplierCostInStoreCurrency(zarStore.getId(), supplier.getId(),
                zarProduct.getId())).isEqualByComparingTo("10.0000");
        assertThatIllegalArgumentException().isThrownBy(() -> posService.setDefaultCurrency(zar.getId()));
        Sale sale = posService.recordSale(store.getId(), zar.getId(), new BigDecimal("46"),
                "CASH", shift.getId(), cashier.getId(),
                List.of(new PosService.CheckoutLine(storeProduct.getId(), new BigDecimal("2"))));
        assertThat(sale.getSubtotal()).isEqualByComparingTo("20.0000");
        assertThat(sale.getTaxTotal()).isEqualByComparingTo("3.0000");
        assertThat(sale.getAmountDue()).isEqualByComparingTo("46.0000");
        assertThat(sale.getChangeAmount()).isEqualByComparingTo("0.0000");
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.authorizeProductCancellation(cashier.getId(), shift.getId(), storeProduct.getId(),
                        BigDecimal.ONE, "Unauthorized attempt", "cashier-test", "4567"));
        posService.authorizeProductCancellation(cashier.getId(), shift.getId(), storeProduct.getId(),
                BigDecimal.ONE, "Customer changed mind", "supervisor-test", "0123");
        assertThat(dataManager.loadValue("select count(v) from PosVoid v where v.voidType = 'PRODUCT'", Long.class).one())
                .isEqualTo(1);

        PurchaseOrder order = posService.createPurchaseOrder(store.getId(), supplier.getId(),
                List.of(new PosService.PurchaseLineInput(storeProduct.getId(), new BigDecimal("4"), new BigDecimal("5"))));
        PurchaseOrderLine orderLine = dataManager.load(PurchaseOrderLine.class)
                .query("select l from PurchaseOrderLine l where l.purchaseOrder.id = :orderId")
                .parameter("orderId", order.getId())
                .one();
        GoodsReceipt receipt = posService.receivePurchaseOrder(order.getId(), "DELIVERY-001",
                Map.of(orderLine.getId(), new BigDecimal("2")));
        assertThat(receipt.getReference()).isEqualTo("DELIVERY-001");
        assertThat(dataManager.load(PurchaseOrder.class).id(order.getId()).one().getStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(dataManager.load(StoreProduct.class).id(storeProduct.getId()).one().getQuantity()).isEqualByComparingTo("5");
        posService.receivePurchaseOrder(order.getId(), "DELIVERY-002",
                Map.of(orderLine.getId(), new BigDecimal("2")));
        assertThat(dataManager.load(PurchaseOrder.class).id(order.getId()).one().getStatus()).isEqualTo("RECEIVED");

        SupplierInvoice invoice = posService.registerSupplierInvoice(store.getId(), supplier.getId(), usd.getId(),
                "INV-001", new BigDecimal("10"), null);
        GoodsReceiptLine receiptLine = dataManager.load(GoodsReceiptLine.class)
                .query("select l from GoodsReceiptLine l where l.goodsReceipt.id = :receiptId")
                .parameter("receiptId", receipt.getId())
                .one();
        SupplierInvoice matched = posService.matchSupplierInvoice(invoice.getId(),
                List.of(new PosService.InvoiceMatchInput(receiptLine.getId(), new BigDecimal("2"), new BigDecimal("5"))));
        assertThat(matched.getStatus()).isEqualTo("MATCHED");

        SupplierPayment firstPayment = posService.paySupplierInvoice(invoice.getId(), new BigDecimal("4"), "PAY-001");
        assertThat(firstPayment.getAmount()).isEqualByComparingTo("4");
        assertThat(dataManager.load(SupplierInvoice.class).id(invoice.getId()).one().getStatus()).isEqualTo("PARTIALLY_PAID");
        posService.paySupplierInvoice(invoice.getId(), new BigDecimal("6"), "PAY-002");
        assertThat(dataManager.load(SupplierInvoice.class).id(invoice.getId()).one().getStatus()).isEqualTo("PAID");

        Sale cancelledSale = posService.recordSale(store.getId(), usd.getId(), new BigDecimal("11.5"),
                "CASH", shift.getId(), cashier.getId(),
                List.of(new PosService.CheckoutLine(storeProduct.getId(), BigDecimal.ONE)));
        posService.cancelTransaction(cashier.getId(), cancelledSale.getId(), "Duplicate transaction",
                "supervisor-test", "0123");
        assertThat(dataManager.load(Sale.class).id(cancelledSale.getId()).one().getStatus()).isEqualTo("CANCELLED");
        assertThat(dataManager.load(StoreProduct.class).id(storeProduct.getId()).one().getQuantity()).isEqualByComparingTo("7");
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.closeDay(supervisor.getId(), "0123", store.getId()));

        PosShift closedShift = posService.closeShift(supervisor.getId(), "0123",
                shift.getId(), new BigDecimal("43"));
        assertThat(closedShift.getExpectedCash()).isEqualByComparingTo("43");
        assertThat(closedShift.getCashVariance()).isEqualByComparingTo("0");
        PosDayClose dayClose = posService.closeDay(supervisor.getId(), "0123", store.getId());
        assertThat(dayClose.getCashSales()).isEqualByComparingTo("23");
        assertThatIllegalArgumentException().isThrownBy(() ->
                posService.openShift(supervisor.getId(), "0123", store.getId(),
                        cashier.getId(), BigDecimal.ZERO));
        posService.resetCheckoutPassword(cashier.getId(), "0000");
        assertThat(posService.authenticateCheckoutUser("cashier-test", "0000").getId())
                .isEqualTo(cashier.getId());
    }
}
