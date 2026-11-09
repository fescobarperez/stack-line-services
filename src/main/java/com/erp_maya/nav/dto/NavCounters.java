package com.erp_maya.nav.dto;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Pendientes por opción del menú lateral. Cada número es lo que requiere
 * acción en esa pantalla; 0 = el menú no muestra contador.
 *
 * @param billing    ventas sin DTE autorizado por SAT (sin documento o con error)
 * @param returns    notas de crédito con FEL no autorizada
 * @param inventory  productos con existencia bajo el mínimo (igual que el Dashboard)
 * @param purchases  órdenes de compra pendientes o recibidas parcialmente
 * @param transfers  traslados en borrador o en tránsito
 * @param quotes     cotizaciones que requieren al asesor: prospecto / en armado
 *                   del asistente o con solicitudes de cambio sin atender
 * @param cxc        facturas por cobrar vencidas (igual que Cuentas por Cobrar)
 * @param cxp        facturas de proveedor vencidas sin pagar
 */
@Serdeable
public record NavCounters(long billing, long returns, long inventory, long purchases,
                          long transfers, long quotes, long cxc, long cxp) {}
