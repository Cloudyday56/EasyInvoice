package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One received file (upload or email attachment). The bytes live on disk under the SHA-256. */
@Entity
@Table(name = "source_documents")
@Getter
@Setter
@NoArgsConstructor
public class SourceDocument extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentChannel channel;
    private String emailId;
    @Column(unique = true, nullable = false)
    private String idempotencyKey;   // email id (or "upload") + ":" + sha256, so the same file is never taken twice
    private String fileName;         // null when an email had no attachment
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    @Column(columnDefinition = "text")
    private String storagePath;
    private String sender;
    private String subject;
    @Column(columnDefinition = "text")
    private String body;
    private OffsetDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    public boolean hasFile() {
        return storagePath != null;
    }
}
