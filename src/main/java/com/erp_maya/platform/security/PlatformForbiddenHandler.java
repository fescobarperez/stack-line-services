package com.erp_maya.platform.security;

import com.erp_maya.platform.dto.PlatformDtos.PlatformError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
public class PlatformForbiddenHandler implements ExceptionHandler<PlatformForbiddenException, HttpResponse<PlatformError>> {

    @Override
    public HttpResponse<PlatformError> handle(HttpRequest request, PlatformForbiddenException e) {
        return HttpResponse.<PlatformError>status(HttpStatus.FORBIDDEN).body(PlatformError.of("FORBIDDEN", e.getMessage()));
    }
}
