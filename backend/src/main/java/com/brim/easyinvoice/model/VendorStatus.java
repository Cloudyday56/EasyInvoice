package com.brim.easyinvoice.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Stored as the constant name; JSON and the CSV use the lower-case form ("pending_review"). */
public enum VendorStatus {
    ACTIVE, PENDING_REVIEW, ON_HOLD, BLOCKED;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
