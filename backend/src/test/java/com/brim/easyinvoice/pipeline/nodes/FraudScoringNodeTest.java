package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.BankChangeRequest;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.FlagSeverity;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.pipeline.FlagDraft;
import com.brim.easyinvoice.pipeline.FraudScoringConfig;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.Outcome;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.BankChangeRequestRepository;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Unit tests for the weighted fraud signals: no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class FraudScoringNodeTest {

    @Mock private InvoiceRepository invoices;
    @Mock private BankChangeRequestRepository bankChanges;
    @Mock private FlagRepository flags;

    private FraudScoringConfig config;
    private FraudScoringNode node;
    private Vendor vendor;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        config = new FraudScoringConfig();
        vendor = new Vendor();
        vendor.setId("V1");
        vendor.setLegalName("Acme Corp");
        vendor.setApContactEmail("ap@acmecorp.com");
        vendor.setBankInstitution("RBC");
        vendor.setBankTransit("00001");
        vendor.setBankAccount("123456789");

        invoice = new Invoice();
        invoice.setId("I1");
        invoice.setVendor(vendor);
        invoice.setInvoiceDate(LocalDate.of(2026, 1, 15));
        invoice.setTotalCad(new BigDecimal("50000")); // above the split limit
        invoice.setCanonicalInvoiceNumber("INV-001");

        // Mockito returns empty lists for repository list methods by default; individual tests
        // stub only the queries needed for their signal.

        node = new FraudScoringNode(config, invoices, bankChanges, flags);
    }

    @Test
    void cleanInvoicePassesWithZeroScoreAndNoFlags() {
        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(0);
        assertThat(result.flags()).isEmpty();
        assertThat(result.holdQueue()).isNull();
    }

    @Test
    void bankDetailsMismatchBlocksImmediatelyEvenWhenThresholdsAreHigher() {
        config.setPassBelow(200);
        config.setBlockAt(200);
        invoice.setRemitBankAccount("999999");

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.BLOCK);
        assertThat(result.holdQueue()).isEqualTo(HoldQueue.FRAUD_HOLD);
        assertThat(result.reasonCode()).isEqualTo("FRAUD_RISK");
        assertThat(result.patch().get("riskScore")).isEqualTo(100);
        assertThat(codes(result)).contains("BANK_DETAILS_MISMATCH");
    }

    @Test
    void matchingBankDetailsDoNotSignal() {
        invoice.setRemitBankInstitution("RBC");
        invoice.setRemitBankTransit("00001");
        invoice.setRemitBankAccount("123-456789"); // punctuation is normalised away

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(0);
    }

    @Test
    void unverifiedBankChangeFlagsTheInvoice() {
        when(bankChanges.findByVendor_IdAndChangedAtGreaterThanEqualOrderByChangedAtDesc(anyString(), any(LocalDate.class)))
                .thenReturn(List.of(change("PENDING", false)));

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.FLAG);
        assertThat(result.patch().get("riskScore")).isEqualTo(45);
        assertThat(codes(result)).containsExactly("UNVERIFIED_BANK_CHANGE");
    }

    @Test
    void verifiedBankChangeDoesNotSignal() {
        when(bankChanges.findByVendor_IdAndChangedAtGreaterThanEqualOrderByChangedAtDesc(anyString(), any(LocalDate.class)))
                .thenReturn(List.of(change("VERIFIED", true)));

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(0);
    }

    @Test
    void freeMailSenderPassesWithWarningFlags() {
        StageResult result = node.run(invoice, context("Alice <alice@gmail.com>"));

        // 25 (free mail) + 10 (other domain) = 35 -> below the FLAG threshold, but flags still travel.
        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(35);
        assertThat(codes(result)).containsExactlyInAnyOrder("FREE_MAIL_DOMAIN", "SENDER_DOMAIN_MISMATCH");
    }

    @Test
    void lookalikeSenderDomainFlagsTheInvoice() {
        StageResult result = node.run(invoice, context("billing@acmecorp.co"));

        assertThat(result.outcome()).isEqualTo(Outcome.FLAG);
        assertThat(result.patch().get("riskScore")).isEqualTo(45);
        assertThat(codes(result)).containsExactly("LOOKALIKE_SENDER_DOMAIN");
    }

    @ParameterizedTest
    @ValueSource(ints = {39, 40, 69, 70})
    void duplicateInvoiceNumberRespectsThresholds(int weight) {
        config.setDuplicateInvoiceWeight(weight);
        when(invoices.existsByVendor_IdAndCanonicalInvoiceNumberAndIdNot("V1", "INV-001", "I1"))
                .thenReturn(true);

        StageResult result = node.run(invoice, context(null));
        Outcome expected = weight >= 70 ? Outcome.BLOCK : weight >= 40 ? Outcome.FLAG : Outcome.PASS;

        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.patch().get("riskScore")).isEqualTo(weight);
        assertThat(codes(result)).contains("DUPLICATE_INVOICE_NUMBER");
        if (expected == Outcome.BLOCK) {
            assertThat(result.holdQueue()).isEqualTo(HoldQueue.FRAUD_HOLD);
        }
    }

    @Test
    void splitInvoiceSignalFiresForSimilarNearbyAmounts() {
        invoice.setTotalCad(new BigDecimal("5000"));
        Invoice sibling = new Invoice();
        sibling.setId("I2");
        sibling.setTotalCad(new BigDecimal("5200"));
        when(invoices.findByVendorAndDateWindow(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sibling));

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(25);
        assertThat(codes(result)).containsExactly("SPLIT_INVOICE");
    }

    @Test
    void amountOutlierSignalFiresAgainstVendorHistory() {
        when(invoices.findByVendor_IdAndIdNot("V1", "I1"))
                .thenReturn(List.of(
                        total("H1", "1000"), total("H2", "1100"), total("H3", "900"),
                        total("H4", "1050"), total("H5", "950")));
        invoice.setTotalCad(new BigDecimal("100000"));

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(20);
        assertThat(codes(result)).containsExactly("AMOUNT_OUTLIER");
    }

    @Test
    void combinedSignalsBlockTheInvoice() {
        when(invoices.existsByVendor_IdAndCanonicalInvoiceNumberAndIdNot("V1", "INV-001", "I1"))
                .thenReturn(true);
        when(invoices.findByVendorAndDateWindow(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(total("I2", "5000")));
        when(invoices.findByVendor_IdAndIdNot("V1", "I1"))
                .thenReturn(List.of(
                        total("H1", "1000"), total("H2", "1100"), total("H3", "900"),
                        total("H4", "1050"), total("H5", "950")));
        invoice.setTotalCad(new BigDecimal("5000"));

        StageResult result = node.run(invoice, context(null));

        // 30 duplicate + 25 split + 20 outlier = 75
        assertThat(result.outcome()).isEqualTo(Outcome.BLOCK);
        assertThat(result.holdQueue()).isEqualTo(HoldQueue.FRAUD_HOLD);
        assertThat(result.patch().get("riskScore")).isEqualTo(75);
        assertThat(codes(result)).containsExactlyInAnyOrder(
                "DUPLICATE_INVOICE_NUMBER", "SPLIT_INVOICE", "AMOUNT_OUTLIER");
    }

    @Test
    void signalsOverriddenByAnAdminAreSkippedOnRescore() {
        when(invoices.existsByVendor_IdAndCanonicalInvoiceNumberAndIdNot("V1", "INV-001", "I1"))
                .thenReturn(true);
        User admin = new User();
        admin.setId("U9");
        Flag reviewed = new Flag(invoice, "FRAUD_SCORING", "DUPLICATE_INVOICE_NUMBER",
                FlagSeverity.FLAG, "Reviewed by Admin");
        reviewed.setOverriddenBy(admin);
        when(flags.findByInvoice_IdOrderById("I1")).thenReturn(List.of(reviewed));

        StageResult result = node.run(invoice, context(null));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        assertThat(result.patch().get("riskScore")).isEqualTo(0);
        assertThat(result.flags()).isEmpty();
    }

    // ---------- fixtures ----------

    private BankChangeRequest change(String status, boolean verifiedByCallback) {
        BankChangeRequest change = new BankChangeRequest();
        change.setVendor(vendor);
        change.setChangedAt(invoice.getInvoiceDate().minusDays(5));
        change.setStatus(status);
        change.setVerifiedByCallback(verifiedByCallback);
        return change;
    }

    private static Invoice total(String id, String amount) {
        Invoice row = new Invoice();
        row.setId(id);
        row.setTotalCad(new BigDecimal(amount));
        return row;
    }

    private static NodeContext context(String sender) {
        if (sender == null) return new NodeContext(null, 1);
        SourceDocument document = new SourceDocument();
        document.setSender(sender);
        return new NodeContext(document, 1);
    }

    private static List<String> codes(StageResult result) {
        return result.flags().stream().map(FlagDraft::code).toList();
    }
}
