package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.Invoice;

/**
 * The one contract every pipeline step follows.
 *
 * Rules: a node does one job, never calls another node, never changes the invoice status,
 * and returns the same answer if run again on the same invoice (idempotent).
 */
public interface Node {

    NodeName name();

    StageResult run(Invoice invoice, NodeContext ctx);
}
