package com.erp_maya.accounting.dto;

import java.math.BigDecimal;

/**
 * Proyección de agregación: suma de débitos y créditos por cuenta (solo cuentas
 * con movimiento, es decir las de detalle). La usa el balance de comprobación y
 * los estados financieros.
 */
public record AccountAggregate(Long accountId, String code, String name, String normalBalance,
                               BigDecimal debit, BigDecimal credit) {}
