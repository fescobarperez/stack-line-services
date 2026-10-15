package com.erp_maya.platform.security;

import io.micronaut.http.HttpStatus;

import java.util.Map;

/**
 * Error del contrato de la consola: estado HTTP + `code` estable + mensaje,
 * y `fields` cuando es de validación. Lo traduce {@link PlatformExceptionHandler}.
 */
public class PlatformException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public PlatformException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public static PlatformException validation(Map<String, String> fields) {
        return new PlatformException(HttpStatus.BAD_REQUEST, "VALIDATION", "Revisa los campos marcados", fields);
    }

    public static PlatformException conflict(String code, String message, Map<String, String> fields) {
        return new PlatformException(HttpStatus.CONFLICT, code, message, fields);
    }

    public static PlatformException notFound(String message) {
        return new PlatformException(HttpStatus.NOT_FOUND, "NOT_FOUND", message, null);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFields() { return fields; }
}
