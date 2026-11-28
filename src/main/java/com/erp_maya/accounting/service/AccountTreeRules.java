package com.erp_maya.accounting.service;

/**
 * Reglas del árbol del plan de cuentas: el árbol tiene que estar completo.
 *
 * Una cuenta de nivel 1 (clase) es raíz y no lleva padre. Una de nivel N > 1
 * cuelga de una de nivel N − 1: no existe un nivel 2 sin su nivel 1, ni un
 * nivel 4 sin su nivel 3. Sin esto los estados financieros no pueden sumar
 * por grupo (Activo corriente, Pasivo…) y el catálogo deja de ser un árbol.
 *
 * Lógica pura, sin base de datos, para probarla sola.
 */
public final class AccountTreeRules {

    public static final int NIVEL_MAXIMO = 9;

    private AccountTreeRules() {}

    /**
     * Qué está mal con el nivel y la cuenta padre de una cuenta, o null si
     * está bien.
     *
     * @param nivel        el nivel que se quiere guardar
     * @param padreCodigo  el código de la cuenta padre, o null si no lleva
     * @param padreNivel   el nivel de la cuenta padre, o null si no lleva
     */
    public static String validar(Integer nivel, String padreCodigo, Integer padreNivel) {
        if (nivel == null || nivel < 1 || nivel > NIVEL_MAXIMO) {
            return "El nivel de la cuenta debe estar entre 1 y " + NIVEL_MAXIMO + ".";
        }
        boolean tienePadre = padreCodigo != null;
        if (nivel == 1) {
            return tienePadre ? "Una cuenta de nivel 1 (clase) es raíz: no lleva cuenta padre." : null;
        }
        if (!tienePadre) {
            return "Una cuenta de nivel " + nivel + " necesita su cuenta padre de nivel " + (nivel - 1)
                    + ": el árbol del plan de cuentas tiene que estar completo.";
        }
        if (padreNivel == null || padreNivel != nivel - 1) {
            return "La cuenta padre " + padreCodigo + " es de nivel " + padreNivel + "; una cuenta de nivel "
                    + nivel + " cuelga de una de nivel " + (nivel - 1) + ".";
        }
        return null;
    }
}
