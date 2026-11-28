package com.erp_maya.pos.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SaleDtos {

    private SaleDtos() {}

    @Serdeable
    /**
     * `productId` o `concept`: uno de los dos. Sin producto es un renglón de
     * servicio —anticipo, avance de proyecto, mano de obra— que no toca bodega.
     */
    public record ItemRequest(Long productId, String concept, @NotNull BigDecimal quantity,
                              @NotNull BigDecimal unitPrice, BigDecimal discount,
                              String discountSource, Long promotionId) {}

    @Serdeable
    /**
     * `cashRegisterId` es opcional solo para una venta a crédito: esa no mueve
     * dinero, así que no pertenece a ningún arqueo. Una venta de contado sin
     * turno se rechaza — sería la puerta de atrás al control de cajas.
     */
    public record Request(String docNumber, String docType, String series, Long relatedSaleId,
                          String reason, Long clientId, Long branchId, Long userId,
                          Long cashRegisterId, String paymentMethod, String status,
                          Boolean credit, BigDecimal discountTotal, Long authorizationId,
                          Long projectId, @NotEmpty @Valid List<ItemRequest> items) {}

    /** Quién recibió el impreso original. Vacío = lo recibió el cliente. */
    @Serdeable
    public record DeliverRequest(String deliveredTo) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {}

    @Serdeable
    public record Response(Long id, String docNumber, String docType, String series,
                           Long relatedSaleId, String reason, Long clientId, String clientName,
                           Long branchId, String branchName, Long userId, Long cashRegisterId,
                           Instant saleDate, String paymentMethod, BigDecimal subtotal, BigDecimal tax,
                           BigDecimal taxRate, BigDecimal total, BigDecimal signedTotal,
                           boolean credit, Long projectId,
                           Instant deliveredAt, String deliveredTo,
                           String status, List<ItemResponse> items,
                           Instant voidedAt, String voidReason) {}

    /** Anular una venta: el motivo es obligatorio (auditoría y SAT). */
    @Serdeable
    public record VoidRequest(@NotBlank @jakarta.validation.constraints.Size(min = 5, max = 300) String reason) {}

    // ── Ticket de la venta (POS → Imprimir / Descargar PDF / Enviar) ─────

    /** Un renglón del ticket, ya con el nombre que se imprime. */
    @Serdeable
    public record TicketLine(String description, BigDecimal quantity, String unit,
                             BigDecimal unitPrice, BigDecimal discount, BigDecimal total) {}

    /** El DTE de la venta, si se certificó. */
    @Serdeable
    public record TicketFel(String dteType, String series, String number, String authorization,
                            Instant certifiedAt, String resolution) {}

    /**
     * Todo lo que lleva el ticket, con los datos reales de la venta: empresa y
     * NIT de la configuración, la sucursal, el cajero que la registró, el
     * cliente y el DTE. Antes el ticket de la pantalla los traía inventados.
     */
    @Serdeable
    public record Ticket(Long saleId, String docType, String docNumber, Instant saleDate,
                         String companyName, String legalName, String companyNit, String companyAddress,
                         String companyPhone, String companyEmail,
                         String branchName, String cashier,
                         String clientName, String clientNit, String clientEmail,
                         List<TicketLine> lines, BigDecimal subtotal, BigDecimal discountTotal,
                         BigDecimal taxableBase, BigDecimal tax, BigDecimal taxRate, BigDecimal total,
                         String paymentMethod, boolean credit, TicketFel fel,
                         String status, Instant voidedAt, String voidReason) {}

    @Serdeable
    public record SendTicketRequest(@NotBlank @Email String to) {}
}
