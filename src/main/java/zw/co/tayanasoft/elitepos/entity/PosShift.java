package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "POS_SHIFT_", indexes = {
        @Index(name = "IDX_POS_SHIFT__STORE_DATE_STATUS", columnList = "STORE_ID,BUSINESS_DATE,STATUS"),
        @Index(name = "IDX_POS_SHIFT__CASHIER_DATE_STATUS", columnList = "CASHIER_ID,BUSINESS_DATE,STATUS")
})
public class PosShift {
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CASHIER_ID", nullable = false)
    private CheckoutUser cashier;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OPENED_BY_ID", nullable = false)
    private CheckoutUser openedBy;
    @NotNull
    @Column(name = "BUSINESS_DATE", nullable = false)
    private LocalDate businessDate;
    @NotNull
    @Column(name = "OPENED_AT", nullable = false)
    private LocalDateTime openedAt;
    @NotNull
    @Column(name = "OPENING_FLOAT", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingFloat = BigDecimal.ZERO;
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = "OPEN";
    @Column(name = "CLOSED_AT")
    private LocalDateTime closedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLOSED_BY_ID")
    private CheckoutUser closedBy;
    @Column(name = "TERMINAL_NUMBER", length = 64)
    private String terminalNumber;
    @Column(name = "EXPECTED_CASH", precision = 19, scale = 4)
    private BigDecimal expectedCash;
    @Column(name = "COUNTED_CASH", precision = 19, scale = 4)
    private BigDecimal countedCash;
    @Column(name = "CASH_VARIANCE", precision = 19, scale = 4)
    private BigDecimal cashVariance;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    @InstanceName
    public String getShiftName() { return cashier == null ? "Shift" : cashier.getDisplayName() + " - " + businessDate; }
    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }
    public CheckoutUser getCashier() { return cashier; }
    public void setCashier(CheckoutUser cashier) { this.cashier = cashier; }
    public CheckoutUser getOpenedBy() { return openedBy; }
    public void setOpenedBy(CheckoutUser openedBy) { this.openedBy = openedBy; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(BigDecimal openingFloat) { this.openingFloat = openingFloat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public CheckoutUser getClosedBy() { return closedBy; }
    public void setClosedBy(CheckoutUser closedBy) { this.closedBy = closedBy; }
    public String getTerminalNumber() { return terminalNumber; }
    public void setTerminalNumber(String terminalNumber) { this.terminalNumber = terminalNumber; }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }
    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }
    public BigDecimal getCashVariance() { return cashVariance; }
    public void setCashVariance(BigDecimal cashVariance) { this.cashVariance = cashVariance; }
}
