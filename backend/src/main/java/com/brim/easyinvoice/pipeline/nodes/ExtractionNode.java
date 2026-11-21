package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.repo.PurchaseOrderRepository;
import com.brim.easyinvoice.model.DocumentType;
import com.brim.easyinvoice.extraction.ExtractedInvoice;
import com.brim.easyinvoice.extraction.Extractor;
import com.brim.easyinvoice.intake.FileStorage;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceLine;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.pipeline.FlagDraft;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job: turn the document into structured data, using whichever Extractor is configured
 * (manual by default, LLM with EXTRACTOR=llm).
 */
@Component
public class ExtractionNode implements Node {

    static final double MIN_CONFIDENCE = 0.6;

    private final Extractor extractor;
    private final FileStorage storage;
    private final PurchaseOrderRepository purchaseOrders;

    public ExtractionNode(Extractor extractor, FileStorage storage, PurchaseOrderRepository purchaseOrders) {
        this.purchaseOrders = purchaseOrders;
        this.extractor = extractor;
        this.storage = storage;
    }

    @Override
    public NodeName name() {
        return NodeName.EXTRACTION;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        SourceDocument doc = ctx.document();
        if (doc == null || !doc.hasFile()) {
            return StageResult.block(HoldQueue.EXTRACTION_REVIEW, "NO_FILE", "There is no file to read");
        }

        ExtractedInvoice data = extractor.extract(doc, storage.read(doc.getStoragePath()));

        if (!data.hasRequiredFields()) {
            return StageResult.block(HoldQueue.EXTRACTION_REVIEW, "REQUIRED_FIELD_MISSING",
                    "Vendor, invoice number, currency or total could not be read");
        }
        if (data.confidence() < MIN_CONFIDENCE) {
            return StageResult.block(HoldQueue.EXTRACTION_REVIEW, "LOW_CONFIDENCE",
                    "Extraction confidence " + data.confidence() + " is below " + MIN_CONFIDENCE);
        }

        // Keys are Invoice field names. HashMap, not Map.of: extracted values may be null.
        Map<String, Object> patch = new HashMap<>();
        patch.put("documentType", DocumentType.parse(data.documentType()));
        patch.put("vendorNameRaw", data.vendorName());
        patch.put("vendorGstNumberRaw", data.vendorGstNumber());
        patch.put("vendorInvoiceNumber", data.invoiceNumber());
        patch.put("invoiceDate", data.invoiceDate());
        patch.put("dueDate", data.dueDate());
        patch.put("currency", data.currency());
        patch.put("subtotal", data.subtotal());
        patch.put("gstHstAmount", data.gstHstAmount());
        patch.put("pstQstAmount", data.pstQstAmount());
        patch.put("total", data.total());
        patch.put("poNumberRaw", data.poNumber());
        patch.put("purchaseOrder", data.poNumber() == null ? null : purchaseOrders.findById(data.poNumber()).orElse(null));
        patch.put("shipToProvince", data.shipToProvince());
        patch.put("remitBankInstitution", data.remitBankInstitution());
        patch.put("remitBankTransit", data.remitBankTransit());
        patch.put("remitBankAccount", data.remitBankAccount());

        List<InvoiceLine> lines = new ArrayList<>();
        List<ExtractedInvoice.Line> extractedLines = data.lines() == null ? List.of() : data.lines();
        for (int i = 0; i < extractedLines.size(); i++) {
            ExtractedInvoice.Line l = extractedLines.get(i);
            boolean federalTax = l.taxCode() == null || l.taxCode().startsWith("HST") || l.taxCode().startsWith("GST");
            InvoiceLine line = new InvoiceLine();   // Invoice.replaceLines links it to the invoice
            line.setLineNo(i + 1);
            line.setDescription(l.description());
            line.setQuantity(l.quantity());
            line.setUnitPrice(l.unitPrice());
            line.setAmount(l.amount());
            line.setDepartment(invoice.getDepartment());
            line.setGstHstCode(federalTax ? l.taxCode() : null);
            line.setPstQstCode(federalTax ? null : l.taxCode());
            lines.add(line);
        }

        String message = "Extracted with " + extractor.name() + " (confidence " + data.confidence() + ")";
        List<String> unsure = data.lowConfidenceFields() == null ? List.of() : data.lowConfidenceFields();
        if (!unsure.isEmpty()) {
            List<FlagDraft> flags = unsure.stream()
                    .map(field -> FlagDraft.warning("LOW_CONFIDENCE_FIELD", "Check field: " + field))
                    .toList();
            return StageResult.flag(message, flags, patch, lines);
        }
        return StageResult.pass(message, patch, lines);
    }
}
