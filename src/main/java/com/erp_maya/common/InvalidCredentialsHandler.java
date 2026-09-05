package com.erp_maya.common;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/** Traduce {@link InvalidCredentialsException} a un 401 (credenciales inválidas). */
@Produces
@Singleton
@Requires(classes = {InvalidCredentialsException.class, ExceptionHandler.class})
public class InvalidCredentialsHandler
        implements ExceptionHandler<InvalidCredentialsException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, InvalidCredentialsException e) {
        return HttpResponse.<ApiError>status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(401, "Unauthorized", e.getMessage()));
    }
}
