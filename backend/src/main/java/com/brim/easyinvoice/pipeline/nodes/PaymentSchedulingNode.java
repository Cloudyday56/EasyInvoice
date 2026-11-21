package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.PaymentNumber;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.PaymentNumberRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.VendorRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Job: choose how and when to pay. Creates a SCHEDULED payment row.
 * Method: Brim virtual card if the vendor accepts cards, else EFT. Date: the due date.
 *
 * TODO pre-release gate: vendor still active (done), bank details unchanged since approval,
 *      no other payment for this invoice already SENT or SETTLED. Bank change -> Fraud hold.
 * TODO card eligibility: CAD/USD only, under a card limit (e.g. $25,000).
 */
@Component
public class PaymentSchedulingNode implements Node {

    private final PaymentRepository payments;
    private final PaymentNumberRepository paymentNumbers;
    private final VendorRepository vendors;

    public PaymentSchedulingNode(PaymentRepository payments, PaymentNumberRepository paymentNumbers,
                                 VendorRepository vendors) {
        this.payments = payments;
        this.paymentNumbers = paymentNumbers;
        this.vendors = vendors;
    }

    @Override
    public NodeName name() {
        return NodeName.PAYMENT_SCHEDULING;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        Optional<Payment> existing = payments.activeForInvoice(invoice.getId());
        if (existing.isPresent()) {
            return StageResult.pass("Payment " + existing.get().getId() + " already scheduled"); // idempotent
        }

        Vendor vendor = invoice.getVendorId() == null ? null : vendors.findById(invoice.getVendorId()).orElse(null);
        if (vendor == null || !vendor.isActive()) {
            return StageResult.block(HoldQueue.PAYMENT_EXCEPTIONS, "VENDOR_NOT_ACTIVE",
                    "Vendor is no longer active; payment not scheduled");
        }

        PaymentMethod method = vendor.isAcceptsCardPayment() ? PaymentMethod.CARD : PaymentMethod.EFT;
        int terms = vendor.getPaymentTermsDays() == null ? 30 : vendor.getPaymentTermsDays();
        LocalDate payDate = invoice.getDueDate() != null ? invoice.getDueDate() : LocalDate.now().plusDays(terms);

        Payment payment = new Payment();
        payment.setId(String.format("EP-%05d", paymentNumbers.save(new PaymentNumber()).getId()));
        payment.setIdempotencyKey(payment.getId());
        payment.setInvoice(invoice);
        payment.setVendor(vendor);
        payment.setMethod(method);
        payment.setScheduledDate(payDate);
        payment.setCurrency(invoice.getCurrency());
        payment.setAmount(invoice.getTotal());
        payment.setAmountCad(invoice.getTotalCad());
        payment.setStatus(PaymentStatus.SCHEDULED);
        payment.setPreparedBy("system");
        payments.save(payment);
        return StageResult.pass("Scheduled " + method + " payment " + payment.getId() + " for " + payDate);
    }
}
