package com.brim.easyinvoice.notify;

import java.time.OffsetDateTime;

public record Notification(long id, String userId, String template, String invoiceId, String message,
                           boolean read, OffsetDateTime createdAt) {
}
