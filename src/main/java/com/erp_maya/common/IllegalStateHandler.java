package com.erp_maya.common;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/** Traduce violaciones de reglas de negocio (IllegalStateException) a un 409. */
@Produces
@Singleton
@Requires(classes = {IllegalStateException.class, ExceptionHandler.class})
public class IllegalStateHandler
        implements ExceptionHandler<IllegalStateException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, IllegalStateException e) {
        return HttpResponse.<ApiError>status(io.micronaut.http.HttpStatus.CONFLICT)
                .body(new ApiError(409, "Conflict", e.getMessage()));
    }
}
