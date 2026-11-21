package com.brim.easyinvoice.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** status: SCHEDULED -> RELEASED -> SENT -> SETTLED, or FAILED. */
public record Payment(
        String id,
        String invoiceId,
        String vendorId,
        String method,
        LocalDate scheduledDate,
        LocalDate paidDate,
        String currency,
        BigDecimal amount,
        BigDecimal amountCad,
        String reference,
        String status,
        String preparedBy,
        String releasedBy,
        OffsetDateTime releasedAt,
        OffsetDateTime sentAt,
        String failureReason) {
}
