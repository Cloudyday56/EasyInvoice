package com.brim.easyinvoice.web;

import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.Vendor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Imported card payments have no VirtualCard row, but still need a read-only card view. */
class InvoiceControllerHistoricalCardTest {

    @Test
    void historicalCardIsMaskedUsedAndNeverRedeemable() throws Exception {
        Vendor vendor = new Vendor();
        vendor.setId("V15");
        vendor.setLegalName("Northwind Ltd");

        Payment payment = new Payment();
        payment.setId("PAY-1");
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(PaymentStatus.SETTLED);
        payment.setReference("BRIM-VC-17166635");
        payment.setAmount(new BigDecimal("1234.56"));
        payment.setCurrency("CAD");
        payment.setVendor(vendor);

        Method method = InvoiceController.class.getDeclaredMethod("historicalCard", Payment.class);
        method.setAccessible(true);

        InvoiceController.CardInfo card = (InvoiceController.CardInfo) method.invoke(null, payment);

        assertThat(card.status()).isEqualTo("USED");
        assertThat(card.maskedNumber()).isEqualTo("**** **** **** 6635");
        assertThat(card.amountLimit()).isEqualByComparingTo("1234.56");
        assertThat(card.merchantId()).isEqualTo("V15");
        assertThat(card.merchantName()).isEqualTo("Northwind Ltd");
        assertThat(card.redemptionReference()).isEqualTo("BRIM-VC-17166635");
        assertThat(card.canRedeem()).isFalse();
        assertThat(card.transactionId()).isNull();
        assertThat(card.expiresAt()).isNull();
    }
}
