package com.erp_maya.platform.security;

/** El operador está autenticado pero su rol no alcanza: 403 FORBIDDEN. */
public class PlatformForbiddenException extends RuntimeException {
    public PlatformForbiddenException(String message) {
        super(message);
    }
}
