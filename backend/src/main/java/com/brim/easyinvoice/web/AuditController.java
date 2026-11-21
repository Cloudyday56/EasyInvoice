package com.brim.easyinvoice.web;

import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.audit.AuditHasher;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final CurrentUser currentUser;
    private final AuditService audit;
    private final AuthorizationService authorization;
    private final InvoiceRepository invoices;

    public AuditController(CurrentUser currentUser, AuditService audit, AuthorizationService authorization,
                           InvoiceRepository invoices) {
        this.currentUser = currentUser;
        this.audit = audit;
        this.authorization = authorization;
        this.invoices = invoices;
    }

    @GetMapping
    public List<AuditEvent> latest(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                   @RequestParam(defaultValue = "200") int limit) {
        authorization.authorize(currentUser.require(userId), Action.VIEW_ADMIN_SCREENS, null);
        return audit.latest(Math.min(limit, 1000));
    }

    /** Re-computes every hash in order. Any edited, deleted or reordered row shows up here. */
    @GetMapping("/verify")
    public AuditHasher.VerifyResult verify(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        authorization.authorize(currentUser.require(userId), Action.VIEW_ADMIN_SCREENS, null);
        return audit.verifyChain();
    }

    /** Whole log for Admins; one invoice's events for anyone who may see that invoice. */
    @GetMapping("/export.csv")
    public ResponseEntity<String> export(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                         @RequestParam(required = false) String invoiceId) {
        User user = currentUser.require(userId);
        if (invoiceId == null) {
            authorization.authorize(user, Action.VIEW_ADMIN_SCREENS, null);
        } else {
            authorization.authorize(user, Action.VIEW_INVOICE, invoices.get(invoiceId));
        }
        String fileName = invoiceId == null ? "audit-log.csv" : "audit-" + invoiceId + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(audit.exportCsv(invoiceId));
    }
}
