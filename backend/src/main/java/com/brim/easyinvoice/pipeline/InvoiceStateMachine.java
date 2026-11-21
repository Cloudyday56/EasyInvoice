package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.notify.NotificationService;
import com.brim.easyinvoice.repo.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * The only class that changes invoices.status. Every change writes an audit event,
 * so no caller can forget to.
 */
@Service
public class InvoiceStateMachine {

    private final InvoiceRepository invoices;
    private final AuditService audit;
    private final NotificationService notifications;

    public InvoiceStateMachine(InvoiceRepository invoices, AuditService audit, NotificationService notifications) {
        this.invoices = invoices;
        this.audit = audit;
        this.notifications = notifications;
    }

    public void moveTo(Invoice invoice, InvoiceStatus to, Actor actor, String node, Map<String, ?> payload) {
        InvoiceStatus from = invoice.status();
        if (from == to) return;
        if (from.isTerminal()) {
            throw new IllegalStateException("Invoice " + invoice.id() + " is " + from + " (terminal) and cannot move to " + to);
        }
        invoices.updateStatus(invoice.id(), to, null, null, null);
        audit.record(actor, invoice.id(), null, "status.changed", node, from.name(), to.name(), payload);
    }

    /** Parks the invoice in an exception queue and remembers where it goes back in. */
    public void hold(Invoice invoice, HoldQueue queue, String reasonCode, String message, Actor actor, String node) {
        NodeName resumeAt = queue.resumeNode();
        invoices.updateStatus(invoice.id(), InvoiceStatus.ON_HOLD, queue.name(), message, resumeAt.name());
        Map<String, Object> payload = new HashMap<>();
        payload.put("queue", queue.name());
        payload.put("reasonCode", reasonCode);
        payload.put("message", message);
        payload.put("resumeNode", resumeAt.name());
        audit.record(actor, invoice.id(), null, "invoice.held", node, invoice.status().name(),
                InvoiceStatus.ON_HOLD.name(), payload);
        notifications.notifyAdmins("exception_queue", invoice.id(),
                invoice.id() + " is in " + queue.label() + ": " + message);
    }

    /** Clears the hold. The caller then queues the resume node. */
    public NodeName releaseHold(Invoice invoice, Actor actor, String reason) {
        NodeName resumeAt = NodeName.valueOf(invoice.resumeNode());
        InvoiceStatus to = resumeAt.statusBefore();
        invoices.updateStatus(invoice.id(), to, null, null, null);
        audit.record(actor, invoice.id(), null, "hold.resolved", resumeAt.name(), invoice.status().name(), to.name(),
                Map.of("queue", String.valueOf(invoice.holdQueue()), "reason", reason, "resumeNode", resumeAt.name()));
        return resumeAt;
    }

    public void reject(Invoice invoice, Actor actor, String reason) {
        moveTo(invoice, InvoiceStatus.REJECTED, actor, null, Map.of("reason", reason));
        notifications.notify(invoice.requesterUserId(), "rejected", invoice.id(),
                invoice.id() + " was rejected: " + reason);
    }
}
