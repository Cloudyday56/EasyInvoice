package com.brim.easyinvoice.extraction;

import com.brim.easyinvoice.model.SourceDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: every PDF in the sample batch reads cleanly, and a scan comes back empty instead of crashing.
 * Two kinds of real mess in the batch are expected: some vendors print no GST number, and Evergreen's
 * invoice does not add up (3,180 + 159 GST is not 3,439), which must be flagged, not trusted.
 */
class ManualExtractorTest {

    static final Path BATCH = Path.of("../data/incoming_invoices");
    final ManualExtractor extractor = new ManualExtractor();

    @Test
    void everyPdfInTheSampleBatchIsReadAndAddsUp() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(BATCH)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".pdf")).sorted().toList()) {
                ExtractedInvoice data = extractor.extract(new SourceDocument(), Files.readAllBytes(file));
                boolean knownBadArithmetic = file.getFileName().toString().startsWith("evergreenenergyu_000850");
                if (!data.hasRequiredFields() || data.invoiceDate() == null || data.lines().isEmpty()
                        || data.shipToProvince() == null || (data.confidence() < 0.9) != knownBadArithmetic) {
                    problems.add(file.getFileName() + " -> " + data);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void aScanHasNoTextLayerAndComesBackEmpty() throws IOException {
        ExtractedInvoice data = extractor.extract(new SourceDocument(),
                Files.readAllBytes(BATCH.resolve("scan_8049.png")));
        assertThat(data.hasRequiredFields()).isFalse();
    }

    @Test
    void creditNotesAreNegativeAndFrenchNumbersParse() throws IOException {
        ExtractedInvoice credit = extractor.extract(new SourceDocument(),
                Files.readAllBytes(BATCH.resolve("fixwellfacilitys_CNFA000413.pdf")));
        assertThat(credit.documentType()).isEqualTo("CREDIT_NOTE");
        assertThat(credit.total()).isEqualByComparingTo("-1614.21");

        ExtractedInvoice french = extractor.extract(new SourceDocument(),
                Files.readAllBytes(BATCH.resolve("placemontroyalim-inv-053.pdf")));
        assertThat(french.total()).isEqualByComparingTo("6990.48");
        assertThat(french.pstQstAmount()).isEqualByComparingTo("606.48");
    }
}
