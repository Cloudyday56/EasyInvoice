package com.brim.easyinvoice.web;

import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.intake.FileStorage;
import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceLine;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.pipeline.HumanActionService;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.SourceDocumentRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    /** The invoice page: everything in one response. */
    public record InvoiceDetail(
            Invoice invoice,
            List<InvoiceLine> lines,
            List<Flag> flags,
            List<ApprovalStep> approvalSteps,
            List<Payment> payments,
            List<AuditEvent> auditEvents,
            DocumentInfo document,
            List<PipelineProgress.NodeProgress> pipeline,
            List<Action> allowedActions) {
    }

    /** Source document without the server's file path. */
    public record DocumentInfo(long id, String channel, String fileName, String contentType, String sender,
                               String subject, String body) {
        static DocumentInfo of(SourceDocument d) {
            return new DocumentInfo(d.id(), d.channel(), d.fileName(), d.contentType(), d.sender(), d.subject(), d.body());
        }
    }

    public record ReasonRequest(String reason) {
    }

    private final CurrentUser currentUser;
    private final InvoiceRepository invoices;
    private final ApprovalStepRepository steps;
    private final PaymentRepository payments;
    private final SourceDocumentRepository documents;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final IntakeService intake;
    private final HumanActionService actions;
    private final FileStorage storage;
    private final ObjectMapper json;

    public InvoiceController(CurrentUser currentUser, InvoiceRepository invoices, ApprovalStepRepository steps,
                             PaymentRepository payments, SourceDocumentRepository documents,
                             AuthorizationService authorization, AuditService audit, IntakeService intake,
                             HumanActionService actions, FileStorage storage, ObjectMapper json) {
        this.currentUser = currentUser;
        this.invoices = invoices;
        this.steps = steps;
        this.payments = payments;
        this.documents = documents;
        this.authorization = authorization;
        this.audit = audit;
        this.intake = intake;
        this.actions = actions;
        this.storage = storage;
        this.json = json;
    }

    /** Only the invoices this user may see (same rule as the detail page, applied to each row). */
    @GetMapping
    public List<Invoice> list(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        User user = currentUser.require(userId);
        Map<String, List<ApprovalStep>> stepsByInvoice = steps.allGroupedByInvoice();
        return invoices.findAll().stream()
                .filter(inv -> authorization.canView(user, inv, stepsByInvoice.getOrDefault(inv.id(), List.of())))
                .toList();
    }

    @GetMapping("/{id}")
    public InvoiceDetail detail(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                @PathVariable String id) {
        User user = currentUser.require(userId);
        Invoice invoice = invoices.get(id);
        authorization.authorize(user, Action.VIEW_INVOICE, invoice);

        List<ApprovalStep> invoiceSteps = steps.forInvoice(id);
        List<AuditEvent> events = audit.forInvoice(id);
        SourceDocument doc = invoice.sourceDocumentId() == null ? null : documents.get(invoice.sourceDocumentId());
        return new InvoiceDetail(invoice, invoices.lines(id), invoices.flags(id), invoiceSteps,
                payments.forInvoice(id), events, doc == null ? null : DocumentInfo.of(doc),
                PipelineProgress.compute(invoice, events, json),
                authorization.allowedActions(user, invoice, invoiceSteps));
    }

    /** The original file, for the document viewer. */
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                           @PathVariable String id) {
        User user = currentUser.require(userId);
        Invoice invoice = invoices.get(id);
        authorization.authorize(user, Action.VIEW_INVOICE, invoice);
        if (invoice.sourceDocumentId() == null) throw new NotFoundException("Invoice " + id + " has no document");
        SourceDocument doc = documents.get(invoice.sourceDocumentId());
        if (!doc.hasFile()) throw new NotFoundException("Document has no file");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .body(storage.read(doc.storagePath()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IntakeService.IntakeResult upload(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                             @RequestParam("file") MultipartFile file) {
        return intake.upload(currentUser.require(userId), file);
    }

    @PostMapping("/{id}/approve")
    public void approve(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                        @PathVariable String id, @RequestBody(required = false) ReasonRequest body) {
        actions.approve(currentUser.require(userId), id, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/reject")
    public void reject(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                       @PathVariable String id, @RequestBody ReasonRequest body) {
        actions.reject(currentUser.require(userId), id, body.reason());
    }

    @PostMapping("/{id}/return")
    public void returnToAp(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                           @PathVariable String id, @RequestBody ReasonRequest body) {
        actions.returnToAp(currentUser.require(userId), id, body.reason());
    }

    @PostMapping("/{id}/resume")
    public void resume(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                       @PathVariable String id, @RequestBody ReasonRequest body) {
        actions.resumeFromHold(currentUser.require(userId), id, body.reason());
    }

    @PostMapping("/{id}/reject-hold")
    public void rejectHold(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                           @PathVariable String id, @RequestBody ReasonRequest body) {
        actions.rejectFromHold(currentUser.require(userId), id, body.reason());
    }
}
