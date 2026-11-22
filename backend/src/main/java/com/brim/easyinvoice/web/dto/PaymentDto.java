package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentDto(
        String id,
        String invoiceId,
        String vendorId,
        PaymentMethod method,
        LocalDate scheduledDate,
        LocalDate paidDate,
        String currency,
        BigDecimal amount,
        BigDecimal fxRateToCad,
        BigDecimal amountCad,
        BigDecimal discountTaken,
        String reference,
        PaymentStatus status,
        String sourceStatus,
        String idempotencyKey,
        String preparedBy,
        String releasedBy,
        OffsetDateTime releasedAt,
        OffsetDateTime sentAt,
        String failureReason) {

    public static PaymentDto from(Payment p) {
        return new PaymentDto(p.getId(), p.getInvoiceId(), p.getVendorId(), p.getMethod(), p.getScheduledDate(),
                p.getPaidDate(), p.getCurrency(), p.getAmount(), p.getFxRateToCad(), p.getAmountCad(),
                p.getDiscountTaken(), p.getReference(), p.getStatus(), p.getSourceStatus(), p.getIdempotencyKey(),
                p.getPreparedBy(), p.getReleasedById(), p.getReleasedAt(), p.getSentAt(), p.getFailureReason());
    }
}
