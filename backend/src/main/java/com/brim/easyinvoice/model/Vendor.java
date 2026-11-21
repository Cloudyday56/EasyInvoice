package com.brim.easyinvoice.model;

public record Vendor(
        String id,
        String legalName,
        String category,
        String defaultGlAccount,
        String status,            // active | pending_review | on_hold | blocked
        boolean acceptsCardPayment,
        Integer paymentTermsDays,
        String apContactEmail) {

    public boolean isActive() {
        return "active".equals(status);
    }
}
