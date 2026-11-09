package com.erp_maya.platform.security;

import com.erp_maya.platform.service.LimitReachedException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

@Produces
@Singleton
public class LimitReachedHandler implements ExceptionHandler<LimitReachedException, HttpResponse<LimitReachedHandler.LimitError>> {

    /** Mismo `status/error/message` que ApiError, más lo que la app necesita para explicarlo. */
    @Serdeable
    public record LimitError(int status, String error, String message, String code, String limit, int value) {}

    @Override
    public HttpResponse<LimitError> handle(HttpRequest request, LimitReachedException e) {
        return HttpResponse.<LimitError>status(HttpStatus.CONFLICT)
                .body(new LimitError(409, "Conflict", e.getMessage(), "LIMIT_REACHED", e.getLimit(), e.getValue()));
    }
}
