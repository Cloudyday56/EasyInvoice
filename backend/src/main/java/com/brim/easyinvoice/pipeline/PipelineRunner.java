package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.FlagSeverity;
import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceLine;
import com.brim.easyinvoice.model.InvoiceStatus;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceLineRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.SourceDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the pipeline. It owns:
 *  - ordering      (NodeName order: after a node passes, the next one is queued)
 *  - status        (through InvoiceStateMachine)
 *  - retries       (a node that throws is retried with exponential backoff, then parked)
 *  - exception queues (a BLOCK parks the invoice with hold_queue + resume_node)
 *  - audit writes  (one event per node result, plus one per status change)
 *
 * It is a simple loop over the jobs table. In production each node would be its own worker queue.
 */
@Service
public class PipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    /** Invoice fields a node may change through StageResult.patch. Status is NOT here on purpose. */
    private static final Set<String> PATCHABLE_FIELDS = Set.of(
            "documentType", "vendor", "vendorNameRaw", "vendorGstNumberRaw", "vendorInvoiceNumber", "canonicalInvoiceNumber",
            "invoiceDate", "dueDate", "currency", "subtotal", "gstHstAmount", "pstQstAmount", "total",
            "fxRateToCad", "totalCad", "department", "poNumberRaw", "purchaseOrder", "contract", "shipToProvince",
            "remitBankInstitution", "remitBankTransit", "remitBankAccount", "riskScore");

    private final Map<NodeName, Node> nodes = new EnumMap<>(NodeName.class);
    private final JobRepository jobs;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final FlagRepository flags;
    private final SourceDocumentRepository documents;
    private final InvoiceStateMachine stateMachine;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final long stepDelayMs;
    private final int maxAttempts;

    public PipelineRunner(List<Node> allNodes, JobRepository jobs, InvoiceRepository invoices,
                          InvoiceLineRepository invoiceLines, FlagRepository flags,
                          SourceDocumentRepository documents, InvoiceStateMachine stateMachine, AuditService audit,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.pipeline.step-delay-ms}") long stepDelayMs,
                          @Value("${app.pipeline.max-attempts}") int maxAttempts) {
        for (Node node : allNodes) {
            nodes.put(node.name(), node);
        }
        for (NodeName name : NodeName.values()) {
            if (!nodes.containsKey(name)) throw new IllegalStateException("No Node bean for " + name);
        }
        this.jobs = jobs;
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.flags = flags;
        this.documents = documents;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.stepDelayMs = stepDelayMs;
        this.maxAttempts = maxAttempts;
    }

    /** Puts an invoice on the line at the given node. Used by intake and by human actions. */
    public void enqueue(Invoice invoice, NodeName node) {
        jobs.enqueue(invoice, node, 0);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        jobs.requeueInterrupted();
    }

    /** Polls the jobs table and runs every job that is due. synchronized: one job at a time. */
    @Scheduled(fixedDelayString = "${app.pipeline.poll-ms}")
    public synchronized void tick() {
        while (true) {
            Job job = jobs.claimNext().orElse(null);
            if (job == null) return;
            try {
                runJob(job);
            } catch (Exception e) {
                // Should not happen (runJob handles node errors), but never let one bad job stop the loop.
                log.error("Runner failed on job {}", job.getId(), e);
                jobs.markFailed(job.getId(), e.toString());
            }
        }
    }

    /** Lets "reset demo" wipe the database without a job running at the same time. */
    public synchronized void pauseWhile(Runnable action) {
        action.run();
    }

    private void runJob(Job job) {
        Invoice invoice = invoices.get(job.getInvoiceId());
        if (invoice.getStatus().isTerminal() || invoice.getStatus() == InvoiceStatus.ON_HOLD) {
            jobs.markDone(job.getId());  // stale job: the invoice moved on (or was parked) meanwhile
            return;
        }
        invoices.updateCurrentNode(invoice.getId(), job.getNode().name());
        SourceDocument document = invoice.getSourceDocument();

        StageResult result;
        try {
            result = demoBlock(invoice, job.getNode(), document);
            if (result == null) result = skipIfOverridden(invoice, job.getNode());
            if (result == null) {
                result = nodes.get(job.getNode()).run(invoice, new NodeContext(document, job.getAttempts()));
            }
        } catch (Exception e) {
            handleCrash(job, invoice, e);
            return;
        }

        StageResult finalResult = result;
        transaction.executeWithoutResult(tx -> {
            // Reload inside the transaction so the entity is managed: its changes are saved when the transaction commits.
            apply(invoices.get(invoice.getId()), job.getNode(), finalResult);
            jobs.markDone(job.getId());
        });
    }

    /** Persists what the node returned and moves the invoice on. */
    private void apply(Invoice invoice, NodeName node, StageResult result) {
        applyPatch(invoice, result.patch());
        if (result.lines() != null) {
            invoice.replaceLines(result.lines());
        }
        if (result.outcome() != Outcome.WAIT) {
            // re-runs replace their own flags; overridden flags stay
            invoice.getFlags().removeIf(f -> node.name().equals(f.getNode()) && !f.isOverridden());
        }
        for (FlagDraft flag : result.flags()) {
            invoice.addFlag(new Flag(invoice, node.name(), flag.code(), flag.severity(), flag.message()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", result.message());
        if (result.reasonCode() != null) payload.put("reasonCode", result.reasonCode());
        if (result.patch().containsKey("riskScore")) payload.put("riskScore", result.patch().get("riskScore"));
        if (!result.flags().isEmpty()) payload.put("flags", result.flags().stream().map(FlagDraft::code).toList());
        if (result.auditDetails() != null && !result.auditDetails().isEmpty()) payload.putAll(result.auditDetails());

        switch (result.outcome()) {
            case PASS, FLAG -> {
                audit.system(invoice.getId(), "node." + result.outcome(), node.name(), null, null, payload);
                stateMachine.moveTo(invoice, node.statusOnPass(), Actor.SYSTEM, node.name(), null);
                NodeName next = node.next();
                if (next != null) jobs.enqueue(invoice, next, stepDelayMs);
            }
            case BLOCK -> {
                audit.system(invoice.getId(), "node.BLOCK", node.name(), null, null, payload);
                invoice.addFlag(new Flag(invoice, node.name(), result.reasonCode(), FlagSeverity.BLOCK, result.message()));
                stateMachine.hold(invoice, result.holdQueue(), result.reasonCode(), result.message(), Actor.SYSTEM,
                        node.name());
            }
            case WAIT -> {
                if (result.recheckAfterSeconds() == null) {
                    // Waiting for a person: log it once per run. Bank re-checks are not logged (too noisy).
                    audit.system(invoice.getId(), "node.WAIT", node.name(), null, null, payload);
                } else {
                    jobs.enqueue(invoice, node, result.recheckAfterSeconds() * 1000L);
                }
            }
            case IGNORE -> {
                audit.system(invoice.getId(), "node.IGNORE", node.name(), null, null, payload);
                stateMachine.moveTo(invoice, InvoiceStatus.IGNORED, Actor.SYSTEM, node.name(), payload);
            }
        }
    }

    /** A node threw: retry with exponential backoff (2s, 4s, ...), then park it in the node's queue. */
    private void handleCrash(Job job, Invoice invoice, Exception e) {
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        log.warn("Node {} failed on {} (attempt {}): {}", job.getNode(), invoice.getId(), job.getAttempts(), error);
        if (job.getAttempts() < maxAttempts) {
            long backoffMs = 1000L * (1L << job.getAttempts());
            jobs.retryLater(job.getId(), error, backoffMs);
            audit.system(invoice.getId(), "node.RETRY", job.getNode().name(), null, null,
                    Map.of("attempt", job.getAttempts(), "error", error, "retryInMs", backoffMs));
            return;
        }
        transaction.executeWithoutResult(tx -> {
            jobs.markFailed(job.getId(), error);
            String message = job.getNode().label() + " failed after " + maxAttempts + " attempts: " + error;
            Invoice managed = invoices.get(invoice.getId());
            managed.addFlag(new Flag(managed, job.getNode().name(), "NODE_FAILED", FlagSeverity.BLOCK, message));
            stateMachine.hold(managed, job.getNode().failureQueue(), "NODE_FAILED", message,
                    Actor.SYSTEM, job.getNode().name());
        });
    }

    /** Copies the node's patch onto the invoice, field by field, refusing anything not whitelisted. */
    private void applyPatch(Invoice invoice, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) return;
        BeanWrapperImpl wrapper = new BeanWrapperImpl(invoice);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (!PATCHABLE_FIELDS.contains(entry.getKey())) {
                throw new IllegalArgumentException("Invoice field not patchable: " + entry.getKey());
            }
            wrapper.setPropertyValue(entry.getKey(), entry.getValue());
        }
    }

    /** Nodes that only CHECK the invoice. They produce no data later nodes need, so an Admin override may skip them. */
    private static final Set<NodeName> SKIPPABLE_WHEN_OVERRIDDEN = Set.of(NodeName.VALIDATION_RULES, NodeName.FRAUD_SCORING);

    /**
     * "Mark issue resolved" on a hold overrides the BLOCK flags of the node that stopped the invoice. When that node
     * only checks (fraud scoring, validation), it is not run again: re-running would just block on the same
     * thing, or on a new signal the Admin never reviewed. Nodes that produce data (extraction, vendor match,
     * routing) always re-run, since the next nodes depend on what they write.
     */
    private StageResult skipIfOverridden(Invoice invoice, NodeName node) {
        if (!SKIPPABLE_WHEN_OVERRIDDEN.contains(node)) return null;
        return flags.findByInvoice_IdOrderById(invoice.getId()).stream()
                .filter(f -> node.name().equals(f.getNode()) && f.isOverridden() && f.getSeverity() == FlagSeverity.BLOCK)
                .findFirst()
                .map(f -> StageResult.pass(node.label() + " skipped: overridden by " + f.getOverriddenById()
                        + " (" + f.getOverrideReason() + ")"))
                .orElse(null);
    }

    /**
     * Demo hook so the exception queues can be shown while the nodes are still stubs:
     * upload a file whose name contains "block-<node>", e.g. "acme_block-vendor-match.pdf",
     * and that node BLOCKs once. After an Admin resumes it, it passes.
     * TODO: delete once the real nodes produce real blocks.
     */
    private StageResult demoBlock(Invoice invoice, NodeName node, SourceDocument document) {
        if (document == null || document.getFileName() == null) return null;
        String marker = "block-" + node.name().toLowerCase().replace('_', '-');
        if (!document.getFileName().toLowerCase().contains(marker)) return null;
        boolean alreadyResolved = flags.findByInvoice_IdOrderById(invoice.getId()).stream()
                .anyMatch(f -> "DEMO_BLOCK".equals(f.getCode()) && node.name().equals(f.getNode()) && f.isOverridden());
        if (alreadyResolved) return null;
        return StageResult.block(node.failureQueue(), "DEMO_BLOCK",
                "Demo block requested by file name (" + marker + ")");
    }
}
