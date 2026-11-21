package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** One received file (upload or email attachment). The bytes live on disk under the SHA-256. */
@Entity
@Table(name = "source_documents")
@Getter
@Setter
@NoArgsConstructor
public class SourceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String channel;          // UPLOAD | EMAIL
    private String emailId;
    private String idempotencyKey;   // email id (or "upload") + ":" + sha256, so the same file is never taken twice
    private String fileName;         // null when an email had no attachment
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String storagePath;
    private String sender;
    private String subject;
    private String body;
    private OffsetDateTime receivedAt;
    private String uploadedBy;

    public boolean hasFile() {
        return storagePath != null;
    }
}
