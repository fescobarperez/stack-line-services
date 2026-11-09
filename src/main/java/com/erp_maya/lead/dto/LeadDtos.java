package com.erp_maya.lead.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class LeadDtos {

    private LeadDtos() {}

    /**
     * Alta (o actualización, si ya hay uno con el mismo sourceRef). Se exige
     * teléfono o correo en el servicio: sin forma de contactarlo no sirve.
     */
    @Serdeable
    public record Create(
            @NotBlank @Size(max = 150) String name,
            @Nullable @Size(max = 150) String companyName,
            @Nullable @Size(max = 40) String phone,
            @Nullable @Size(max = 150) String email,
            @Nullable @Size(max = 80) String interest,
            @Nullable @Size(max = 1000) String message,
            @Nullable Boolean wantsDemo,
            @Nullable @Size(max = 30) String source,
            @Nullable @Size(max = 120) String sourceRef) {}

    @Serdeable
    public record StatusUpdate(@NotNull String status) {}

    @Serdeable
    public record Response(
            Long id,
            String source,
            String name,
            @Nullable String companyName,
            @Nullable String phone,
            @Nullable String email,
            @Nullable String interest,
            @Nullable String message,
            boolean wantsDemo,
            String status,
            Instant createdAt,
            Instant updatedAt,
            /** true si el aviso por correo salió en esta llamada. */
            boolean notified) {}
}
