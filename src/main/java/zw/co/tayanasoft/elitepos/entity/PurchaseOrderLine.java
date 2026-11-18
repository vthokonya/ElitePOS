package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "PURCHASE_ORDER_LINE_")
public class PurchaseOrderLine {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PURCHASE_ORDER_ID", nullable = false)
    private PurchaseOrder purchaseOrder;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STORE_PRODUCT_ID", nullable = false)
    private StoreProduct storeProduct;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SUPPLIER_PRODUCT_ID", nullable = false)
    private SupplierProduct supplierProduct;
    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "ORDERED_QUANTITY", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedQuantity;
    @NotNull
    @Column(name = "RECEIVED_QUANTITY", nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;
    @NotNull
    @DecimalMin("0.0")
    @Column(name = "UNIT_COST", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public StoreProduct getStoreProduct() { return storeProduct; }
    public void setStoreProduct(StoreProduct storeProduct) { this.storeProduct = storeProduct; }
    public SupplierProduct getSupplierProduct() { return supplierProduct; }
    public void setSupplierProduct(SupplierProduct supplierProduct) { this.supplierProduct = supplierProduct; }
    public BigDecimal getOrderedQuantity() { return orderedQuantity; }
    public void setOrderedQuantity(BigDecimal orderedQuantity) { this.orderedQuantity = orderedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
