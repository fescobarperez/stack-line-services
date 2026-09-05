package com.erp_maya.authorization.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AuthorizationDtos {

    /**
     * Consulta previa: ¿esto requiere autorización? El consumidor NUNCA decide
     * por su cuenta; siempre pregunta. Así añadir un caso es configuración.
     */
    @Serdeable
    public record EvaluateRequest(@NotBlank String type, Long branchId,
                                  BigDecimal amount, String currency, BigDecimal percent) {}

    @Serdeable
    public record EvaluateResponse(boolean required, Long ruleId,
                                   Long levelId, String levelName, Short levelRank,
                                   String resolutionMode, String reason) {}

    /** Alta de solicitud. `approverEmail`/`approverPassword` solo en modo PIN. */
    @Serdeable
    public record CreateRequest(@NotBlank String type, Long branchId,
                                BigDecimal amount, String currency, BigDecimal percent,
                                String payload, String reference,
                                String approverEmail, String approverPassword) {}

    @Serdeable
    public record ResolveRequest(@NotNull String decision, String comment,
                                 String approverEmail, String approverPassword) {}

    @Serdeable
    public record StepResponse(Long id, Long approverId, String approverName,
                               String decision, String comment, Instant createdAt) {}

    @Serdeable
    public record Response(Long id, String type, String typeName, Long ruleId,
                           Long requestedBy, String requestedByName, Long branchId,
                           BigDecimal amount, String currency, BigDecimal percent,
                           String payload, String status, String resolutionMode,
                           String reference, Long levelId, String levelName,
                           Instant createdAt, Instant resolvedAt,
                           List<StepResponse> steps) {}
}
