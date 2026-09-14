package com.erp_maya.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Servidor de correo saliente de una empresa. Uno a uno: la clave primaria
 * ES el company_id.
 *
 * La contraseña vive aquí y no en `companies` porque esa fila se serializa a
 * la interfaz, ni en company_settings, cuyo GET devuelve todos los valores.
 */
@Entity
@Table(name = "company_mail_settings")
public class CompanyMailSettings {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    private String host;

    private Integer port = 587;

    private String username;

    /** Nunca sale por la API: el DTO expone solo `hasPassword`. */
    private String password;

    @Column(name = "from_email")
    private String fromEmail;

    @Column(name = "from_name")
    private String fromName;

    /** none · starttls · ssl */
    @Column(nullable = false)
    private String security = "starttls";

    @Column(nullable = false)
    private Boolean enabled = Boolean.FALSE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public String getSecurity() { return security; }
    public void setSecurity(String security) { this.security = security; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
