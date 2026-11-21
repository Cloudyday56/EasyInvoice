package com.brim.easyinvoice.security;

/** Turned into HTTP 403 by ApiExceptionHandler. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String reason) {
        super(reason);
    }
}
