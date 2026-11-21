package com.brim.easyinvoice.model;

import java.time.OffsetDateTime;

public record ApprovalStep(
        long id,
        String invoiceId,
        int stepNo,
        String requiredRole,
        String reason,
        String assignedUserId,
        String actingFor,     // set when assignedUserId is a delegate for someone on leave
        String status,        // PENDING | APPROVED | REJECTED | RETURNED | VOID
        String actedBy,
        OffsetDateTime actedAt,
        String comment) {

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isApproved() {
        return "APPROVED".equals(status);
    }
}
