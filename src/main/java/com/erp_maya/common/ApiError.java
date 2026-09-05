package com.erp_maya.common;

import io.micronaut.serde.annotation.Serdeable;

/** Cuerpo JSON estándar para errores de la API. */
@Serdeable
public record ApiError(int status, String error, String message) {
}
