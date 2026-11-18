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
@Table(name = "SUPPLIER_PRODUCT_", uniqueConstraints =
        @UniqueConstraint(name = "IDX_SUPPLIER_PRODUCT__SUPPLIER_PRODUCT", columnNames = {"SUPPLIER_ID", "PRODUCT_ID"}))
public class SupplierProduct {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SUPPLIER_ID", nullable = false)
    private Supplier supplier;
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;
    @Column(name = "SUPPLIER_SKU")
    private String supplierSku;
    @NotNull
    @DecimalMin("0.0")
    @Column(name = "LAST_UNIT_COST", nullable = false, precision = 19, scale = 4)
    private BigDecimal lastUnitCost = BigDecimal.ZERO;
    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getSupplierSku() { return supplierSku; }
    public void setSupplierSku(String supplierSku) { this.supplierSku = supplierSku; }
    public BigDecimal getLastUnitCost() { return lastUnitCost; }
    public void setLastUnitCost(BigDecimal lastUnitCost) { this.lastUnitCost = lastUnitCost; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
