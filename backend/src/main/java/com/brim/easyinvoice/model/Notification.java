package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(columnDefinition = "text")
    private String message;
    @Column(name = "is_read")
    private boolean read;
    @org.hibernate.annotations.CreationTimestamp
    private OffsetDateTime createdAt;
}
