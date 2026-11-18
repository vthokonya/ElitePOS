package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "POS_VOID_", indexes = @Index(name = "IDX_POS_VOID__SHIFT_DATE", columnList = "SHIFT_ID,VOIDED_AT"))
public class PosVoid {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SHIFT_ID", nullable = false)
    private PosShift shift;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SALE_ID")
    private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STORE_PRODUCT_ID")
    private StoreProduct storeProduct;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CASHIER_ID", nullable = false)
    private CheckoutUser cashier;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SUPERVISOR_ID", nullable = false)
    private CheckoutUser supervisor;
    @NotBlank
    @Column(name = "VOID_TYPE", nullable = false, length = 32)
    private String voidType;
    @Column(name = "QUANTITY", precision = 19, scale = 4)
    private BigDecimal quantity;
    @NotBlank
    @Column(name = "REASON", nullable = false, length = 1000)
    private String reason;
    @NotNull
    @Column(name = "VOIDED_AT", nullable = false)
    private LocalDateTime voidedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public PosShift getShift() { return shift; }
    public void setShift(PosShift shift) { this.shift = shift; }
    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }
    public StoreProduct getStoreProduct() { return storeProduct; }
    public void setStoreProduct(StoreProduct storeProduct) { this.storeProduct = storeProduct; }
    public CheckoutUser getCashier() { return cashier; }
    public void setCashier(CheckoutUser cashier) { this.cashier = cashier; }
    public CheckoutUser getSupervisor() { return supervisor; }
    public void setSupervisor(CheckoutUser supervisor) { this.supervisor = supervisor; }
    public String getVoidType() { return voidType; }
    public void setVoidType(String voidType) { this.voidType = voidType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
}
