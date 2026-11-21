package com.brim.easyinvoice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** bank_* columns are never sent to the UI: the vendor lookup endpoint returns a slimmer record. */
@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
public class Vendor {

    @Id
    private String id;
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
    private String status;            // active | pending_review | on_hold | blocked
    private LocalDate onboardedAt;

    public boolean isActive() {
        return "active".equals(status);
    }
}
