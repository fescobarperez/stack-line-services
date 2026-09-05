package com.erp_maya.common;

/** Se lanza cuando un recurso no existe (o no pertenece al inquilino actual). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
