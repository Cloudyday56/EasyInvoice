package com.brim.easyinvoice.extraction;

import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.repo.VendorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Stand-in for the LLM. Invents plausible, deterministic data from the file's hash:
 * the same file always "extracts" to the same invoice. It does NOT read the document.
 */
@Component
@ConditionalOnProperty(name = "app.extractor", havingValue = "fake", matchIfMissing = true)
public class FakeExtractor implements Extractor {

    private final VendorRepository vendors;

    public FakeExtractor(VendorRepository vendors) {
        this.vendors = vendors;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public ExtractedInvoice extract(SourceDocument document, byte[] fileBytes) {
        long seed = Long.parseUnsignedLong(document.sha256().substring(0, 12), 16);

        List<Vendor> activeVendors = vendors.findAllActive();
        Vendor vendor = activeVendors.get((int) (seed % activeVendors.size()));

        // Keep amounts under the $10,000 department approval limit so the demo needs one approver.
        BigDecimal subtotal = BigDecimal.valueOf(200 + seed % 8000).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hst = subtotal.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);
        LocalDate invoiceDate = LocalDate.now().minusDays(seed % 10);

        ExtractedInvoice.Line line = new ExtractedInvoice.Line(
                "Services - " + vendor.category(), BigDecimal.ONE, subtotal, subtotal, "HST13");

        return new ExtractedInvoice("INVOICE", vendor.legalName(), null, null, invoiceNumberFrom(document),
                invoiceDate, invoiceDate.plusDays(30), "CAD", subtotal, hst, BigDecimal.ZERO, subtotal.add(hst),
                null, "ON", List.of(line), 0.99, List.of());
    }

    /** "Invoice 4443.pdf" -> "4443"; otherwise a number derived from the hash. */
    private static String invoiceNumberFrom(SourceDocument document) {
        String name = document.fileName() == null ? "" : document.fileName();
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        if (stem.startsWith("Invoice ")) return stem.substring("Invoice ".length());
        return "FAKE-" + document.sha256().substring(0, 6).toUpperCase();
    }
}
