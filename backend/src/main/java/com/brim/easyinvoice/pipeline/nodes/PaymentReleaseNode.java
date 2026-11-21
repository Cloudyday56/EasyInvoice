package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.VirtualCard;
import com.brim.easyinvoice.notify.NotificationService;
import com.brim.easyinvoice.payments.BankAdapter;
import com.brim.easyinvoice.payments.BankStatus;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.VirtualCardRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Job: send the released payment to the bank and confirm it settled.
 * The Admin's "Release" click (HumanActionService.release) marks the payment RELEASED and
 * queues this node. Then, one step per run:
 *   SCHEDULED -> wait for an Admin to release
 *   RELEASED  -> send to the (mock) bank, check back in 2 s
 *   SENT      -> ask the bank; SETTLED -> PASS (invoice becomes PAID), FAILED -> Payment exceptions
 *
 * TODO card flow: mock card network authorizes only if amount and merchant match; on decline fall back to EFT once.
 */
@Component
public class PaymentReleaseNode implements Node {

    private static final int RECHECK_SECONDS = 2;

    private final PaymentRepository payments;
    private final VirtualCardRepository virtualCards;
    private final BankAdapter bank;
    private final NotificationService notifications;

    public PaymentReleaseNode(PaymentRepository payments, VirtualCardRepository virtualCards, BankAdapter bank,
                              NotificationService notifications) {
        this.payments = payments;
        this.virtualCards = virtualCards;
        this.bank = bank;
        this.notifications = notifications;
    }

    @Override
    public NodeName name() {
        return NodeName.PAYMENT_RELEASE;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        Payment payment = payments.activeForInvoice(invoice.getId()).orElse(null);
        if (payment == null) {
            return StageResult.block(HoldQueue.PAYMENT_EXCEPTIONS, "NO_PAYMENT", "No payment exists for this invoice");
        }

        switch (payment.getStatus()) {
            case SCHEDULED:
                return StageResult.waitForHuman("Payment " + payment.getId() + " is waiting for an Admin to release it");

            case RELEASED:
                if (payment.getMethod() == PaymentMethod.CARD) {
                    VirtualCard card = new VirtualCard();
                    card.setPayment(payment);
                    card.setMaskedNumber("**** **** **** " + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
                    card.setAmountLimit(payment.getAmount());
                    card.setMerchantLock(payment.getVendor());
                    card.setExpiresAt(OffsetDateTime.now().plusDays(7));
                    virtualCards.save(card);
                }
                String reference = bank.send(payment);
                payment.setStatus(PaymentStatus.SENT);
                payment.setReference(reference);
                payment.setSentAt(OffsetDateTime.now());
                payments.save(payment);
                return StageResult.recheckIn(RECHECK_SECONDS, "Sent to bank, reference " + reference);

            case SENT:
                BankStatus status = bank.status(payment.getId());
                if (status == BankStatus.SETTLED) {
                    payment.setStatus(PaymentStatus.SETTLED);
                    payment.setPaidDate(LocalDate.now());
                    payments.save(payment);
                    notifications.notify(invoice.getRequester(), "payment_settled", invoice,
                            "Payment for " + invoice.getId() + " settled");
                    return StageResult.pass("Payment " + payment.getId() + " settled");
                }
                if (status == BankStatus.FAILED) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason("Bank reported failure");
                    payments.save(payment);
                    return StageResult.block(HoldQueue.PAYMENT_EXCEPTIONS, "PAYMENT_FAILED",
                            "Bank rejected payment " + payment.getId());
                }
                if (status == BankStatus.UNKNOWN) {
                    bank.send(payment); // the mock forgot it (restart): re-send, same idempotency key
                }
                return StageResult.recheckIn(RECHECK_SECONDS, "Waiting for settlement");

            case SETTLED:
                return StageResult.pass("Payment " + payment.getId() + " already settled");

            default:
                return StageResult.block(HoldQueue.PAYMENT_EXCEPTIONS, "UNEXPECTED_PAYMENT_STATUS",
                        "Payment " + payment.getId() + " is " + payment.getStatus());
        }
    }
}
