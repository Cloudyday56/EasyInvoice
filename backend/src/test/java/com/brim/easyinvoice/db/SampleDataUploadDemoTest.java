package com.brim.easyinvoice.db;

import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The upload demo: each sample file, uploaded by an employee in the UI, stops at the node the README says.
 * (A separate class from SampleDataSeedTest: the inbox test there creates the same invoices by email,
 * and a second copy of the same vendor invoice number would be a repeat.)
 */
@SpringBootTest(properties = {
        "app.data-dir=../sample-data",
        "app.seed.lenient=true",
        "app.pipeline.poll-ms=100",
        "app.pipeline.step-delay-ms=0",
        "app.storage-dir=target/test-storage-upload-demo",
})
class SampleDataUploadDemoTest {

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
    @Autowired UserRepository users;
    @Autowired InvoiceRepository invoices;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void eachSampleFileStopsWhereTheDemoSaysItDoes() throws Exception {
        assertThat(upload("scan_8749.png", "image/png")).isEqualTo("ON_HOLD EXTRACTION_REVIEW");
        assertThat(upload("Invoice 000961.pdf", "application/pdf")).isEqualTo("ON_HOLD VALIDATION_FAILED");
        assertThat(upload("voyageurcorporat-inv-017.pdf", "application/pdf")).isEqualTo("ON_HOLD FRAUD_HOLD");
        assertThat(upload("pineconecollabor_PI262809.pdf", "application/pdf")).isEqualTo("PENDING_APPROVAL null");
    }

    /** Uploads as Aiden Grant (U013), waits for the invoice to stop moving, returns "STATUS HOLD_QUEUE". */
    private String upload(String fileName, String type) throws IOException {
        Path pdf = Path.of("../sample-data/incoming_invoices", fileName);
        String id = intake.upload(users.get("U013"),
                new MockMultipartFile("file", fileName, type, Files.readAllBytes(pdf))).invoiceId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return await().atMost(Duration.ofSeconds(60)).until(() -> transaction.execute(s -> {
            Invoice i = invoices.findById(id).orElseThrow();
            InvoiceStatus status = i.getStatus();
            boolean moving = status == InvoiceStatus.RECEIVED || status == InvoiceStatus.EXTRACTED || status == InvoiceStatus.VALIDATING;
            return moving ? null : status + " " + i.getHoldQueue();
        }), outcome -> outcome != null);
    }
}
