package com.erp_maya.platform.security;

import com.erp_maya.platform.dto.PlatformDtos.PlatformError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
public class PlatformExceptionHandler implements ExceptionHandler<PlatformException, HttpResponse<PlatformError>> {

    @Override
    public HttpResponse<PlatformError> handle(HttpRequest request, PlatformException e) {
        return HttpResponse.<PlatformError>status(e.getStatus())
                .body(new PlatformError(e.getCode(), e.getMessage(), e.getFields()));
    }
}
