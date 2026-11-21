package com.brim.easyinvoice.model;

public enum DocumentType {
    INVOICE, CREDIT_NOTE;

    /** Lenient parse for extractor output ("credit_note", "Credit Note"); anything unknown is an INVOICE. */
    public static DocumentType parse(String raw) {
        if (raw == null) return INVOICE;
        return raw.trim().toUpperCase().replace(' ', '_').equals("CREDIT_NOTE") ? CREDIT_NOTE : INVOICE;
    }
}
