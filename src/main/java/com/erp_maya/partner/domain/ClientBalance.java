package com.erp_maya.partner.domain;

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * El saldo por cobrar de un cliente, leído de la vista v_client_balance (046).
 *
 * Es una vista mapeada como entidad y no una consulta nativa a propósito: una
 * consulta nativa que devuelve varias columnas llega como Object[] de un solo
 * elemento y revienta al leer la segunda — cuesta lo mismo y falla en runtime,
 * no al compilar.
 *
 * `@Immutable` porque no hay nada que escribir aquí: el saldo se deriva de las
 * ventas a crédito y los abonos, y el único dato propio del cliente es su
 * saldo inicial, que vive en `clients.opening_balance`.
 */
@Entity
@Immutable
@Introspected
@Table(name = "v_client_balance")
public class ClientBalance {

    @Id
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "company_id")
    private Long companyId;

    /** opening_balance + facturado a crédito − cobrado. */
    private BigDecimal balance;

    /** Lo facturado a crédito, con signo (una NC resta). */
    private BigDecimal charged;

    /** Todo lo cobrado: aplicado a un documento o a cuenta. */
    private BigDecimal paid;

    public Long getClientId() { return clientId; }
    public Long getCompanyId() { return companyId; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getCharged() { return charged; }
    public BigDecimal getPaid() { return paid; }
}
