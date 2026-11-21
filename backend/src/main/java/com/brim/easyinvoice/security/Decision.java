package com.brim.easyinvoice.security;

public record Decision(boolean allowed, String reason) {

    public static Decision allow() {
        return new Decision(true, null);
    }

    public static Decision deny(String reason) {
        return new Decision(false, reason);
    }
}
