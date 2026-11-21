package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job: decide whether an email is an invoice, a credit note, or noise.
 * STUB: only the "no attachment" case is handled. Every email with an attachment passes.
 */
@Component
public class EmailClassifierNode implements Node {

    private static final List<String> INVOICE_WORDS = List.of(
            "invoice", "facture", "credit note", "note de crédit", "amount due", "payment");

    @Override
    public NodeName name() {
        return NodeName.EMAIL_CLASSIFIER;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        SourceDocument doc = ctx.document();
        if ("UPLOAD".equals(doc.getChannel())) {
            return StageResult.pass("Uploaded from the UI: classification skipped");
        }
        if (!doc.hasFile()) {
            String text = (doc.getSubject() + " " + doc.getBody()).toLowerCase();
            boolean looksLikeInvoice = INVOICE_WORDS.stream().anyMatch(text::contains);
            if (looksLikeInvoice) {
                // TODO: write the simulated auto-reply asking the vendor to resend.
                return StageResult.block(HoldQueue.MISSING_DOCUMENT, "NO_ATTACHMENT",
                        "Invoice email from " + doc.getSender() + " has no attachment");
            }
            return StageResult.ignore("NOT_INVOICE", "No attachment and no invoice keywords: \"" + doc.getSubject() + "\"");
        }
        // TODO: keyword rules, then a cheap LLM call for ambiguous mail -> invoice | credit_note | statement | not_invoice.
        // TODO: record the sender domain for the fraud lookalike check.
        return StageResult.pass("Has an attachment: treated as an invoice (stub classifier)");
    }
}
