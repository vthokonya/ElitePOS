package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "SALE_", indexes =
        @Index(name = "IDX_SALE__NUMBER", columnList = "SALE_NUMBER", unique = true))
public class Sale {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @Column(name = "SALE_NUMBER", nullable = false, unique = true)
    private String saleNumber;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STORE_ID", nullable = false)
    private Store store;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PAYMENT_CURRENCY_ID", nullable = false)
    private Currency paymentCurrency;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SHIFT_ID", nullable = false)
    private PosShift shift;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CASHIER_ID", nullable = false)
    private CheckoutUser cashier;
    @NotNull
    @Column(name = "PAYMENT_METHOD", nullable = false, length = 20)
    private String paymentMethod;
    @NotNull
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = "COMPLETED";
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CANCELLED_BY_ID")
    private CheckoutUser cancelledBy;
    @Column(name = "CANCELLED_AT")
    private LocalDateTime cancelledAt;
    @Column(name = "CANCELLATION_REASON", length = 1000)
    private String cancellationReason;
    @NotNull
    @DecimalMin(value = "0.00000001")
    @Column(name = "PAYMENT_RATE", nullable = false, precision = 19, scale = 8)
    private BigDecimal paymentRate;
    @NotNull
    @Column(name = "SUBTOTAL", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;
    @NotNull
    @Column(name = "TAX_TOTAL", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal;
    @NotNull
    @Column(name = "TOTAL", nullable = false, precision = 19, scale = 4)
    private BigDecimal total;
    @NotNull
    @Column(name = "AMOUNT_DUE", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDue;
    @NotNull
    @DecimalMin("0.0")
    @Column(name = "TENDERED", nullable = false, precision = 19, scale = 4)
    private BigDecimal tendered;
    @NotNull
    @Column(name = "CHANGE_AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal changeAmount;
    @NotNull
    @Column(name = "SOLD_AT", nullable = false)
    private LocalDateTime soldAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    @InstanceName
    public String getSaleNumber() { return saleNumber; }
    public void setSaleNumber(String saleNumber) { this.saleNumber = saleNumber; }
    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }
    public Currency getPaymentCurrency() { return paymentCurrency; }
    public void setPaymentCurrency(Currency paymentCurrency) { this.paymentCurrency = paymentCurrency; }
    public PosShift getShift() { return shift; }
    public void setShift(PosShift shift) { this.shift = shift; }
    public CheckoutUser getCashier() { return cashier; }
    public void setCashier(CheckoutUser cashier) { this.cashier = cashier; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public CheckoutUser getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(CheckoutUser cancelledBy) { this.cancelledBy = cancelledBy; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public BigDecimal getPaymentRate() { return paymentRate; }
    public void setPaymentRate(BigDecimal paymentRate) { this.paymentRate = paymentRate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public void setTaxTotal(BigDecimal taxTotal) { this.taxTotal = taxTotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }
    public BigDecimal getTendered() { return tendered; }
    public void setTendered(BigDecimal tendered) { this.tendered = tendered; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public void setChangeAmount(BigDecimal changeAmount) { this.changeAmount = changeAmount; }
    public LocalDateTime getSoldAt() { return soldAt; }
    public void setSoldAt(LocalDateTime soldAt) { this.soldAt = soldAt; }
}
