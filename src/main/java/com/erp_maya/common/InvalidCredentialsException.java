package com.erp_maya.common;

/** Se lanza cuando el login falla (empresa/usuario/password inválidos o inactivos). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
