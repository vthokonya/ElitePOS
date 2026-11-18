package zw.co.tayanasoft.elitepos.entity;

import io.jmix.core.annotation.Secret;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.entity.annotation.SystemLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "CHECKOUT_USER_", indexes = @Index(name = "IDX_CHECKOUT_USER__USERNAME", columnList = "USERNAME", unique = true))
public class CheckoutUser {
    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;
    @NotBlank
    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;
    @NotBlank
    @Column(name = "DISPLAY_NAME", nullable = false)
    private String displayName;
    @Secret
    @SystemLevel
    @Column(name = "PASSWORD_HASH", nullable = false)
    private String passwordHash;
    @NotBlank
    @Column(name = "CHECKOUT_ROLE", nullable = false, length = 20)
    private String checkoutRole = "CASHIER";
    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    @InstanceName
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? null : username.trim(); }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getCheckoutRole() { return checkoutRole; }
    public void setCheckoutRole(String checkoutRole) { this.checkoutRole = checkoutRole; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
