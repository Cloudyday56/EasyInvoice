package com.brim.easyinvoice.model;

/** One status per invoice. Only InvoiceStateMachine writes it. */
public enum InvoiceStatus {
    RECEIVED,          // document stored, not yet read
    IGNORED,           // not an invoice (terminal, can be rescued)
    EXTRACTED,         // structured data captured
    VALIDATING,        // moving through automated checks
    ON_HOLD,           // parked in hold_queue, waiting for an Admin
    ON_HOLD_VENDOR,    // waiting for a corrected invoice from the vendor
    PENDING_APPROVAL,  // approval chain built, waiting on a person
    APPROVED,          // all steps approved
    SCHEDULED,         // payment created with a pay date and method
    RELEASED,          // Admin released; sent to the (mock) bank
    PAID,              // settlement confirmed (terminal)
    REJECTED;          // will not be paid (terminal)

    public boolean isTerminal() {
        return this == PAID || this == REJECTED || this == IGNORED;
    }
}
