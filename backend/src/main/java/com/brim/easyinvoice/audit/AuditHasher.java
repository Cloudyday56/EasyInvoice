package com.brim.easyinvoice.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The hash chain, as pure functions (no database), so it is easy to unit test.
 *
 *   hash = sha256(prev_hash + row)
 *
 * Each row's hash covers the previous row's hash, so editing or deleting any past row
 * breaks every hash after it.
 */
public final class AuditHasher {

    /** prev_hash of the very first row. */
    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    /**
     * The row as one string. Each field is written as "length:value|" so field boundaries
     * can't be faked (moving a character from one field to the next changes the lengths).
     */
    public static String canonicalRow(AuditEvent e) {
        StringBuilder sb = new StringBuilder();
        for (String field : List.of(
                nullToEmpty(e.invoiceId()), nullToEmpty(e.paymentId()), nullToEmpty(e.actorId()),
                nullToEmpty(e.actingFor()), nullToEmpty(e.role()), nullToEmpty(e.action()), nullToEmpty(e.node()),
                nullToEmpty(e.fromStatus()), nullToEmpty(e.toStatus()), nullToEmpty(e.payloadJson()),
                e.createdAt().toString())) {
            sb.append(field.length()).append(':').append(field).append('|');
        }
        return sb.toString();
    }

    public static String hash(String prevHash, AuditEvent e) {
        return sha256(prevHash + canonicalRow(e));
    }

    /** Walks the chain in order and reports the first row that does not fit. */
    public static VerifyResult verify(List<AuditEvent> eventsInOrder) {
        String expectedPrev = GENESIS;
        for (AuditEvent e : eventsInOrder) {
            if (!e.prevHash().equals(expectedPrev)) {
                return VerifyResult.broken(eventsInOrder.size(), e.id(), "prev_hash does not match the previous row (row missing or reordered)");
            }
            if (!hash(e.prevHash(), e).equals(e.hash())) {
                return VerifyResult.broken(eventsInOrder.size(), e.id(), "hash does not match the row contents (row was edited)");
            }
            expectedPrev = e.hash();
        }
        return VerifyResult.ok(eventsInOrder.size());
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record VerifyResult(boolean valid, int checked, Long brokenAtId, String problem) {
        static VerifyResult ok(int checked) {
            return new VerifyResult(true, checked, null, null);
        }

        static VerifyResult broken(int checked, Long id, String problem) {
            return new VerifyResult(false, checked, id, problem);
        }
    }
}
