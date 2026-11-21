package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.BaseEntity;
import com.brim.easyinvoice.model.Invoice;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** One row of the jobs table: "run this node on this invoice, not before runAfter". */
@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_status_run_after", columnList = "status, run_after"),
        @Index(name = "idx_jobs_invoice", columnList = "invoice_id")})
@Getter
@Setter
@NoArgsConstructor
public class Job extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeName node;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;
    private int attempts;
    private OffsetDateTime runAfter;
    @Column(columnDefinition = "text")
    private String lastError;
    private OffsetDateTime createdAt;

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public String getInvoiceId() {
        return invoice == null ? null : invoice.getId();
    }
}
