package com.erp_maya.salud.service;

/** El rol del usuario no tiene el permiso de Salud que pide la operación. Se responde 403. */
public class SaludForbiddenException extends RuntimeException {

    public SaludForbiddenException(String message) {
        super(message);
    }
}
