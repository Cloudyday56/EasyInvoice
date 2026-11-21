package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.ApprovalStatus;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Role;

import java.time.OffsetDateTime;

public record ApprovalStepDto(
        Long id,
        String invoiceId,
        int stepNo,
        Role requiredRole,
        String reason,
        String assignedUserId,
        String actingFor,
        ApprovalStatus status,
        String actedBy,
        OffsetDateTime actedAt,
        String comment,
        OffsetDateTime createdAt,
        boolean pending,
        boolean approved) {

    public static ApprovalStepDto from(ApprovalStep s) {
        return new ApprovalStepDto(s.getId(), s.getInvoiceId(), s.getStepNo(), s.getRequiredRole(), s.getReason(),
                s.getAssignedUserId(), s.getActingForId(), s.getStatus(), s.getActedById(), s.getActedAt(),
                s.getComment(), s.getCreatedAt(), s.isPending(), s.isApproved());
    }
}
