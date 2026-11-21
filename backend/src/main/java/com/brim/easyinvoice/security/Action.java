package com.brim.easyinvoice.security;

/** Every protected thing a user can do. Each endpoint names exactly one. */
public enum Action {
    VIEW_INVOICE(false),
    UPLOAD_INVOICE(true),
    APPROVE_INVOICE(true),   // also covers reject and "return to AP"
    RESOLVE_HOLD(true),      // work an exception queue: resume or reject
    RELEASE_PAYMENT(true),
    VIEW_ADMIN_SCREENS(false), // needs-attention queue, payments, full audit log
    RUN_ADMIN_TOOLS(true);   // reset demo, poll inbox, test LLM

    private final boolean changesData;

    Action(boolean changesData) {
        this.changesData = changesData;
    }

    public boolean changesData() {
        return changesData;
    }
}
