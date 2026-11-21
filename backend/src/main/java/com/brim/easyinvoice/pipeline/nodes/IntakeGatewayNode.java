package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.intake.FileStorage;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Job: get every document into the system exactly once.
 * The upload endpoint / inbox poller already stored the file (keyed by SHA-256, deduplicated by
 * idempotency key); this node checks the stored file is something we can process.
 */
@Component
public class IntakeGatewayNode implements Node {

    static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");
    static final long MAX_BYTES = 10 * 1024 * 1024;

    private final FileStorage storage;

    public IntakeGatewayNode(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public NodeName name() {
        return NodeName.INTAKE_GATEWAY;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        SourceDocument doc = ctx.document();
        if (doc == null) {
            return StageResult.block(HoldQueue.INTAKE_ERRORS, "NO_SOURCE_DOCUMENT", "No source document recorded");
        }
        if (!doc.hasFile()) {
            return StageResult.pass("Email received without an attachment; the classifier decides what it is");
        }
        if (!storage.exists(doc.getStoragePath())) {
            return StageResult.block(HoldQueue.INTAKE_ERRORS, "FILE_MISSING", "Stored file not found: " + doc.getFileName());
        }
        if (!ALLOWED_TYPES.contains(doc.getContentType())) {
            return StageResult.block(HoldQueue.INTAKE_ERRORS, "UNSUPPORTED_TYPE",
                    "Unsupported file type " + doc.getContentType() + " (PDF, PNG or JPEG only)");
        }
        if (storage.size(doc.getStoragePath()) > MAX_BYTES) {
            return StageResult.block(HoldQueue.INTAKE_ERRORS, "TOO_LARGE", "File is over 10 MB");
        }
        // TODO: detect corrupt files (try to open the PDF / decode the image).
        return StageResult.pass("Stored " + doc.getFileName() + " (sha256 " + doc.getSha256().substring(0, 12) + "…)");
    }
}
