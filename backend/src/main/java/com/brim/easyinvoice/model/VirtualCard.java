package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Single-use Brim card locked to one vendor and the exact amount. */
@Entity
@Table(name = "virtual_cards")
@Getter
@Setter
@NoArgsConstructor
public class VirtualCard extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    private String maskedNumber;
    @Column(precision = 14, scale = 2)
    private BigDecimal amountLimit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_lock")
    private Vendor merchantLock;       // the one vendor this card can pay

    private OffsetDateTime expiresAt;
    private boolean singleUse = true;
    private String status = "ISSUED";  // ISSUED | USED | EXPIRED
}
