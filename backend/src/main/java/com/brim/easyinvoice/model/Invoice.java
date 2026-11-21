package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The invoice is a state machine: only InvoiceStateMachine changes status and the hold fields.
 * Lines, approval steps and flags belong to the invoice (deleting it deletes them); payments are
 * financial records, so they are only listed here and are never cascaded.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uq_vendor_invoice_number",
                columnNames = {"vendor_id", "canonical_invoice_number"}),
        indexes = {
                @Index(name = "idx_invoices_status", columnList = "status"),
                @Index(name = "idx_invoices_vendor", columnList = "vendor_id"),
                @Index(name = "idx_invoices_department", columnList = "department_id"),
                @Index(name = "idx_invoices_requester", columnList = "requester_user_id")})
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity<String> {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private SourceDocument sourceDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType = DocumentType.INVOICE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;                     // null until Vendor match finds it
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private User requester;

    private String poNumberRaw;                // PO number as read from the document (may not exist)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id")
    private PurchaseOrder purchaseOrder;       // set once the number matches a real PO

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    private String shipToProvince;
    private Integer riskScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo, id")
    private List<ApprovalStep> approvalSteps = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Flag> flags = new ArrayList<>();

    @OneToMany(mappedBy = "invoice")
    @OrderBy("id")
    private List<Payment> payments = new ArrayList<>();

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    // Helpers that keep both sides of the relationship in step.

    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }

    /** Replaces every line (a node re-run produces the full set again). */
    public void replaceLines(List<InvoiceLine> newLines) {
        lines.clear();
        newLines.forEach(this::addLine);
    }

    public void addApprovalStep(ApprovalStep step) {
        step.setInvoice(this);
        approvalSteps.add(step);
    }

    public void addFlag(Flag flag) {
        flag.setInvoice(this);
        flags.add(flag);
    }

    // Id shortcuts: read the foreign key without loading the related row (safe on a lazy reference).

    public String getVendorId() {
        return vendor == null ? null : vendor.getId();
    }

    public String getDepartmentId() {
        return department == null ? null : department.getId();
    }

    public String getRequesterId() {
        return requester == null ? null : requester.getId();
    }

    public Long getSourceDocumentId() {
        return sourceDocument == null ? null : sourceDocument.getId();
    }
}
