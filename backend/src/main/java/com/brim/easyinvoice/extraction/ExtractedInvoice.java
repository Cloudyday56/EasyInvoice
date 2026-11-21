package com.brim.easyinvoice.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What an Extractor reads off a document. The JSON the LLM returns must have exactly this shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedInvoice(
        String documentType,        // INVOICE | CREDIT_NOTE
        String vendorName,
        String vendorGstNumber,
        String vendorQstNumber,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal gstHstAmount,
        BigDecimal pstQstAmount,
        BigDecimal total,
        String poNumber,
        String shipToProvince,
        List<Line> lines,
        double confidence,            // 0..1, overall
        List<String> lowConfidenceFields) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
                       String taxCode) {
    }

    /** Required fields for the invoice to move on without a human. */
    public boolean hasRequiredFields() {
        return vendorName != null && invoiceNumber != null && total != null && currency != null;
    }
}
