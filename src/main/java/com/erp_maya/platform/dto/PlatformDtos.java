package com.erp_maya.platform.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** Contrato de /api/platform (ver API.md de stack-line-gestion-ui). */
public final class PlatformDtos {

    private PlatformDtos() {}

    @Serdeable
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    @Serdeable
    public record OperatorInfo(Long id, String name, String email, String role) {}

    @Serdeable
    public record LoginResponse(String token, OperatorInfo operator) {}

    /** Error de la consola: `code` estable para la UI, `message` para la persona. */
    @Serdeable
    public record PlatformError(String code, String message, @Nullable Map<String, String> fields) {
        public static PlatformError of(String code, String message) {
            return new PlatformError(code, message, null);
        }
    }
}
