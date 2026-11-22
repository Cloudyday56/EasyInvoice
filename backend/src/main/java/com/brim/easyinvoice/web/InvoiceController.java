package com.brim.easyinvoice.web;

import com.brim.easyinvoice.web.dto.PaymentDto;
import com.brim.easyinvoice.web.dto.InvoiceLineDto;
import com.brim.easyinvoice.web.dto.InvoiceDto;
import com.brim.easyinvoice.web.dto.FlagDto;
import com.brim.easyinvoice.web.dto.ApprovalStepDto;
import org.springframework.transaction.annotation.Transactional;
import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.intake.FileStorage;
import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.CardTransaction;
import com.brim.easyinvoice.model.DocumentChannel;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.PaymentMethod;
import com.brim.easyinvoice.model.PaymentStatus;
import com.brim.easyinvoice.model.Payment;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.model.VirtualCard;
import com.brim.easyinvoice.pipeline.HumanActionService;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.CardTransactionRepository;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceLineRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.PaymentRepository;
import com.brim.easyinvoice.repo.SourceDocumentRepository;
import com.brim.easyinvoice.repo.VirtualCardRepository;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    /** The invoice page: everything in one response. */
    public record InvoiceDetail(
            InvoiceDto invoice,
            List<InvoiceLineDto> lines,
            List<FlagDto> flags,
            List<ApprovalStepDto> approvalSteps,
            List<PaymentDto> payments,
            CardInfo card,
            List<AuditEvent> auditEvents,
            DocumentInfo document,
            List<PipelineProgress.NodeProgress> pipeline,
            List<Action> allowedActions) {
    }

    /** Source document without the server's file path. */
    public record DocumentInfo(long id, DocumentChannel channel, String fileName, String contentType, String sender,
                               String subject, String body) {
        static DocumentInfo of(SourceDocument d) {
            return new DocumentInfo(d.getId(), d.getChannel(), d.getFileName(), d.getContentType(), d.getSender(),
                    d.getSubject(), d.getBody());
        }
    }

    public record ReasonRequest(String reason) {
    }

    /** The issued Brim card for this invoice, if any. Card details stay masked. */
    public record CardInfo(
            String paymentId,
            String status,
            String maskedNumber,
            BigDecimal amountLimit,
            String currency,
            String merchantId,
            String merchantName,
            OffsetDateTime expiresAt,
            boolean singleUse,
            String transactionId,
            String redemptionReference,
            String fallbackMethod,
            boolean canRedeem) {
    }

    private final CurrentUser currentUser;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final FlagRepository flags;
    private final ApprovalStepRepository steps;
    private final PaymentRepository payments;
    private final VirtualCardRepository virtualCards;
    private final CardTransactionRepository cardTransactions;
    private final SourceDocumentRepository documents;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final IntakeService intake;
    private final HumanActionService actions;
    private final FileStorage storage;
    private final ObjectMapper json;

    public InvoiceController(CurrentUser currentUser, InvoiceRepository invoices, InvoiceLineRepository invoiceLines,
                             FlagRepository flags, ApprovalStepRepository steps, PaymentRepository payments,
                             VirtualCardRepository virtualCards, CardTransactionRepository cardTransactions,
                             SourceDocumentRepository documents,
                             AuthorizationService authorization, AuditService audit, IntakeService intake,
                             HumanActionService actions, FileStorage storage, ObjectMapper json) {
        this.currentUser = currentUser;
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.flags = flags;
        this.steps = steps;
        this.payments = payments;
        this.virtualCards = virtualCards;
        this.cardTransactions = cardTransactions;
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
    @Transactional(readOnly = true)
    public List<InvoiceDto> list(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        User user = currentUser.require(userId);
        Map<String, List<ApprovalStep>> stepsByInvoice = steps.allGroupedByInvoice();
        Map<String, List<Flag>> flagsByInvoice = flags.allGroupedByInvoice();
        return invoices.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(inv -> authorization.canView(user, inv, stepsByInvoice.getOrDefault(inv.getId(), List.of())))
                .map(inv -> summarize(user, inv, stepsByInvoice.getOrDefault(inv.getId(), List.of()),
                        flagsByInvoice.getOrDefault(inv.getId(), List.of())))
                .toList();
    }

    /** The invoice row plus what the UI needs before the detail loads: flags, this user's actions, who is up next. */
    private InvoiceDto summarize(User user, Invoice invoice, List<ApprovalStep> invoiceSteps, List<Flag> invoiceFlags) {
        return InvoiceDto.from(invoice).with(
                invoiceFlags.stream().map(FlagDto::from).toList(),
                authorization.allowedActions(user, invoice, invoiceSteps).stream().map(Enum::name).toList(),
                AuthorizationService.currentStep(invoiceSteps).map(ApprovalStep::getAssignedUserId).orElse(null),
                invoice.getPayments().stream().filter(p -> p.getStatus() == PaymentStatus.SCHEDULED)
                        .findFirst().map(Payment::getId).orElse(null));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public InvoiceDetail detail(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                @PathVariable String id) {
        User user = currentUser.require(userId);
        Invoice invoice = invoices.get(id);
        authorization.authorize(user, Action.VIEW_INVOICE, invoice);

        List<ApprovalStep> invoiceSteps = steps.forInvoice(id);
        List<AuditEvent> events = audit.forInvoice(id);
        boolean canViewAudit = AuthorizationService.decide(user, Action.VIEW_ADMIN_SCREENS, null, List.of()).allowed();
        SourceDocument doc = invoice.getSourceDocument();
        List<Flag> invoiceFlags = flags.findByInvoice_IdOrderById(id);
        return new InvoiceDetail(summarize(user, invoice, invoiceSteps, invoiceFlags),
                invoiceLines.findByInvoice_IdOrderByLineNo(id).stream().map(InvoiceLineDto::from).toList(),
                invoiceFlags.stream().map(FlagDto::from).toList(),
                invoiceSteps.stream().map(ApprovalStepDto::from).toList(),
                payments.findByInvoice_IdOrderById(id).stream().map(PaymentDto::from).toList(),
                cardFor(user, id),
                canViewAudit ? events : List.of(), doc == null ? null : DocumentInfo.of(doc),
                PipelineProgress.compute(invoice, events, json),
                authorization.allowedActions(user, invoice, invoiceSteps));
    }

    /** Finds the card linked to any payment on this invoice, even after an EFT fallback. */
    private CardInfo cardFor(User user, String invoiceId) {
        List<Payment> invoicePayments = payments.findByInvoice_IdOrderById(invoiceId);
        for (Payment payment : invoicePayments) {
            VirtualCard card = virtualCards.findByPayment_Id(payment.getId()).orElse(null);
            if (card == null) continue;
            CardTransaction transaction = cardTransactions.findByPayment_Id(payment.getId()).stream().findFirst().orElse(null);
            boolean canRedeem = "ISSUED".equals(card.getStatus())
                    && (payment.getStatus() == PaymentStatus.RELEASED || payment.getStatus() == PaymentStatus.SENT)
                    && AuthorizationService.decide(user, Action.RUN_ADMIN_TOOLS, null, List.of()).allowed();
            return new CardInfo(
                    payment.getId(),
                    card.getStatus(),
                    card.getMaskedNumber(),
                    card.getAmountLimit(),
                    payment.getCurrency(),
                    card.getMerchantLock() == null ? null : card.getMerchantLock().getId(),
                    card.getMerchantLock() == null ? null : card.getMerchantLock().getLegalName(),
                    card.getExpiresAt(),
                    card.isSingleUse(),
                    transaction == null ? null : transaction.getId(),
                    payment.getReference(),
                    payment.getMethod() == PaymentMethod.EFT ? "EFT" : null,
                    canRedeem);
        }

        // Imported history has CARD payments and BRIM-VC references but no VirtualCard rows.
        // Show a read-only, already-used card view so "View card" still works for paid history.
        return invoicePayments.stream()
                .filter(payment -> payment.getMethod() == PaymentMethod.CARD)
                .filter(payment -> payment.getStatus() == PaymentStatus.SETTLED || payment.getStatus() == PaymentStatus.SENT)
                .findFirst()
                .map(InvoiceController::historicalCard)
                .orElse(null);
    }

    /** Presentation-only card view for imported card history; never redeemable. */
    private static CardInfo historicalCard(Payment payment) {
        String reference = payment.getReference();
        String lastFour = reference != null && reference.length() >= 4
                ? reference.substring(reference.length() - 4)
                : "0000";
        Vendor vendor = payment.getVendor();
        return new CardInfo(
                payment.getId(),
                "USED",
                "**** **** **** " + lastFour,
                payment.getAmount(),
                payment.getCurrency(),
                vendor == null ? null : vendor.getId(),
                vendor == null ? null : vendor.getLegalName(),
                null,
                true,
                null,
                reference,
                null,
                false);
    }

    /** The original file, for the document viewer. */
    @GetMapping("/{id}/document")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> document(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                           @PathVariable String id) {
        User user = currentUser.require(userId);
        Invoice invoice = invoices.get(id);
        authorization.authorize(user, Action.VIEW_INVOICE, invoice);
        SourceDocument doc = invoice.getSourceDocument();
        if (doc == null) throw new NotFoundException("Invoice " + id + " has no document");
        if (!doc.hasFile()) throw new NotFoundException("Document has no file");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .body(storage.read(doc.getStoragePath()));
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
