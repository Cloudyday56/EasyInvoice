package com.brim.easyinvoice.model;

/** SCHEDULED -> RELEASED -> SENT -> SETTLED, or FAILED. */
public enum PaymentStatus {
    SCHEDULED, RELEASED, SENT, SETTLED, FAILED
}
