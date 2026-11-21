package com.brim.easyinvoice;

import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.db.DemoResetService;
import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceLine;
import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.repo.DepartmentRepository;
import com.brim.easyinvoice.repo.InvoiceLineRepository;
import com.brim.easyinvoice.repo.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.pipeline.HumanActionService;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.FxRateRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.UserRepository;
import com.brim.easyinvoice.security.ForbiddenException;
import com.brim.easyinvoice.web.ApprovalController;
import com.brim.easyinvoice.web.InvoiceController;
import com.brim.easyinvoice.web.PaymentController;
import com.brim.easyinvoice.web.QueueController;
import com.brim.easyinvoice.web.UserController;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;

import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The whole skeleton against a real PostgreSQL (started inside the test, no Docker needed):
 * the schema Hibernate generates, the CSV seed, the pipeline runner, approvals, release, the audit chain.
 */
@SpringBootTest(properties = {
        "app.pipeline.poll-ms=100",
        "app.pipeline.step-delay-ms=0",
        "app.bank.settle-after-ms=300",
        "app.storage-dir=target/test-storage",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineEndToEndTest {

    /** A real Postgres inside the test JVM. As a bean, it is shut down after everything that uses it. */
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

    @Autowired IntakeService intake;
    @Autowired HumanActionService actions;
    @Autowired DemoResetService demoReset;
    @Autowired AuditService audit;
    @Autowired InvoiceRepository invoices;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository users;
    @Autowired FlagRepository flags;
    @Autowired FxRateRepository fxRates;
    @Autowired VendorRepository vendors;
    @Autowired DepartmentRepository departments;
    @Autowired InvoiceLineRepository invoiceLines;
    @Autowired PlatformTransactionManager transactionManager;
    TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    @Autowired InvoiceController invoiceController;
    @Autowired PaymentController paymentController;
    @Autowired QueueController queueController;
    @Autowired ApprovalController approvalController;
    @Autowired UserController userController;

    @BeforeEach
    void setUpTransactions() {
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    @Order(1)
    void invoiceTravelsFromUploadToPaidWithAValidAuditChain() throws Exception {
        User employee = users.get("U013");     // Operations employee
        User approver = users.get("U007");     // Operations manager
        User financeManager = users.get("U003");

        // Upload -> the runner moves it through the nodes until a person must approve.
        MockMultipartFile file = new MockMultipartFile("file", "Invoice 9001.pdf", "application/pdf", "pdf bytes".getBytes());
        String invoiceId = intake.upload(employee, file).invoiceId();
        waitForStatus(invoiceId, InvoiceStatus.PENDING_APPROVAL);

        // The server refuses the wrong people.
        assertThatThrownBy(() -> actions.approve(employee, invoiceId, null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> actions.approve(users.get("U008"), invoiceId, null)).isInstanceOf(ForbiddenException.class);

        // The assigned approver approves -> payment is scheduled.
        actions.approve(approver, invoiceId, "Looks right");
        waitForStatus(invoiceId, InvoiceStatus.SCHEDULED);
        Payment payment = payments.activeForInvoice(invoiceId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SCHEDULED);

        // Approver cannot release; a different Admin can. The mock bank then settles it.
        assertThatThrownBy(() -> actions.release(approver, payment.getId())).isInstanceOf(ForbiddenException.class);
        actions.release(financeManager, payment.getId());
        waitForStatus(invoiceId, InvoiceStatus.PAID);
        assertThat(payments.get(payment.getId()).getStatus()).isEqualTo(PaymentStatus.SETTLED);

        // Every step, including the refusals, is in a hash chain that still verifies.
        assertThat(audit.forInvoice(invoiceId)).extracting(e -> e.getAction())
                .contains("invoice.received", "approval.approved", "payment.released", "authz.denied");
        assertThat(audit.verifyChain().valid()).isTrue();

        // The screens' endpoints run their queries and serialize the entities to JSON.
        String detail = json.writeValueAsString(invoiceController.detail("U001", invoiceId));
        assertThat(detail).contains("\"approvalSteps\"", "\"auditEvents\"", "\"pipeline\"", "\"totalCad\"");
        assertThat(invoiceController.list("U013")).extracting(i -> i.id()).contains(invoiceId);   // employee sees own
        assertThat(invoiceController.list("U008")).extracting(i -> i.id()).doesNotContain(invoiceId); // other dept does not
        assertThat(paymentController.list("U003")).extracting(r -> r.payment().id()).contains(payment.getId());
        assertThat(approvalController.myApprovals("U007")).isEmpty();   // nothing waiting any more
        assertThat(queueController.queues("U001")).hasSize(9);          // 8 exception queues + ignored mail
        assertThat(json.writeValueAsString(userController.lookups("U001"))).contains("\"legalName\"");
        assertThat(json.writeValueAsString(userController.allUsers())).contains("\"readOnly\"");
    }

    @Test
    @Order(2)
    void aBlockedInvoiceParksInAQueueAndResumesAtTheRightNode() {
        User employee = users.get("U013");
        User admin = users.get("U004");   // AP clerk

        // A file named like block-<node> makes that node BLOCK once (demo hook in the runner).
        MockMultipartFile file = new MockMultipartFile("file", "Invoice 9002_block-vendor-match.pdf",
                "application/pdf", "other bytes".getBytes());
        String invoiceId = intake.upload(employee, file).invoiceId();
        waitForStatus(invoiceId, InvoiceStatus.ON_HOLD);

        var held = invoices.get(invoiceId);
        assertThat(held.getHoldQueue()).isEqualTo("VENDOR_HOLD");
        assertThat(held.getResumeNode()).isEqualTo("VENDOR_MATCH");
        assertThat(queueController.queues("U001")).anySatisfy(q ->
                assertThat(q.items()).extracting(i -> i.id()).contains(invoiceId));
        assertThatThrownBy(() -> actions.resumeFromHold(employee, invoiceId, "please")).isInstanceOf(ForbiddenException.class);

        actions.resumeFromHold(admin, invoiceId, "Checked the vendor by phone");
        waitForStatus(invoiceId, InvoiceStatus.PENDING_APPROVAL);

        // The block stays visible on the invoice, marked as overridden with the reason.
        Flag flag = flags.findByInvoice_IdOrderById(invoiceId).stream()
                .filter(f -> "DEMO_BLOCK".equals(f.getCode())).findFirst().orElseThrow();
        assertThat(flag.getOverriddenById()).isEqualTo("U004");
        assertThat(flag.getOverrideReason()).isEqualTo("Checked the vendor by phone");
    }

    @Test
    @Order(3)
    void pollingTheInboxIsIdempotent() {
        Map<String, Integer> first = intake.pollInbox(users.get("U004"));
        assertThat(first.get("emails")).isEqualTo(59);
        assertThat(first.get("created")).isEqualTo(60);   // 59 emails, one has two attachments

        Map<String, Integer> second = intake.pollInbox(users.get("U004"));
        assertThat(second.get("created")).isZero();
        assertThat(second.get("alreadyKnown")).isEqualTo(60);
    }

    @Test
    @Order(4)
    void resetDemoWipesAndReloadsTheSampleData() {
        demoReset.reset(users.get("U001"));
        assertThat(users.count()).isEqualTo(40);
        assertThat(invoices.count()).isEqualTo(426);
        assertThat(audit.verifyChain().valid()).isTrue();

        // Seeded values keep their exact decimals (4 places for FX rates) and CSV quirks are flagged, not dropped.
        assertThat(fxRates.usdToCadOn(LocalDate.of(2026, 1, 2))).hasValueSatisfying(rate ->
                assertThat(rate).isEqualByComparingTo("1.3738"));
        assertThat(invoices.get("AP-00001").getTotalCad()).isEqualByComparingTo("2520.00");
        assertThat(flags.findByInvoice_IdOrderById("AP-00026")).extracting(f -> f.getCode())
                .contains("HISTORICAL_REPEAT_NUMBER");
    }

    @Test
    @Order(5)
    void theRepeatGuardRefusesTheSameVendorAndInvoiceNumberTwice() {
        invoices.saveAndFlush(draftInvoice("T-1", vendors.getReferenceById("V001")));
        assertThatThrownBy(() -> invoices.saveAndFlush(draftInvoice("T-2", vendors.getReferenceById("V001"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Invoice draftInvoice(String id, Vendor vendor) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setVendor(vendor);
        invoice.setCanonicalInvoiceNumber("REPEAT1");
        invoice.setStatus(InvoiceStatus.RECEIVED);
        return invoice;
    }

    @Test
    @Order(6)
    void theSchemaHasRealForeignKeys(@Autowired javax.sql.DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from information_schema.table_constraints "
                     + "where constraint_type = 'FOREIGN KEY' and table_schema = 'public'")) {
            rows.next();
            assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(39);
        }
    }

    @Test
    @Order(6)
    void theDatabaseRefusesARowThatPointsAtNothing() {
        Invoice orphan = draftInvoice("T-ORPHAN", vendors.getReferenceById("NO-SUCH-VENDOR"));
        orphan.setCanonicalInvoiceNumber("ORPHAN1");
        assertThatThrownBy(() -> invoices.saveAndFlush(orphan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(7)
    void relationshipsCanBeWalkedAndUpdatedThroughTheEntities() {
        transaction.executeWithoutResult(tx -> {
            Invoice invoice = invoices.get("AP-00001");
            assertThat(invoice.getVendor().getLegalName()).isNotBlank();
            assertThat(invoice.getLines()).isNotEmpty();
            assertThat(invoice.getLines().get(0).getInvoice()).isEqualTo(invoice);
            assertThat(invoice.getApprovalSteps()).isNotEmpty();
            assertThat(invoice.getPayments()).isNotEmpty();

            // Updating a relationship is just setting the reference; the foreign key column follows.
            invoice.setDepartment(departments.getReferenceById("D02"));
        });
        assertThat(invoices.get("AP-00001").getDepartmentId()).isEqualTo("D02");

        // Lines belong to their invoice: deleting the invoice deletes them (and replacing lines drops the old ones).
        transaction.executeWithoutResult(tx -> {
            Invoice draft = draftInvoice("T-LINES", vendors.getReferenceById("V002"));
            draft.addLine(line(1));
            draft.addLine(line(2));
            Invoice invoice = invoices.saveAndFlush(draft);   // assigned ids are merged: keep working on the returned, managed copy
            assertThat(invoiceLines.findByInvoice_IdOrderByLineNo("T-LINES")).hasSize(2);
            invoice.replaceLines(java.util.List.of(line(1)));
            invoices.flush();
            assertThat(invoiceLines.findByInvoice_IdOrderByLineNo("T-LINES")).hasSize(1);
            invoices.delete(invoice);
            invoices.flush();
            assertThat(invoiceLines.findByInvoice_IdOrderByLineNo("T-LINES")).isEmpty();
        });
    }

    private static InvoiceLine line(int no) {
        InvoiceLine line = new InvoiceLine();
        line.setLineNo(no);
        line.setDescription("line " + no);
        return line;
    }

    private void waitForStatus(String invoiceId, InvoiceStatus expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(invoices.get(invoiceId).getStatus()).isEqualTo(expected));
    }
}
