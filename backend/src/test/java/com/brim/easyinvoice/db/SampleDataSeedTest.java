package com.brim.easyinvoice.db;

import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.audit.AuditEventRepository;
import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.UserRepository;
import com.brim.easyinvoice.repo.VendorRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Seeds sample-data/ (3 history invoices plus deliberately bad rows) in lenient mode, then polls its inbox.
 * Every good row must load, every bad row must be skipped and reported, and each inbox email must end where
 * the demo says it does: extraction review, validation failed, fraud hold, missing document, ignored.
 */
@SpringBootTest(properties = {
        "app.data-dir=../sample-data",
        "app.seed.lenient=true",
        "app.pipeline.poll-ms=100",
        "app.pipeline.step-delay-ms=0",
        "app.storage-dir=target/test-storage-sample",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SampleDataSeedTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws IOException {
            return EmbeddedPostgres.start();
        }

        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return DataSourceBuilder.create()
                    .url("jdbc:postgresql://localhost:" + postgres.getPort() + "/postgres")
                    .username("postgres").password("postgres").build();
        }
    }

    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired InvoiceRepository invoices;
    @Autowired FlagRepository flags;
    @Autowired AuditEventRepository audits;
    @Autowired IntakeService intake;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Order(1)
    void goodRowsLoadAndBadRowsAreSkippedAndReported() {
        assertThat(invoices.count()).isEqualTo(3);
        assertThat(invoices.findById("AP-00277")).isPresent();          // the duplicate-id copy did not replace it
        assertThat(invoices.findById("AP-BAD-01")).isEmpty();           // unparseable total
        assertThat(invoices.findById("AP-BAD-02")).isEmpty();           // unknown vendor

        assertThat(vendors.count()).isEqualTo(7);
        assertThat(users.findById("U-BAD-01")).isEmpty();               // unknown role
        assertThat(users.findById("U-BAD-02")).isEmpty();               // unknown department
    }

    @Test
    @Order(2)
    void usersKeepRolesAndTheDelegateRelationship() {
        User onLeave = users.findById("U012").orElseThrow();
        assertThat(onLeave.getRole()).isEqualTo(Role.APPROVER);
        assertThat(onLeave.getStatus()).isEqualTo(UserStatus.ON_LEAVE);
        assertThat(onLeave.getDelegateId()).isEqualTo("U003");
        assertThat(users.findById("U003").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(users.findById("U013").orElseThrow().getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(users.findById("U015").orElseThrow().getStatus()).isEqualTo(UserStatus.TERMINATED);
    }

    @Test
    @Order(3)
    void rejectsAreListedInTheSeedAuditEvent() {
        AuditEvent seeded = audits.findAll().stream().filter(e -> "seed.loaded".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(seeded.getPayloadJson()).contains("rejected_rows", "duplicate id AP-00277", "unknown Vendor V999");
    }

    @Test
    @Order(4)
    void everyInboxEmailEndsWhereTheDemoSaysItDoes() {
        intake.pollInbox(users.get("U003"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Map<String, List<String>> outcomes = await().atMost(Duration.ofSeconds(60)).until(() -> transaction.execute(s -> {
            Map<String, List<String>> byOutcome = new TreeMap<>();
            for (Invoice i : invoices.findAll()) {
                if (!i.getId().startsWith("EI-")) continue;
                if (i.getStatus() != InvoiceStatus.ON_HOLD && i.getStatus() != InvoiceStatus.IGNORED) return null;   // still moving
                String outcome = i.getStatus() == InvoiceStatus.IGNORED ? "IGNORED" : i.getHoldQueue();
                byOutcome.computeIfAbsent(outcome, k -> new java.util.ArrayList<>())
                        .add(i.getVendorInvoiceNumber() == null ? "-" : i.getVendorInvoiceNumber());
            }
            return byOutcome.values().stream().mapToInt(List::size).sum() == 5 ? byOutcome : null;
        }), o -> o != null);

        assertThat(outcomes.keySet()).containsExactly(
                "EXTRACTION_REVIEW", "FRAUD_HOLD", "IGNORED", "MISSING_DOCUMENT", "VALIDATION_FAILED");
        assertThat(outcomes.get("VALIDATION_FAILED")).containsExactly("000961");   // already billed in history (AP-00393)
        assertThat(outcomes.get("FRAUD_HOLD")).containsExactly("000903-A");        // look-alike sender + split of AP-00277

        Invoice held = transaction.execute(s -> invoices.findAll().stream()
                .filter(i -> "000903-A".equals(i.getVendorInvoiceNumber())).findFirst().orElseThrow());
        assertThat(held.getRiskScore()).isGreaterThanOrEqualTo(70);
        assertThat(flags.findByInvoice_IdOrderById(held.getId()))
                .extracting(f -> f.getCode()).contains("LOOKALIKE_SENDER_DOMAIN", "SPLIT_INVOICE");
    }
}
