package com.brim.easyinvoice.payments;

import com.brim.easyinvoice.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pretend bank: a payment is SENT when submitted and SETTLED a few seconds later.
 * State is in memory; after a restart the Payment release node simply sends again (idempotent).
 *
 * TODO: return FAILED for test accounts (closed account, bad transit) to exercise Payment exceptions.
 */
@Component
public class MockBankAdapter implements BankAdapter {

    private record Submission(String reference, Instant sentAt) {
    }

    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    private final Duration settleAfter;

    public MockBankAdapter(@Value("${app.bank.settle-after-ms}") long settleAfterMs) {
        this.settleAfter = Duration.ofMillis(settleAfterMs);
    }

    @Override
    public String send(Payment payment) {
        Submission submission = submissions.computeIfAbsent(payment.getId(), id -> new Submission(
                (payment.getMethod().equals("CARD") ? "BRIM-VC-" : "BRIM-EFT-") + Math.abs(id.hashCode()),
                Instant.now()));
        return submission.reference();
    }

    @Override
    public BankStatus status(String paymentId) {
        Submission submission = submissions.get(paymentId);
        if (submission == null) return BankStatus.UNKNOWN;
        boolean settled = Instant.now().isAfter(submission.sentAt().plus(settleAfter));
        return settled ? BankStatus.SETTLED : BankStatus.SENT;
    }

    public void clear() {
        submissions.clear();
    }
}
