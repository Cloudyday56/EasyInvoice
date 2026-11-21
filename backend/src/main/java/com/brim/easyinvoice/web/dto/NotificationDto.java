package com.brim.easyinvoice.web.dto;

import com.brim.easyinvoice.model.Notification;

import java.time.OffsetDateTime;

public record NotificationDto(Long id, String userId, String template, String invoiceId, String message,
                              boolean read, OffsetDateTime createdAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getUser().getId(), n.getTemplate(),
                n.getInvoice() == null ? null : n.getInvoice().getId(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
