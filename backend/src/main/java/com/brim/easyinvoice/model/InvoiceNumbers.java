package com.brim.easyinvoice.model;

public final class InvoiceNumbers {

    private InvoiceNumbers() {
    }

    /**
     * Canonical form used by the repeat guard: keep only letters and digits, upper-case,
     * drop leading zeros. "inv-00734" -> "INV00734", "000 809" -> "809".
     */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String noLeadingZeros = cleaned.replaceFirst("^0+", "");
        return noLeadingZeros.isEmpty() ? "0" : noLeadingZeros;
    }
}
