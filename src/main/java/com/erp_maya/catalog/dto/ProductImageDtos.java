package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** Subida de la foto del producto. El navegador ya la mandó reducida. */
public final class ProductImageDtos {

    private ProductImageDtos() {
    }

    /** Las dos versiones en base64 (sin el prefijo data:). */
    @Serdeable
    public record Upload(@NotBlank String imageBase64, @NotBlank String imageMime,
                         @NotBlank String thumbBase64, @NotBlank String thumbMime) {
    }

    /** imageVersion: lo que hay que poner en ?v= para pedirla. */
    @Serdeable
    public record Saved(Long productId, Long imageVersion) {
    }
}
