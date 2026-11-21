package com.brim.easyinvoice.model;

import java.time.OffsetDateTime;

public record SourceDocument(
        long id,
        String channel,       // UPLOAD | EMAIL
        String emailId,
        String fileName,      // null when an email had no attachment
        String contentType,
        Long sizeBytes,
        String sha256,
        String storagePath,
        String sender,
        String subject,
        String body,
        OffsetDateTime receivedAt,
        String uploadedBy) {

    public boolean hasFile() {
        return storagePath != null;
    }
}
