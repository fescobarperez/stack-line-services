package com.erp_maya.common;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/** Traduce {@link ResourceNotFoundException} a un 404 con cuerpo {@link ApiError}. */
@Produces
@Singleton
@Requires(classes = {ResourceNotFoundException.class, ExceptionHandler.class})
public class ResourceNotFoundHandler
        implements ExceptionHandler<ResourceNotFoundException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, ResourceNotFoundException e) {
        return HttpResponse.notFound(new ApiError(404, "Not Found", e.getMessage()));
    }
}
