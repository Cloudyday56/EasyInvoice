package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** severity is FLAG (warning that travels with the invoice) or BLOCK (stopped the line). */
@Entity
@Table(name = "flags")
@Getter
@Setter
@NoArgsConstructor
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String invoiceId;
    private String node;
    private String code;
    private String severity;
    private String message;
    @CreationTimestamp
    private OffsetDateTime createdAt;
    private String overriddenBy;
    private String overrideReason;

    public Flag(String invoiceId, String node, String code, String severity, String message) {
        this.invoiceId = invoiceId;
        this.node = node;
        this.code = code;
        this.severity = severity;
        this.message = message;
    }
}
