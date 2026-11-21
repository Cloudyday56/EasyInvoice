package com.brim.easyinvoice.pipeline;

import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** One row of the jobs table: "run this node on this invoice, not before runAfter". */
@Entity
@Table(name = "jobs", indexes = @Index(columnList = "status, runAfter"))
@Getter
@Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String invoiceId;
    @Enumerated(EnumType.STRING)
    private NodeName node;
    private String status = "PENDING";   // PENDING | RUNNING | DONE | FAILED
    private int attempts;
    private OffsetDateTime runAfter;
    @Column(columnDefinition = "text")
    private String lastError;
    private OffsetDateTime createdAt;
}
