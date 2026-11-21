package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.InvoiceLine;

import java.util.List;
import java.util.Map;

/**
 * What a node returns. The node only DESCRIBES the result; the runner persists it,
 * moves the status and writes the audit event.
 *
 * @param outcome             PASS | FLAG | BLOCK | WAIT | IGNORE
 * @param flags               warnings to attach to the invoice
 * @param patch               invoice columns to update (see InvoiceRepository.PATCHABLE_COLUMNS)
 * @param lines               replacement line items, or null to leave them as they are
 * @param reasonCode          short machine code, e.g. NO_VENDOR_MATCH
 * @param message             one human-readable sentence for the audit log and the UI
 * @param holdQueue           where a BLOCK parks the invoice
 * @param recheckAfterSeconds for WAIT: run this node again after N seconds (null = wait for a human)
 * @param auditDetails        optional structured facts copied into the audit payload (never into the invoice)
 */
public record StageResult(
        Outcome outcome,
        List<FlagDraft> flags,
        Map<String, Object> patch,
        List<InvoiceLine> lines,
        String reasonCode,
        String message,
        HoldQueue holdQueue,
        Integer recheckAfterSeconds,
        Map<String, Object> auditDetails) {

    /** Existing nodes keep using the original eight-argument form. */
    public StageResult(Outcome outcome, List<FlagDraft> flags, Map<String, Object> patch, List<InvoiceLine> lines,
                       String reasonCode, String message, HoldQueue holdQueue, Integer recheckAfterSeconds) {
        this(outcome, flags, patch, lines, reasonCode, message, holdQueue, recheckAfterSeconds, Map.of());
    }

    /** Returns a copy with extra audit facts; used by the fraud node for its score breakdown. */
    public StageResult withAuditDetails(Map<String, Object> details) {
        return new StageResult(outcome, flags, patch, lines, reasonCode, message, holdQueue, recheckAfterSeconds, details);
    }

    public static StageResult pass(String message) {
        return new StageResult(Outcome.PASS, List.of(), Map.of(), null, null, message, null, null);
    }

    public static StageResult pass(String message, Map<String, Object> patch) {
        return new StageResult(Outcome.PASS, List.of(), patch, null, null, message, null, null);
    }

    public static StageResult pass(String message, Map<String, Object> patch, List<InvoiceLine> lines) {
        return new StageResult(Outcome.PASS, List.of(), patch, lines, null, message, null, null);
    }

    /** A pass that still attaches warning flags. Erasure prevents overloading pass() with a FlagDraft list. */
    public static StageResult passWithFlags(String message, Map<String, Object> patch, List<FlagDraft> flags) {
        return new StageResult(Outcome.PASS, flags, patch, null, null, message, null, null);
    }

    public static StageResult flag(String message, List<FlagDraft> flags, Map<String, Object> patch,
                                   List<InvoiceLine> lines) {
        return new StageResult(Outcome.FLAG, flags, patch, lines, null, message, null, null);
    }

    public static StageResult block(HoldQueue queue, String reasonCode, String message) {
        return new StageResult(Outcome.BLOCK, List.of(), Map.of(), null, reasonCode, message, queue, null);
    }

    /** A block that also persists its score and the signals that produced it. */
    public static StageResult blockWithFlags(HoldQueue queue, String reasonCode, String message,
                                             Map<String, Object> patch, List<FlagDraft> flags) {
        return new StageResult(Outcome.BLOCK, flags, patch, null, reasonCode, message, queue, null);
    }

    public static StageResult waitForHuman(String message) {
        return new StageResult(Outcome.WAIT, List.of(), Map.of(), null, null, message, null, null);
    }

    public static StageResult recheckIn(int seconds, String message) {
        return new StageResult(Outcome.WAIT, List.of(), Map.of(), null, null, message, null, seconds);
    }

    public static StageResult ignore(String reasonCode, String message) {
        return new StageResult(Outcome.IGNORE, List.of(), Map.of(), null, reasonCode, message, null, null);
    }
}
