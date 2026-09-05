package com.erp_maya.security.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.inject.Singleton;

/** Hashing y verificación de passwords con bcrypt (coste 12). */
@Singleton
public class PasswordEncoder {

    private static final int COST = 12;

    public String encode(String raw) {
        return BCrypt.withDefaults().hashToString(COST, raw.toCharArray());
    }

    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null || hash.isBlank()) {
            return false;
        }
        return BCrypt.verifyer().verify(raw.toCharArray(), hash).verified;
    }
}
