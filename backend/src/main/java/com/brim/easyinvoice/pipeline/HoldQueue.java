package com.brim.easyinvoice.pipeline;

/** The exception queues. Each one knows where the invoice re-enters once an Admin fixes it. */
public enum HoldQueue {
    INTAKE_ERRORS("Intake errors"),
    MISSING_DOCUMENT("Missing document"),
    EXTRACTION_REVIEW("Extraction review"),
    VENDOR_HOLD("Vendor hold"),
    VALIDATION_FAILED("Validation failed"),
    FRAUD_HOLD("Fraud hold"),
    ROUTING_ERROR("Routing error"),
    PAYMENT_EXCEPTIONS("Payment exceptions");

    private final String label;

    HoldQueue(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Re-entry rules from the design doc's queue table. */
    public NodeName resumeNode() {
        return switch (this) {
            case INTAKE_ERRORS -> NodeName.INTAKE_GATEWAY;
            case MISSING_DOCUMENT -> NodeName.EXTRACTION;
            case EXTRACTION_REVIEW, VENDOR_HOLD -> NodeName.VENDOR_MATCH;
            case VALIDATION_FAILED -> NodeName.VALIDATION_RULES;
            // A fraud fix must be re-scored; Admin-overridden signals are skipped by the node.
            case FRAUD_HOLD -> NodeName.FRAUD_SCORING;
            case ROUTING_ERROR -> NodeName.APPROVAL_EXECUTION;
            case PAYMENT_EXCEPTIONS -> NodeName.PAYMENT_SCHEDULING;
        };
    }
}
