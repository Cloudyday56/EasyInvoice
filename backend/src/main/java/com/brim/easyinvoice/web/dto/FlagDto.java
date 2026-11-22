package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.FlagSeverity;

import java.time.OffsetDateTime;

public record FlagDto(
        Long id,
        String invoiceId,
        String node,
        String code,
        FlagSeverity severity,
        String message,
        OffsetDateTime createdAt,
        String overriddenBy,
        String overrideReason) {

    public static FlagDto from(Flag f) {
        return new FlagDto(f.getId(), f.getInvoiceId(), f.getNode(), f.getCode(), f.getSeverity(), f.getMessage(),
                f.getCreatedAt(), f.getOverriddenById(), f.getOverrideReason());
    }
}
