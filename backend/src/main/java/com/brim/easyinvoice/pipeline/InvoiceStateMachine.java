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
 * The only class that changes Invoice.status. Every change writes an audit event,
 * so no caller can forget to. Callers pass an invoice loaded inside their transaction.
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
        InvoiceStatus from = invoice.getStatus();
        if (from == to) return;
        if (from.isTerminal()) {
            throw new IllegalStateException("Invoice " + invoice.getId() + " is " + from + " (terminal) and cannot move to " + to);
        }
        change(invoice, to, null, null, null);
        audit.record(actor, invoice.getId(), null, "status.changed", node, from.name(), to.name(), payload);
    }

    /** Parks the invoice in an exception queue and remembers where it goes back in. */
    public void hold(Invoice invoice, HoldQueue queue, String reasonCode, String message, Actor actor, String node) {
        InvoiceStatus from = invoice.getStatus();
        NodeName resumeAt = queue.resumeNode();
        change(invoice, InvoiceStatus.ON_HOLD, queue.name(), message, resumeAt.name());

        Map<String, Object> payload = new HashMap<>();
        payload.put("queue", queue.name());
        payload.put("reasonCode", reasonCode);
        payload.put("message", message);
        payload.put("resumeNode", resumeAt.name());
        audit.record(actor, invoice.getId(), null, "invoice.held", node, from.name(),
                InvoiceStatus.ON_HOLD.name(), payload);
        notifications.notifyAdmins("exception_queue", invoice,
                invoice.getId() + " is in " + queue.label() + ": " + message);
    }

    /** Clears the hold. The caller then queues the resume node. */
    public NodeName releaseHold(Invoice invoice, Actor actor, String reason) {
        return releaseHold(invoice, actor, reason, Map.of());
    }

    /** Clears the hold, adding extra facts (e.g. overridden fraud signals) to the audit event. */
    public NodeName releaseHold(Invoice invoice, Actor actor, String reason, Map<String, ?> details) {
        InvoiceStatus from = invoice.getStatus();
        String queue = invoice.getHoldQueue();
        NodeName resumeAt = NodeName.valueOf(invoice.getResumeNode());
        InvoiceStatus to = resumeAt.statusBefore();
        change(invoice, to, null, null, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("queue", String.valueOf(queue));
        payload.put("reason", reason);
        payload.put("resumeNode", resumeAt.name());
        payload.putAll(details);
        audit.record(actor, invoice.getId(), null, "hold.resolved", resumeAt.name(), from.name(), to.name(), payload);
        return resumeAt;
    }

    public void reject(Invoice invoice, Actor actor, String reason) {
        moveTo(invoice, InvoiceStatus.REJECTED, actor, null, Map.of("reason", reason));
        notifications.notify(invoice.getRequester(), "rejected", invoice,
                invoice.getId() + " was rejected: " + reason);
    }

    private void change(Invoice invoice, InvoiceStatus status, String holdQueue, String holdReason, String resumeNode) {
        invoice.setStatus(status);
        invoice.setHoldQueue(holdQueue);
        invoice.setHoldReason(holdReason);
        invoice.setResumeNode(resumeNode);
        invoices.save(invoice);
    }
}
