package com.erp_maya.catalog.controller;

import com.erp_maya.catalog.dto.ProductImageDtos;
import com.erp_maya.catalog.service.ProductImageService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.security.Principal;

/**
 * Foto del producto. GET admite ?size=thumb (miniatura) y ?v=<imageVersion>:
 * con la versión en la URL la respuesta se puede cachear sin vencimiento,
 * porque una foto nueva cambia la versión.
 */
@Controller("/api/products")
public class ProductImageController {

    private final ProductImageService images;

    public ProductImageController(ProductImageService images) {
        this.images = images;
    }

    @Put("/{id}/image")
    public ProductImageDtos.Saved upload(Long id, @Valid @Body ProductImageDtos.Upload request, @Nullable Principal auth) {
        return images.save(id, request, auth != null ? auth.getName() : null);
    }

    @Delete("/{id}/image")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        images.delete(id);
    }

    @Get("/{id}/image")
    public HttpResponse<?> image(Long id, @Nullable @QueryValue String size, @Nullable @QueryValue Long v,
                                 HttpRequest<?> request) {
        return images.image(id, size)
                .<HttpResponse<?>>map(img -> {
                    String etag = "\"" + img.version() + (ProductImageService.THUMB.equalsIgnoreCase(size) ? "-t" : "-f") + "\"";
                    String cache = v != null && v == img.version()
                            ? "private, max-age=31536000, immutable"
                            : "private, no-cache";
                    if (etag.equals(request.getHeaders().get(HttpHeaders.IF_NONE_MATCH))) {
                        return HttpResponse.notModified().header(HttpHeaders.ETAG, etag).header(HttpHeaders.CACHE_CONTROL, cache);
                    }
                    return HttpResponse.ok(img.bytes())
                            .contentType(img.mime())
                            .header(HttpHeaders.ETAG, etag)
                            .header(HttpHeaders.CACHE_CONTROL, cache);
                })
                .orElseGet(HttpResponse::notFound);
    }
}
