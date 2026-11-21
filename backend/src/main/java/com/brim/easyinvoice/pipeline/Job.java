package com.brim.easyinvoice.pipeline;

/** One row of the jobs table: "run this node on this invoice". */
public record Job(long id, String invoiceId, NodeName node, int attempts) {
}
