package com.brim.easyinvoice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** bank_* columns are never sent to the UI: the vendor lookup endpoint returns a slimmer record. */
@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
public class Vendor extends BaseEntity<String> {

    @Id
    private String id;
    @Column(nullable = false)
    private String legalName;
    private String category;
    private String defaultGlAccount;
    private String address;
    private String provinceOrState;
    private String country;
    private String gstHstNumber;
    private String qstNumber;
    private Integer paymentTermsDays;
    private String earlyPayDiscount;
    private String preferredPaymentMethod;
    private boolean acceptsCardPayment;
    private String bankInstitution;
    private String bankTransit;
    private String bankAccount;
    private LocalDate bankDetailsUpdatedAt;
    private String apContactEmail;
    private boolean requiresPo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus status = VendorStatus.ACTIVE;
    private LocalDate onboardedAt;

    public boolean isActive() {
        return status == VendorStatus.ACTIVE;
    }
}
