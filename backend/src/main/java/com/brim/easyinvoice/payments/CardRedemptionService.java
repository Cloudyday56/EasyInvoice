package com.brim.easyinvoice.payments;

import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.CardTransaction;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.VirtualCard;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.PipelineRunner;
import com.brim.easyinvoice.repo.CardTransactionRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.VirtualCardRepository;
import com.brim.easyinvoice.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Mock card-network authorization for the single-use Brim virtual cards we issue on release. */
@Service
public class CardRedemptionService {

    public enum Scenario { CORRECT, WRONG_MERCHANT, WRONG_AMOUNT, EXPIRED }

    public record Redemption(boolean approved, String message, String reference, String fallbackMethod,
                             String cardStatus) {
    }

    private final PaymentRepository payments;
    private final VirtualCardRepository virtualCards;
    private final CardTransactionRepository cardTransactions;
    private final BankAdapter bank;
    private final PipelineRunner runner;
    private final AuditService audit;

    public CardRedemptionService(PaymentRepository payments, VirtualCardRepository virtualCards,
                                 CardTransactionRepository cardTransactions, BankAdapter bank,
                                 PipelineRunner runner, AuditService audit) {
        this.payments = payments;
        this.virtualCards = virtualCards;
        this.cardTransactions = cardTransactions;
        this.bank = bank;
        this.runner = runner;
        this.audit = audit;
    }

    @Transactional
    public Redemption redeem(User user, String paymentId, String scenarioText, String merchantId, BigDecimal amount) {
        Payment payment = payments.get(paymentId);
        if (payment.getMethod() != PaymentMethod.CARD) {
            throw new BadRequestException("Payment " + paymentId + " is not a card payment");
        }
        if (payment.getStatus() != PaymentStatus.RELEASED && payment.getStatus() != PaymentStatus.SENT) {
            throw new BadRequestException("Payment " + paymentId + " is " + payment.getStatus() + ", not redeemable");
        }
        VirtualCard card = virtualCards.findByPayment_Id(paymentId)
                .orElseThrow(() -> new BadRequestException("No Brim virtual card was issued for " + paymentId));
        if (!"ISSUED".equals(card.getStatus())) {
            throw new BadRequestException("Brim virtual card is already " + card.getStatus().toLowerCase(Locale.ROOT));
        }

        Scenario scenario = scenarioText == null || scenarioText.isBlank()
                ? Scenario.CORRECT
                : parseScenario(scenarioText);
        String expectedMerchant = card.getMerchantLock() == null ? null : card.getMerchantLock().getId();
        String attemptedMerchant = switch (scenario) {
            case WRONG_MERCHANT -> "OTHER-MERCHANT";
            default -> merchantId == null ? expectedMerchant : merchantId;
        };
        BigDecimal attemptedAmount = switch (scenario) {
            case WRONG_AMOUNT -> card.getAmountLimit() == null ? null : card.getAmountLimit().add(BigDecimal.ONE);
            default -> amount == null ? card.getAmountLimit() : amount;
        };
        boolean expired = scenario == Scenario.EXPIRED
                || (card.getExpiresAt() != null && OffsetDateTime.now().isAfter(card.getExpiresAt()));
        boolean merchantOk = expectedMerchant != null && expectedMerchant.equals(attemptedMerchant);
        boolean amountOk = attemptedAmount != null && attemptedAmount.compareTo(card.getAmountLimit()) == 0;

        Invoice invoice = payment.getInvoice();
        if (!expired && merchantOk && amountOk) {
            card.setStatus("USED");
            virtualCards.save(card);

            CardTransaction transaction = new CardTransaction();
            transaction.setId("CT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
            transaction.setCardId(String.valueOf(card.getId()));
            transaction.setCardholder(user);
            transaction.setPostedDate(LocalDate.now());
            transaction.setMerchantDescriptor(card.getMerchantLock() == null
                    ? "Vendor"
                    : card.getMerchantLock().getLegalName());
            transaction.setAmount(attemptedAmount);
            transaction.setCurrency(payment.getCurrency());
            transaction.setAmountCad(payment.getAmountCad());
            transaction.setSource("ap_virtual_card");
            transaction.setReceiptAttached(true);
            transaction.setMemo("Brim virtual card redemption");
            transaction.setPayment(payment);
            transaction.setInvoice(invoice);
            cardTransactions.save(transaction);

            audit.record(Actor.of(user), invoice.getId(), payment.getId(), "card.redeemed",
                    NodeName.PAYMENT_RELEASE.name(), null, null,
                    Map.of("card", card.getMaskedNumber(), "merchant", attemptedMerchant,
                            "amount", attemptedAmount));

            sendIfNeeded(payment);
            runner.enqueue(invoice, NodeName.PAYMENT_RELEASE);
            return new Redemption(true, "Card redeemed for " + attemptedMerchant, payment.getReference(), null, "USED");
        }

        String reasonCode = expired ? "EXPIRED_CARD" : !merchantOk ? "MERCHANT_MISMATCH" : "AMOUNT_MISMATCH";
        String message = expired
                ? "Card declined: it has expired"
                : !merchantOk
                    ? "Card declined: merchant does not match the vendor lock"
                    : "Card declined: amount does not match the card limit";
        audit.record(Actor.of(user), invoice.getId(), payment.getId(), "card.declined",
                NodeName.PAYMENT_RELEASE.name(), null, null,
                Map.of("reasonCode", reasonCode, "card", card.getMaskedNumber(),
                        "attemptedMerchant", attemptedMerchant, "attemptedAmount", attemptedAmount));

        // One fallback: the payment goes out as an EFT with a fresh reference.
        card.setStatus("DECLINED");
        virtualCards.save(card);
        payment.setMethod(PaymentMethod.EFT);
        payment.setReference(null);
        sendIfNeeded(payment);
        audit.record(Actor.of(user), invoice.getId(), payment.getId(), "payment.fallback_eft",
                NodeName.PAYMENT_RELEASE.name(), null, null,
                Map.of("reasonCode", reasonCode, "reference", payment.getReference()));
        runner.enqueue(invoice, NodeName.PAYMENT_RELEASE);
        return new Redemption(false, message + " · payment sent by EFT instead", payment.getReference(), "EFT", "DECLINED");
    }

    private void sendIfNeeded(Payment payment) {
        payment.setReference(bank.send(payment));
        payment.setStatus(PaymentStatus.SENT);
        payment.setSentAt(OffsetDateTime.now());
        payments.save(payment);
    }

    private static Scenario parseScenario(String value) {
        try {
            return Scenario.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown redemption scenario: " + value);
        }
    }
}
