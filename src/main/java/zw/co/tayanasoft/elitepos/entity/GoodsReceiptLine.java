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
@Table(name = "GOODS_RECEIPT_LINE_")
public class GoodsReceiptLine {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GOODS_RECEIPT_ID", nullable = false)
    private GoodsReceipt goodsReceipt;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PURCHASE_ORDER_LINE_ID", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;
    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "QUANTITY", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public GoodsReceipt getGoodsReceipt() { return goodsReceipt; }
    public void setGoodsReceipt(GoodsReceipt goodsReceipt) { this.goodsReceipt = goodsReceipt; }
    public PurchaseOrderLine getPurchaseOrderLine() { return purchaseOrderLine; }
    public void setPurchaseOrderLine(PurchaseOrderLine purchaseOrderLine) { this.purchaseOrderLine = purchaseOrderLine; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
