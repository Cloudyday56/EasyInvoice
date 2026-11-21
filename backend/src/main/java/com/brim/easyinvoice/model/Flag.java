package com.brim.easyinvoice.model;

import java.time.OffsetDateTime;

/** severity is FLAG (warning that travels with the invoice) or BLOCK (stopped the line). */
public record Flag(
        long id,
        String invoiceId,
        String node,
        String code,
        String severity,
        String message,
        OffsetDateTime createdAt,
        String overriddenBy,
        String overrideReason) {
}
