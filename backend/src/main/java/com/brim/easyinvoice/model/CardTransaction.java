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

@Entity
@Table(name = "card_transactions")
@Getter
@Setter
@NoArgsConstructor
public class CardTransaction {

    @Id
    private String id;
    private String cardId;
    private String cardholderUserId;
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
    private String paymentId;       // set for virtual-card payments made by this app
    private String invoiceId;
}
