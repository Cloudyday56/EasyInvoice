package com.brim.easyinvoice.pipeline;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tunable fraud rules. Values come from app.fraud.* in application.yaml; the defaults also make
 * the node usable in plain unit tests without a Spring context.
 */
@Component
@ConfigurationProperties(prefix = "app.fraud")
@Getter
@Setter
public class FraudScoringConfig {

    /** Below this score the node passes. */
    private int passBelow = 40;

    /** At or above this score the node blocks the invoice in the fraud queue. */
    private int blockAt = 70;

    /** How far back to look for an unverified bank change. */
    private int bankChangeWindowDays = 30;

    /** How many days either side of the invoice date count as a possible split. */
    private int splitWindowDays = 3;

    /** A split candidate must be below this amount. */
    private BigDecimal splitLimitCad = new BigDecimal("10000");

    /** A split candidate's amount must be within this percentage of the invoice. */
    private double splitTolerancePercent = 10;

    /** Need at least this many prior invoices before an amount outlier is meaningful. */
    private int minHistorySamples = 5;

    /** Outlier threshold, in standard deviations above the vendor's average. */
    private double outlierSigma = 3;

    private List<String> freeMailDomains = List.of(
            "gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "icloud.com", "aol.com",
            "proton.me", "protonmail.com", "live.com", "msn.com", "mail.com", "gmail.ca");

    private int bankMismatchWeight = 100;
    private int unverifiedBankChangeWeight = 45;
    private int lookalikeDomainWeight = 45;
    private int senderDomainMismatchWeight = 10;
    private int freeMailDomainWeight = 25;
    private int duplicateInvoiceWeight = 30;
    private int splitInvoiceWeight = 25;
    private int amountOutlierWeight = 20;
}
