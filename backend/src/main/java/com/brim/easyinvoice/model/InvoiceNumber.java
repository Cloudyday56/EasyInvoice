package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;

/** Each row is one issued invoice number (EI-00001 is id 1). The database sequence behind it keeps them unique and ordered. */
@Entity
@Table(name = "invoice_numbers")
@Getter
public class InvoiceNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invoice_number_seq")
    @SequenceGenerator(name = "invoice_number_seq", sequenceName = "invoice_number_seq", allocationSize = 1)
    private Long id;
}
