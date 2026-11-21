package com.brim.easyinvoice.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bank statement from /data, kept for reconciliation later. */
@Entity
@Table(name = "bank_statement_lines")
@Getter
@Setter
@NoArgsConstructor
public class BankStatementLine {

    @Id
    private String id;
    private LocalDate postedDate;
    private String description;
    @Column(precision = 14, scale = 2)
    private BigDecimal debit;
    @Column(precision = 14, scale = 2)
    private BigDecimal credit;
}
