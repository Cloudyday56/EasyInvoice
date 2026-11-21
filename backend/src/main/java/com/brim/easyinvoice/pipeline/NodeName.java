package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.InvoiceStatus;

/**
 * The 10 nodes, in pipeline order. This enum IS the ordering: the runner always moves an
 * invoice to the next constant. Each node also says which status the invoice gets when the
 * node passes, and which queue to use if the node keeps crashing.
 */
public enum NodeName {
    INTAKE_GATEWAY("Intake gateway", InvoiceStatus.RECEIVED, HoldQueue.INTAKE_ERRORS),
    EMAIL_CLASSIFIER("Email classifier", InvoiceStatus.RECEIVED, HoldQueue.INTAKE_ERRORS),
    EXTRACTION("Extraction", InvoiceStatus.EXTRACTED, HoldQueue.EXTRACTION_REVIEW),
    VENDOR_MATCH("Normalization and vendor match", InvoiceStatus.VALIDATING, HoldQueue.VENDOR_HOLD),
    VALIDATION_RULES("Validation rules", InvoiceStatus.VALIDATING, HoldQueue.VALIDATION_FAILED),
    FRAUD_SCORING("Fraud and risk scoring", InvoiceStatus.VALIDATING, HoldQueue.FRAUD_HOLD),
    APPROVAL_ROUTING("Approval routing", InvoiceStatus.PENDING_APPROVAL, HoldQueue.ROUTING_ERROR),
    APPROVAL_EXECUTION("Approval execution", InvoiceStatus.APPROVED, HoldQueue.ROUTING_ERROR),
    PAYMENT_SCHEDULING("Payment scheduling", InvoiceStatus.SCHEDULED, HoldQueue.PAYMENT_EXCEPTIONS),
    PAYMENT_RELEASE("Payment release", InvoiceStatus.PAID, HoldQueue.PAYMENT_EXCEPTIONS);

    private final String label;
    private final InvoiceStatus statusOnPass;
    private final HoldQueue failureQueue;

    NodeName(String label, InvoiceStatus statusOnPass, HoldQueue failureQueue) {
        this.label = label;
        this.statusOnPass = statusOnPass;
        this.failureQueue = failureQueue;
    }

    public String label() {
        return label;
    }

    public InvoiceStatus statusOnPass() {
        return statusOnPass;
    }

    public HoldQueue failureQueue() {
        return failureQueue;
    }

    /** The node after this one, or null if this is the last. */
    public NodeName next() {
        int nextIndex = ordinal() + 1;
        return nextIndex < values().length ? values()[nextIndex] : null;
    }

    /** The status an invoice has just before this node runs (used when resuming from a hold). */
    public InvoiceStatus statusBefore() {
        return ordinal() == 0 ? InvoiceStatus.RECEIVED : values()[ordinal() - 1].statusOnPass();
    }
}
