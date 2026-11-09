package com.erp_maya.salud.controller;

import com.erp_maya.common.ApiError;
import com.erp_maya.salud.service.SaludForbiddenException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * 403 con el mismo cuerpo {@link ApiError} que el resto de la API, para que
 * la app muestre el mensaje («Tu rol no tiene permiso para…») y no un
 * «Forbidden» genérico.
 */
@Produces
@Singleton
@Requires(classes = {SaludForbiddenException.class, ExceptionHandler.class})
public class SaludForbiddenHandler implements ExceptionHandler<SaludForbiddenException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, SaludForbiddenException e) {
        return HttpResponse.<ApiError>status(HttpStatus.FORBIDDEN).body(new ApiError(403, "Forbidden", e.getMessage()));
    }
}
