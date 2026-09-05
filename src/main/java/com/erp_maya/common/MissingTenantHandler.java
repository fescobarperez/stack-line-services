package com.erp_maya.common;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/** Traduce {@link MissingTenantException} a un 400 (falta el header X-Company-Id). */
@Produces
@Singleton
@Requires(classes = {MissingTenantException.class, ExceptionHandler.class})
public class MissingTenantHandler
        implements ExceptionHandler<MissingTenantException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, MissingTenantException e) {
        return HttpResponse.badRequest(new ApiError(400, "Bad Request", e.getMessage()));
    }
}
