package com.erp_maya.upload;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

/** Subida de archivos del inquilino actual (logos de empresa) vía S3 prefirmado. */
@Controller("/api/uploads")
public class UploadController {

    private final S3UploadService service;

    public UploadController(S3UploadService service) {
        this.service = service;
    }

    /**
     * Emite una URL prefirmada para subir un logo directo a S3.
     * El frontend luego hace PUT a uploadUrl y guarda publicUrl en company.logo_url.
     */
    @Post("/logo-url")
    public UploadDtos.LogoUploadResponse logoUrl(@Valid @Body UploadDtos.LogoUploadRequest request) {
        return service.presignLogoUpload(request);
    }
}
