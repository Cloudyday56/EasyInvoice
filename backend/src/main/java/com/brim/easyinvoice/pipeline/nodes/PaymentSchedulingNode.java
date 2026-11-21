package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
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
    private final VendorRepository vendors;

    public PaymentSchedulingNode(PaymentRepository payments, VendorRepository vendors) {
        this.payments = payments;
        this.vendors = vendors;
    }

    @Override
    public NodeName name() {
        return NodeName.PAYMENT_SCHEDULING;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        Optional<Payment> existing = payments.activeForInvoice(invoice.id());
        if (existing.isPresent()) {
            return StageResult.pass("Payment " + existing.get().id() + " already scheduled"); // idempotent
        }

        Vendor vendor = vendors.findById(invoice.vendorId()).orElse(null);
        if (vendor == null || !vendor.isActive()) {
            return StageResult.block(HoldQueue.PAYMENT_EXCEPTIONS, "VENDOR_NOT_ACTIVE",
                    "Vendor is no longer active; payment not scheduled");
        }

        String method = vendor.acceptsCardPayment() ? "CARD" : "EFT";
        int terms = vendor.paymentTermsDays() == null ? 30 : vendor.paymentTermsDays();
        LocalDate payDate = invoice.dueDate() != null ? invoice.dueDate() : LocalDate.now().plusDays(terms);

        String paymentId = payments.nextPaymentId();
        payments.insertScheduled(paymentId, invoice.id(), vendor.id(), method, payDate,
                invoice.currency(), invoice.total(), invoice.totalCad());
        return StageResult.pass("Scheduled " + method + " payment " + paymentId + " for " + payDate);
    }
}
