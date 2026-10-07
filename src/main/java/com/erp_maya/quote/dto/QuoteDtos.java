package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QuoteDtos {

    private QuoteDtos() {}

    /** Renglón. productId opcional: si no viene, se usa itemName (texto libre). */
    @Serdeable
    public record ItemRequest(Long productId, String itemName, String uom,
                              @NotNull BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount) {}

    @Serdeable
    public record Request(String docNumber, String partyType,
                          Long projectId,
                          Long clientId, String clientName, String clientNit, String clientEmail, String clientContact,
                          String supplierName, String supplierNit, String supplierEmail, String supplierContact,
                          LocalDate quoteDate, LocalDate validUntil, LocalDate deadline,
                          String leadTime, String paymentTerms, String createdBy, String notes,
                          String profitCalcType, BigDecimal profitValue,
                          @NotEmpty @Valid List<ItemRequest> items,
                          // Solo el asistente los manda; con origin="agente" nace como 'prospecto'.
                          String origin, String channel, String conversationRef) {}

    @Serdeable
    /**
     * Renglón a conservar (id) o nuevo (id nulo, con productId o description).
     * Los que no vengan se eliminan.
     */
    public record UpdateItemRequest(Long id, String description,
                                    @NotNull BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount,
                                    Long productId, String uom) {}

    @Serdeable
    public record UpdateRequest(LocalDate validUntil, String notes,
                                String profitCalcType, BigDecimal profitValue,
                                @NotEmpty @Valid List<UpdateItemRequest> items) {}

    @Serdeable
    public record ItemResponse(Long id, Long productId, String productName, String uom, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {}

    @Serdeable
    public record HistoryEntry(Long id, String action, String actor, Instant timestamp) {}

    @Serdeable
    public record Response(Long id, String docNumber, String partyType,
                           Long clientId, String clientName, String clientNit, String clientEmail, String clientContact,
                           String supplierName, String supplierNit, String supplierEmail, String supplierContact,
                           LocalDate quoteDate, LocalDate validUntil, LocalDate deadline,
                           String leadTime, String paymentTerms, String createdBy,
                           Long projectId,
                           BigDecimal subtotal, BigDecimal tax, BigDecimal taxRate,
                           String profitCalcType, BigDecimal profitValue, BigDecimal profitAmount,
                           BigDecimal total, String status, String notes,
                           List<ItemResponse> items, List<HistoryEntry> history,
                           String origin, String channel, String conversationRef) {}

    /** Cambio de estado (enviar/aprobar/rechazar/convertir). */
    @Serdeable
    public record StatusRequest(@NotNull String status, String note, String actor) {}
}
