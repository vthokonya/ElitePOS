package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "PRODUCT_", indexes = {
        @Index(name = "IDX_PRODUCT__BARCODE", columnList = "BARCODE", unique = true),
        @Index(name = "IDX_PRODUCT__SKU", columnList = "SKU", unique = true)
})
public class Product {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotBlank
    @Column(name = "NAME", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "BARCODE", nullable = false, unique = true)
    private String barcode;

    @Column(name = "SKU", unique = true)
    private String sku;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "STANDARD_COST", nullable = false, precision = 19, scale = 4)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "STANDARD_SELL_PRICE", nullable = false, precision = 19, scale = 4)
    private BigDecimal standardSellPrice = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "TAX_RATE", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    @InstanceName
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getStandardCost() { return standardCost; }
    public void setStandardCost(BigDecimal standardCost) { this.standardCost = standardCost; }
    public BigDecimal getStandardSellPrice() { return standardSellPrice; }
    public void setStandardSellPrice(BigDecimal standardSellPrice) { this.standardSellPrice = standardSellPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
