package com.erp_maya.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de una línea de póliza para el mayor por cuenta (con datos de la póliza). */
public record LedgerLineRow(LocalDate date, String reference, String description,
                            BigDecimal debit, BigDecimal credit) {}
