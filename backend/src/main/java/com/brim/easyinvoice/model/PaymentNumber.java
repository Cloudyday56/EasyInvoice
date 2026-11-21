package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;

/** Each row is one issued payment number (EP-00001 is id 1). The database sequence behind it keeps them unique and ordered. */
@Entity
@Table(name = "payment_numbers")
@Getter
public class PaymentNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_number_seq")
    @SequenceGenerator(name = "payment_number_seq", sequenceName = "payment_number_seq", allocationSize = 1)
    private Long id;
}
