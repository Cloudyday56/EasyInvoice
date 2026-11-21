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
import java.time.OffsetDateTime;

/** Single-use Brim card locked to one vendor and the exact amount. */
@Entity
@Table(name = "virtual_cards")
@Getter
@Setter
@NoArgsConstructor
public class VirtualCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String paymentId;
    private String maskedNumber;
    @Column(precision = 14, scale = 2)
    private BigDecimal amountLimit;
    private String merchantLock;       // vendor id
    private OffsetDateTime expiresAt;
    private boolean singleUse = true;
    private String status = "ISSUED";  // ISSUED | USED | EXPIRED
}
