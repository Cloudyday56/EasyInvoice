package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.SourceDocument;

/** Extra facts a node may need besides the invoice. */
public record NodeContext(
        SourceDocument document,  // the file/email the invoice came from (null for seeded history)
        int attempt) {            // 1 on the first try, 2 and 3 on retries
}
