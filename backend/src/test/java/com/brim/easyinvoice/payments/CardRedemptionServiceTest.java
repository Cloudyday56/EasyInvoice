package com.brim.easyinvoice.payments;

import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.CardTransaction;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.model.VirtualCard;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.PipelineRunner;
import com.brim.easyinvoice.repo.CardTransactionRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.VirtualCardRepository;
import com.brim.easyinvoice.web.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the mock card network: merchant lock, amount lock, single use and EFT fallback. */
@ExtendWith(MockitoExtension.class)
class CardRedemptionServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private VirtualCardRepository virtualCards;
    @Mock private CardTransactionRepository cardTransactions;
    @Mock private BankAdapter bank;
    @Mock private PipelineRunner runner;
    @Mock private AuditService audit;

    private CardRedemptionService service;
    private Payment payment;
    private VirtualCard card;
    private Vendor vendor;
    private Invoice invoice;
    private User user;

    @BeforeEach
    void setUp() {
        vendor = new Vendor();
        vendor.setId("V1");
        vendor.setLegalName("Acme Corp");

        invoice = new Invoice();
        invoice.setId("I1");
        invoice.setVendor(vendor);

        payment = new Payment();
        payment.setId("P1");
        payment.setInvoice(invoice);
        payment.setVendor(vendor);
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(PaymentStatus.RELEASED);
        payment.setCurrency("CAD");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setAmountCad(new BigDecimal("100.00"));

        card = new VirtualCard();
        card.setId(1L);
        card.setPayment(payment);
        card.setMaskedNumber("**** **** **** 4821");
        card.setAmountLimit(new BigDecimal("100.00"));
        card.setMerchantLock(vendor);
        card.setExpiresAt(OffsetDateTime.now().plusDays(7));
        card.setSingleUse(true);
        card.setStatus("ISSUED");

        user = new User();
        user.setId("U1");
        user.setRole(Role.ADMIN);

        when(payments.get("P1")).thenReturn(payment);
        when(virtualCards.findByPayment_Id("P1")).thenReturn(Optional.of(card));

        service = new CardRedemptionService(payments, virtualCards, cardTransactions, bank, runner, audit);
    }

    @Test
    void correctMerchantAndAmountUsesCardAndSendsPayment() {
        when(bank.send(payment)).thenReturn("BRIM-VC-test");
        CardRedemptionService.Redemption result = service.redeem(user, "P1", "CORRECT", null, null);

        assertThat(result.approved()).isTrue();
        assertThat(result.cardStatus()).isEqualTo("USED");
        assertThat(result.reference()).isEqualTo("BRIM-VC-test");
        assertThat(card.getStatus()).isEqualTo("USED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);

        ArgumentCaptor<CardTransaction> transaction = ArgumentCaptor.forClass(CardTransaction.class);
        verify(cardTransactions).save(transaction.capture());
        assertThat(transaction.getValue().getSource()).isEqualTo("ap_virtual_card");
        assertThat(transaction.getValue().getMerchantDescriptor()).isEqualTo("Acme Corp");
        assertThat(transaction.getValue().getAmount()).isEqualByComparingTo("100.00");

        ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);
        verify(audit).record(any(), eq("I1"), eq("P1"), actions.capture(),
                eq(NodeName.PAYMENT_RELEASE.name()), isNull(), isNull(), anyMap());
        assertThat(actions.getValue()).isEqualTo("card.redeemed");
        verify(runner).enqueue(invoice, NodeName.PAYMENT_RELEASE);
    }

    @Test
    void wrongMerchantDeclinesCardAndFallsBackToEft() {
        when(bank.send(payment)).thenAnswer(invocation ->
                payment.getMethod() == PaymentMethod.CARD ? "BRIM-VC-test" : "BRIM-EFT-test");
        CardRedemptionService.Redemption result = service.redeem(user, "P1", "WRONG_MERCHANT", null, null);

        assertThat(result.approved()).isFalse();
        assertThat(result.fallbackMethod()).isEqualTo("EFT");
        assertThat(result.reference()).isEqualTo("BRIM-EFT-test");
        assertThat(card.getStatus()).isEqualTo("DECLINED");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.EFT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SENT);
        verify(cardTransactions, never()).save(any());

        ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);
        verify(audit, times(2)).record(any(), eq("I1"), eq("P1"), actions.capture(),
                eq(NodeName.PAYMENT_RELEASE.name()), isNull(), isNull(), anyMap());
        assertThat(actions.getAllValues()).containsExactly("card.declined", "payment.fallback_eft");
        verify(runner).enqueue(invoice, NodeName.PAYMENT_RELEASE);
    }

    @Test
    void usedCardCannotBeRedeemedAgain() {
        card.setStatus("USED");

        assertThatThrownBy(() -> service.redeem(user, "P1", "CORRECT", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already used");
        verify(cardTransactions, never()).save(any());
    }
}
