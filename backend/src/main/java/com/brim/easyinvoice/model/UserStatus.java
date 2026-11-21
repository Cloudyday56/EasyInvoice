package com.brim.easyinvoice.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Stored as the constant name; JSON and the CSV use the lower-case form ("on_leave"). */
public enum UserStatus {
    ACTIVE, ON_LEAVE, TERMINATED;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
