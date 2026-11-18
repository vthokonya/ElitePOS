package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "STORE_", indexes = @Index(name = "IDX_STORE__NAME", columnList = "NAME", unique = true))
public class Store {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotBlank
    @Column(name = "NAME", nullable = false, unique = true)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TRADING_CURRENCY_ID", nullable = false)
    private Currency tradingCurrency;

    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    @InstanceName
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Currency getTradingCurrency() { return tradingCurrency; }
    public void setTradingCurrency(Currency tradingCurrency) { this.tradingCurrency = tradingCurrency; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
