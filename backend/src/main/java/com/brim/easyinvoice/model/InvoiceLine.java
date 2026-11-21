package com.brim.easyinvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String invoiceId;
    private int lineNo;
    @Column(columnDefinition = "text")
    private String description;
    @Column(precision = 14, scale = 4)
    private BigDecimal quantity;
    @Column(precision = 14, scale = 4)
    private BigDecimal unitPrice;
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
    private String glAccount;
    private String departmentId;
    private String gstHstCode;
    private String pstQstCode;
    private String lineType;
}
