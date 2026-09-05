package com.erp_maya.common;

/** Se lanza cuando un request a /api/** no trae un tenant resuelto desde el JWT. */
public class MissingTenantException extends RuntimeException {
    public MissingTenantException() {
        super("Sesión inválida: el token no contiene la empresa (companyId).");
    }
}
