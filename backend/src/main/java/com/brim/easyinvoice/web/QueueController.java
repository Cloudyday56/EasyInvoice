package com.brim.easyinvoice.web;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** The Admin's "Needs attention" inbox: every exception queue, sorted by due date. */
@RestController
public class QueueController {

    public record QueueView(String queue, String label, String resumeNode, List<Invoice> items) {
    }

    private final CurrentUser currentUser;
    private final InvoiceRepository invoices;
    private final AuthorizationService authorization;

    public QueueController(CurrentUser currentUser, InvoiceRepository invoices, AuthorizationService authorization) {
        this.currentUser = currentUser;
        this.invoices = invoices;
        this.authorization = authorization;
    }

    @GetMapping("/api/queues")
    public List<QueueView> queues(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        User user = currentUser.require(userId);
        authorization.authorize(user, Action.VIEW_ADMIN_SCREENS, null);

        List<Invoice> onHold = invoices.findOnHold(); // already sorted by due date
        List<QueueView> views = new ArrayList<>();
        for (HoldQueue queue : HoldQueue.values()) {
            List<Invoice> items = onHold.stream().filter(i -> queue.name().equals(i.getHoldQueue())).toList();
            views.add(new QueueView(queue.name(), queue.label(), queue.resumeNode().name(), items));
        }
        // Not a hold queue, but the design wants ignored mail visible so a misclassification can be rescued.
        List<Invoice> ignored = invoices.findByStatus(InvoiceStatus.IGNORED);
        views.add(new QueueView("IGNORED", "Ignored mail", null, ignored));
        return views;
    }
}
