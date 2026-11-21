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
            case FRAUD_HOLD -> NodeName.APPROVAL_ROUTING;
            case ROUTING_ERROR -> NodeName.APPROVAL_EXECUTION;
            // TODO: resume at FRAUD_SCORING instead when the fix changed bank details.
            case PAYMENT_EXCEPTIONS -> NodeName.PAYMENT_SCHEDULING;
        };
    }
}
