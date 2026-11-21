package com.brim.easyinvoice.intake;

import com.brim.easyinvoice.model.DocumentChannel;
import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceNumber;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.PipelineRunner;
import com.brim.easyinvoice.repo.InvoiceNumberRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.SourceDocumentRepository;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import com.brim.easyinvoice.security.ForbiddenException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The two entry points of the Intake gateway: UI upload and the AP inbox.
 * Both store the file under its SHA-256, record a source_document, create a draft invoice
 * and queue the first node. The idempotency key (email id or "upload" + file hash) means
 * the same file is never turned into two drafts.
 */
@Service
@Transactional(noRollbackFor = ForbiddenException.class)
public class IntakeService {

    /** Result of an intake: the invoice, and whether it was new or already known. */
    public record IntakeResult(String invoiceId, boolean duplicate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InboxEmail(String email_id, String from_name, String from_address, String subject,
                      String received_at, String body, List<String> attachments) {
    }

    private final SourceDocumentRepository documents;
    private final InvoiceRepository invoices;
    private final InvoiceNumberRepository invoiceNumbers;
    private final FileStorage storage;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final PipelineRunner runner;
    private final ObjectMapper json;
    private final Path dataDir;

    public IntakeService(SourceDocumentRepository documents, InvoiceRepository invoices,
                         InvoiceNumberRepository invoiceNumbers, FileStorage storage,
                         AuthorizationService authorization, AuditService audit, PipelineRunner runner,
                         ObjectMapper json, @Value("${app.data-dir}") String dataDir) {
        this.documents = documents;
        this.invoices = invoices;
        this.invoiceNumbers = invoiceNumbers;
        this.storage = storage;
        this.authorization = authorization;
        this.audit = audit;
        this.runner = runner;
        this.json = json;
        this.dataDir = Path.of(dataDir);
    }

    public IntakeResult upload(User user, MultipartFile file) {
        authorization.authorize(user, Action.UPLOAD_INVOICE, null);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String contentType = file.getContentType() != null ? file.getContentType() : guessContentType(fileName);
        return intake(DocumentChannel.UPLOAD, null, fileName, contentType, bytes, null, null, null,
                OffsetDateTime.now(), user);
    }

    /** Reads data/inbox/emails.json; one draft per attachment (or one per email with no attachment). */
    public Map<String, Integer> pollInbox(User user) {
        authorization.authorize(user, Action.RUN_ADMIN_TOOLS, null);
        List<InboxEmail> emails = readInbox();
        int created = 0;
        int skipped = 0;
        for (InboxEmail email : emails) {
            String sender = email.from_name() + " <" + email.from_address() + ">";
            OffsetDateTime receivedAt = LocalDateTime.parse(email.received_at()).atZone(ZoneId.of("America/Toronto"))
                    .toOffsetDateTime();
            List<String> attachments = email.attachments() == null ? List.of() : email.attachments();
            if (attachments.isEmpty()) {
                IntakeResult r = intake(DocumentChannel.EMAIL, email.email_id(), null, null, null, sender, email.subject(),
                        email.body(), receivedAt, null);
                if (r.duplicate()) skipped++; else created++;
            }
            for (String attachment : attachments) {
                byte[] bytes = readFile(dataDir.resolve("incoming_invoices").resolve(attachment));
                IntakeResult r = intake(DocumentChannel.EMAIL, email.email_id(), attachment, guessContentType(attachment), bytes,
                        sender, email.subject(), email.body(), receivedAt, null);
                if (r.duplicate()) skipped++; else created++;
            }
        }
        audit.record(Actor.of(user), null, null, "inbox.polled", NodeName.INTAKE_GATEWAY.name(), null, null,
                Map.of("emails", emails.size(), "created", created, "alreadyKnown", skipped));
        return Map.of("emails", emails.size(), "created", created, "alreadyKnown", skipped);
    }

    private IntakeResult intake(DocumentChannel channel, String emailId, String fileName, String contentType, byte[] bytes,
                                String sender, String subject, String body, OffsetDateTime receivedAt,
                                User uploader) {
        String sha = bytes == null ? null : sha256(bytes);
        String idempotencyKey = (emailId == null ? "upload" : emailId) + ":" + (sha == null ? "no-attachment" : sha);

        Optional<SourceDocument> known = documents.findByIdempotencyKey(idempotencyKey);
        if (known.isPresent()) {
            String existingInvoice = invoices.findBySourceDocument_Id(known.get().getId()).map(Invoice::getId).orElse(null);
            return new IntakeResult(existingInvoice, true);
        }

        SourceDocument document = new SourceDocument();
        document.setChannel(channel);
        document.setEmailId(emailId);
        document.setIdempotencyKey(idempotencyKey);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setSizeBytes(bytes == null ? null : (long) bytes.length);
        document.setSha256(sha);
        document.setStoragePath(bytes == null ? null : storage.save(bytes, sha, extensionOf(fileName)));
        document.setSender(sender);
        document.setSubject(subject);
        document.setBody(body);
        document.setReceivedAt(receivedAt);
        document.setUploadedBy(uploader);
        documents.save(document);

        // The uploader is the requester; their department pays. Email has no requester (routing will ask).
        Invoice invoice = new Invoice();
        invoice.setId(String.format("EI-%05d", invoiceNumbers.save(new InvoiceNumber()).getId()));
        invoice.setSourceDocument(document);
        invoice.setRequester(uploader);
        invoice.setDepartment(uploader == null ? null : uploader.getDepartment());
        invoice.setReceivedDate(LocalDate.now());
        invoice.setStatus(InvoiceStatus.RECEIVED);
        invoices.save(invoice);
        String invoiceId = invoice.getId();

        Actor actor = uploader == null ? Actor.SYSTEM : Actor.of(uploader);
        audit.record(actor, invoiceId, null, "invoice.received", NodeName.INTAKE_GATEWAY.name(), null, "RECEIVED",
                Map.of("channel", channel.name(), "file", fileName == null ? "(none)" : fileName,
                        "sha256", sha == null ? "" : sha));
        runner.enqueue(invoice, NodeName.INTAKE_GATEWAY);
        return new IntakeResult(invoiceId, false);
    }

    private List<InboxEmail> readInbox() {
        return json.readValue(readFile(dataDir.resolve("inbox").resolve("emails.json")),
                new TypeReference<List<InboxEmail>>() {
                });
    }

    private static byte[] readFile(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
