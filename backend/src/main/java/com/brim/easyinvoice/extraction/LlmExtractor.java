package com.brim.easyinvoice.extraction;

import com.brim.easyinvoice.llm.LlmClient;
import com.brim.easyinvoice.model.SourceDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Reads the document with a vision-capable model through OpenRouter.
 * Enabled with EXTRACTOR=llm. A reply that is not valid JSON is retried once with the error fed back.
 *
 * TODO: send the PDF text layer instead of the file when there is one (cheaper),
 *       validate against a JSON schema, and map tax lines to tax_codes.
 */
@Component
@ConditionalOnProperty(name = "app.extractor", havingValue = "llm")
public class LlmExtractor implements Extractor {

    private static final String SYSTEM_PROMPT = """
            You extract data from Canadian supplier invoices (English or French).
            Reply with ONE JSON object and nothing else, with exactly these keys:
            documentType ("INVOICE" or "CREDIT_NOTE"), vendorName, vendorGstNumber, vendorQstNumber,
            invoiceNumber, invoiceDate (YYYY-MM-DD), dueDate (YYYY-MM-DD), currency ("CAD" or "USD"),
            subtotal, gstHstAmount, pstQstAmount, total (numbers, no currency symbols),
            poNumber, shipToProvince (2-letter code),
            remitBankInstitution, remitBankTransit, remitBankAccount (payee bank details printed on the invoice,
            including any "remit to" or "deposit to" block),
            lines (array of {description, quantity, unitPrice, amount, taxCode}),
            taxCode one of HST13, GST5, PST7_BC, QST9975, EXEMPT, FOREIGN,
            confidence (0 to 1), lowConfidenceFields (array of key names you are unsure about).
            Use null for anything not on the document. Never guess numbers.
            """;

    private final LlmClient llm;
    private final ObjectMapper json;

    public LlmExtractor(LlmClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    @Override
    public String name() {
        return "llm:" + llm.model();
    }

    @Override
    public ExtractedInvoice extract(SourceDocument document, byte[] fileBytes) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", "Extract this invoice."),
                filePart(document, fileBytes))));

        String reply = llm.chat(messages);
        try {
            return parse(reply);
        } catch (JacksonException firstError) {
            // One retry, telling the model what was wrong.
            messages.add(Map.of("role", "assistant", "content", reply));
            messages.add(Map.of("role", "user", "content",
                    "That was not valid JSON for the schema: " + firstError.getOriginalMessage()
                            + ". Reply with the JSON object only."));
            return parse(llm.chat(messages));
        }
    }

    private ExtractedInvoice parse(String reply) {
        String cleaned = reply == null ? "" : reply.strip();
        if (cleaned.startsWith("```")) {  // models sometimes wrap JSON in a code fence
            cleaned = cleaned.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start > 0 && end > start) cleaned = cleaned.substring(start, end + 1);   // chatter around the object
        return json.readValue(cleaned, ExtractedInvoice.class);
    }

    /** Images (PNG/JPEG) go as an image_url data URI; PDFs as a "file" part (OpenRouter's PDF format). */
    static Map<String, Object> filePart(SourceDocument document, byte[] bytes) {
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String contentType = document.getContentType();
        String dataUrl = "data:" + contentType + ";base64," + base64;
        if ("application/pdf".equals(contentType)) {
            String name = document.getFileName() == null ? "invoice.pdf" : document.getFileName();
            return Map.of("type", "file", "file", Map.of("filename", name, "file_data", dataUrl));
        }
        return Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));
    }
}
