package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "card_transactions")
@Getter
@Setter
@NoArgsConstructor
public class CardTransaction extends BaseEntity<String> {

    @Id
    private String id;
    private String cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cardholder_user_id")
    private User cardholder;

    private LocalDate postedDate;
    private String merchantDescriptor;
    private String mcc;
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
    private String currency;
    @Column(precision = 14, scale = 2)
    private BigDecimal amountCad;
    private String source;          // employee_card | ap_virtual_card
    private Boolean receiptAttached;
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;        // set for virtual-card payments made by this app

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
}
