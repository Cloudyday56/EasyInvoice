package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "approval_steps", indexes = {
        @Index(name = "idx_approval_steps_invoice", columnList = "invoice_id"),
        @Index(name = "idx_approval_steps_assignee", columnList = "assigned_user_id")})
@Getter
@Setter
@NoArgsConstructor
public class ApprovalStep extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    private int stepNo;
    @Enumerated(EnumType.STRING)
    private Role requiredRole;       // APPROVER | ADMIN
    @Column(columnDefinition = "text")
    private String reason;           // why this step exists (policy, flag, escalation)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_for")
    private User actingFor;          // original approver, when assignedUser is a delegate

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acted_by")
    private User actedBy;
    private OffsetDateTime actedAt;
    @Column(columnDefinition = "text")
    private String comment;
    private OffsetDateTime createdAt;   // the seed sets it from the approval time

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    // Id shortcuts: read the foreign key without loading the related row (safe on a lazy reference).

    public String getInvoiceId() {
        return invoice == null ? null : invoice.getId();
    }

    public String getAssignedUserId() {
        return assignedUser == null ? null : assignedUser.getId();
    }

    public String getActedById() {
        return actedBy == null ? null : actedBy.getId();
    }

    public String getActingForId() {
        return actingFor == null ? null : actingFor.getId();
    }
}
