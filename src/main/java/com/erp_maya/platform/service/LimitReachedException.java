package com.erp_maya.platform.service;

/** Se pasaría del límite del plan: 409 LIMIT_REACHED con el límite y su valor. */
public class LimitReachedException extends RuntimeException {

    private final String limit;
    private final int value;

    public LimitReachedException(String limit, int value, String message) {
        super(message);
        this.limit = limit;
        this.value = value;
    }

    public String getLimit() { return limit; }
    public int getValue() { return value; }
}
