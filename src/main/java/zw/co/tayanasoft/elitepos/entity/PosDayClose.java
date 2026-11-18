package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "POS_DAY_CLOSE_", uniqueConstraints =
        @UniqueConstraint(name = "IDX_POS_DAY_CLOSE__STORE_DATE", columnNames = {"STORE_ID", "BUSINESS_DATE"}))
public class PosDayClose {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STORE_ID", nullable = false)
    private Store store;
    @NotNull
    @Column(name = "BUSINESS_DATE", nullable = false)
    private LocalDate businessDate;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CLOSED_BY_ID", nullable = false)
    private CheckoutUser closedBy;
    @NotNull
    @Column(name = "CLOSED_AT", nullable = false)
    private LocalDateTime closedAt;
    @NotNull
    @Column(name = "CASH_SALES", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashSales = BigDecimal.ZERO;
    @NotNull
    @Column(name = "NON_CASH_SALES", nullable = false, precision = 19, scale = 4)
    private BigDecimal nonCashSales = BigDecimal.ZERO;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public CheckoutUser getClosedBy() { return closedBy; }
    public void setClosedBy(CheckoutUser closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getCashSales() { return cashSales; }
    public void setCashSales(BigDecimal cashSales) { this.cashSales = cashSales; }
    public BigDecimal getNonCashSales() { return nonCashSales; }
    public void setNonCashSales(BigDecimal nonCashSales) { this.nonCashSales = nonCashSales; }
}
