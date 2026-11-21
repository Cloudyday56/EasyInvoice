package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "invoice_lines", indexes = @Index(name = "idx_invoice_lines_invoice", columnList = "invoice_id"))
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    private int lineNo;
    @Column(columnDefinition = "text")
    private String description;
    @Column(precision = 14, scale = 4)
    private BigDecimal quantity;
    @Column(precision = 14, scale = 4)
    private BigDecimal unitPrice;
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account")
    private GlAccount glAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    private String gstHstCode;
    private String pstQstCode;
    private String lineType;
}
