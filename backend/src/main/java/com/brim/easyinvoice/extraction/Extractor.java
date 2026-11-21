package com.brim.easyinvoice.extraction;

import com.brim.easyinvoice.model.SourceDocument;

/**
 * Turns a document into structured data. Two implementations, chosen by app.extractor:
 *   manual -> ManualExtractor (default, reads the PDF text layer, no network)
 *   llm  -> LlmExtractor  (OpenRouter, needs OPENROUTER_API_KEY)
 */
public interface Extractor {

    ExtractedInvoice extract(SourceDocument document, byte[] fileBytes);

    String name();
}
