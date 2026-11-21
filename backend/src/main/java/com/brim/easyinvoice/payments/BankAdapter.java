package com.brim.easyinvoice.payments;

import com.brim.easyinvoice.model.Payment;

/** The payment rail. Only a mock exists; a real EFT/card integration would implement this. */
public interface BankAdapter {

    /** Sends the payment. Idempotent: the payment id is the idempotency key, so a retry never pays twice. */
    String send(Payment payment);

    BankStatus status(String paymentId);
}
