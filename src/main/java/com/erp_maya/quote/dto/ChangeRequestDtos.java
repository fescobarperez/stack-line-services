package com.erp_maya.quote.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Solicitudes de cambio del cliente sobre una cotización que ya tomó un vendedor. */
public final class ChangeRequestDtos {

    private ChangeRequestDtos() {}

    /** Lo que registra el asistente (o un vendedor a mano). */
    @Serdeable
    public record CreateRequest(@NotBlank String kind, Long productId, String productName,
                                BigDecimal quantity, BigDecimal discountPct, String detail,
                                String source, String requestedBy,
                                /** Solo para kind = consulta: la solicitud sobre la que pregunta. */
                                Long parentId) {}

    @Serdeable
    public record Response(Long id, String kind, Long productId, String productName,
                           BigDecimal quantity, BigDecimal discountPct, String detail,
                           String status, String response,
                           BigDecimal adjustedQuantity, BigDecimal adjustedDiscountPct,
                           String source, String requestedBy, String resolvedBy, Instant resolvedAt,
                           Instant createdAt, String summary,
                           String reasonCode, String reasonLabel,
                           /** Lo que se le dijo al cliente: explicación del motivo + comentario del vendedor. */
                           String clientMessage,
                           Long parentId) {}

    /**
     * Un motivo del catálogo. {@code clientText} es lo que se le explica al
     * cliente; si es nulo (p. ej. 'otro'), el comentario del vendedor es
     * obligatorio y es la explicación.
     */
    @Serdeable
    public record Reason(String code, String label, String clientText) {}

    /**
     * Lo que decide el vendedor sobre una solicitud.
     * decision: aceptar | ajustar | rechazar | responder.
     */
    @Serdeable
    public record Decision(@NotNull Long id, @NotBlank String decision, String response,
                           BigDecimal adjustedQuantity, BigDecimal adjustedDiscountPct,
                           /** Obligatorio al rechazar o ajustar. */
                           String reasonCode) {}

    @Serdeable
    public record ApplyRequest(String actor, @NotEmpty @Valid List<Decision> decisions) {}

    @Serdeable
    public record ApplyResult(QuoteDtos.Response quote, List<Response> requests, boolean clientNotified) {}

    /** Lo que se le dice al cliente: lo arma el ERP y agents-services solo lo formatea. */
    @Serdeable
    public record NotificationItem(Long requestId, String kind, String status, String detail, String requested, String response) {}

    @Serdeable
    public record ChangesAppliedPayload(Long quoteId, String docNumber, String clientName,
                                        String total, String pdfPath, List<NotificationItem> results,
                                        /** Sigue 'enviada' y sin cambios de líneas: se le vuelve a pedir su decisión. */
                                        boolean askDecision,
                                        /** Estaba 'enviada' y cambió: volvió a borrador y viene una versión nueva. */
                                        boolean newVersionPending,
                                        int version) {}
}
