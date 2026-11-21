package com.brim.easyinvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.PrePersist;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** The invoice is a state machine: only InvoiceStateMachine changes status and the hold fields. */
@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(
        name = "uq_vendor_invoice_number", columnNames = {"vendorId", "canonicalInvoiceNumber"}))
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    private String id;
    private Long sourceDocumentId;
    private String documentType = "INVOICE";   // INVOICE | CREDIT_NOTE
    private String vendorId;
    private String vendorNameRaw;              // vendor name as read from the document
    private String vendorInvoiceNumber;
    // Repeat guard: one vendor cannot bill the same canonical number twice (upper-case, no spaces/dashes/leading zeros)
    private String canonicalInvoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate receivedDate;
    private LocalDate dueDate;
    private String currency;
    @Column(precision = 14, scale = 2)
    private BigDecimal subtotal;
    @Column(precision = 14, scale = 2)
    private BigDecimal gstHstAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal pstQstAmount;
    @Column(precision = 14, scale = 2)
    private BigDecimal total;
    @Column(precision = 10, scale = 4)
    private BigDecimal fxRateToCad;
    @Column(precision = 14, scale = 2)
    private BigDecimal totalCad;
    private String departmentId;
    private String requesterUserId;
    private String poId;
    private String contractId;
    private String shipToProvince;
    private Integer riskScore;
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;
    private String sourceStatus;               // status as written in invoices.csv (history only)
    private String holdQueue;                  // set when status = ON_HOLD
    @Column(columnDefinition = "text")
    private String holdReason;
    private String resumeNode;                 // node the invoice re-enters at once the hold is cleared
    private String currentNode;                // last node that ran
    private OffsetDateTime createdAt;          // the seed sets it from received_date
    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
