package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "SUPPLIER_INVOICE_", indexes = @Index(
        name = "IDX_SUPPLIER_INVOICE__SUPPLIER_NUMBER",
        columnList = "SUPPLIER_ID,INVOICE_NUMBER", unique = true))
public class SupplierInvoice {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @Column(name = "INVOICE_NUMBER", nullable = false)
    private String invoiceNumber;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STORE_ID", nullable = false)
    private Store store;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SUPPLIER_ID", nullable = false)
    private Supplier supplier;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CURRENCY_ID", nullable = false)
    private Currency currency;
    @NotNull
    @Column(name = "INVOICE_DATE", nullable = false)
    private LocalDate invoiceDate = LocalDate.now();
    @NotNull
    @DecimalMin("0.0")
    @Column(name = "TOTAL", nullable = false, precision = 19, scale = 4)
    private BigDecimal total;
    @NotNull
    @Column(name = "MATCHED_TOTAL", nullable = false, precision = 19, scale = 4)
    private BigDecimal matchedTotal = BigDecimal.ZERO;
    @NotNull
    @Column(name = "PAID_TOTAL", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidTotal = BigDecimal.ZERO;
    @Column(name = "STATUS", nullable = false)
    private String status = "UNMATCHED";

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    @InstanceName
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getMatchedTotal() { return matchedTotal; }
    public void setMatchedTotal(BigDecimal matchedTotal) { this.matchedTotal = matchedTotal; }
    public BigDecimal getPaidTotal() { return paidTotal; }
    public void setPaidTotal(BigDecimal paidTotal) { this.paidTotal = paidTotal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
