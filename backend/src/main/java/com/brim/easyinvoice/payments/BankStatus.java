package com.brim.easyinvoice.payments;

public enum BankStatus {
    UNKNOWN,  // the bank has never seen this payment
    SENT,     // accepted, not settled yet
    SETTLED,
    FAILED
}
