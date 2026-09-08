package com.erp_maya.upload;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTOs para la subida de logo mediante URL prefirmada. */
public class UploadDtos {

    /** Petición del frontend: nombre y tipo del archivo que va a subir. */
    @Serdeable
    public record LogoUploadRequest(
            @NotBlank String fileName,
            @NotBlank String contentType
    ) {}

    /**
     * Respuesta: la URL prefirmada para hacer el PUT directo a S3, la URL
     * pública final donde quedará el objeto, y el content-type que el
     * navegador DEBE usar en el PUT (debe coincidir con el firmado).
     */
    @Serdeable
    public record LogoUploadResponse(
            String uploadUrl,
            String publicUrl,
            String contentType,
            long expiresInSeconds
    ) {}
}
