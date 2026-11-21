package com.brim.easyinvoice.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the hash chain as pure functions: build a chain in memory, tamper with it, verify. */
class AuditHashChainTest {

    @Test
    void untouchedChainVerifies() {
        List<AuditEvent> chain = buildChain(5);
        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isTrue();
        assertThat(result.checked()).isEqualTo(5);
    }

    @Test
    void firstRowChainsFromGenesis() {
        assertThat(buildChain(1).getFirst().prevHash()).isEqualTo(AuditHasher.GENESIS);
    }

    @Test
    void eachRowPointsAtThePreviousHash() {
        List<AuditEvent> chain = buildChain(3);
        assertThat(chain.get(1).prevHash()).isEqualTo(chain.get(0).hash());
        assertThat(chain.get(2).prevHash()).isEqualTo(chain.get(1).hash());
    }

    @Test
    void editingARowIsDetectedAtThatRow() {
        List<AuditEvent> chain = new ArrayList<>(buildChain(5));
        AuditEvent original = chain.get(2);
        chain.set(2, withPayload(original, "{\"amount\":999999}")); // someone edits the amount in place

        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo(original.id());
        assertThat(result.problem()).contains("edited");
    }

    @Test
    void editingARowAndRecomputingItsHashBreaksTheNextRow() {
        List<AuditEvent> chain = new ArrayList<>(buildChain(5));
        AuditEvent edited = withPayload(chain.get(2), "{\"amount\":999999}");
        String newHash = AuditHasher.hash(edited.prevHash(), edited);
        chain.set(2, withHash(edited, newHash)); // a smarter attacker fixes the row's own hash...

        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo(chain.get(3).id()); // ...but row 4 still points at the old hash
    }

    @Test
    void deletingARowIsDetected() {
        List<AuditEvent> chain = new ArrayList<>(buildChain(5));
        AuditEvent removed = chain.remove(1);

        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo(removed.id() + 1);
        assertThat(result.problem()).contains("prev_hash");
    }

    @Test
    void hashIsDeterministic() {
        AuditEvent e = event(1L, "approval.approved", "{\"step\":1}", AuditHasher.GENESIS);
        assertThat(AuditHasher.hash(AuditHasher.GENESIS, e)).isEqualTo(AuditHasher.hash(AuditHasher.GENESIS, e));
        assertThat(AuditHasher.hash(AuditHasher.GENESIS, e)).hasSize(64);
    }

    @Test
    void movingTextBetweenFieldsChangesTheHash() {
        // Without length prefixes, ("ab" + "c") and ("a" + "bc") would hash the same.
        AuditEvent a = new AuditEvent(1L, "ab", "c", "U1", null, "ADMIN", "x", null, null, null, null,
                Instant.parse("2026-11-21T10:00:00Z"), AuditHasher.GENESIS, null);
        AuditEvent b = new AuditEvent(1L, "a", "bc", "U1", null, "ADMIN", "x", null, null, null, null,
                Instant.parse("2026-11-21T10:00:00Z"), AuditHasher.GENESIS, null);
        assertThat(AuditHasher.hash(AuditHasher.GENESIS, a)).isNotEqualTo(AuditHasher.hash(AuditHasher.GENESIS, b));
    }

    @Test
    void emptyLogIsValid() {
        assertThat(AuditHasher.verify(List.of()).valid()).isTrue();
    }

    // ---------- helpers ----------

    /** Builds a chain the same way AuditService.record() does. */
    private static List<AuditEvent> buildChain(int size) {
        List<AuditEvent> chain = new ArrayList<>();
        String prev = AuditHasher.GENESIS;
        for (long id = 1; id <= size; id++) {
            AuditEvent draft = event(id, "status.changed", "{\"n\":" + id + "}", prev);
            AuditEvent stored = withHash(draft, AuditHasher.hash(prev, draft));
            chain.add(stored);
            prev = stored.hash();
        }
        return chain;
    }

    private static AuditEvent event(long id, String action, String payload, String prevHash) {
        return new AuditEvent(id, "EI-00001", null, "system", null, "SYSTEM", action, "EXTRACTION", "RECEIVED",
                "EXTRACTED", payload, Instant.parse("2026-11-21T10:00:00Z").plusSeconds(id), prevHash, null);
    }

    private static AuditEvent withPayload(AuditEvent e, String payload) {
        return new AuditEvent(e.id(), e.invoiceId(), e.paymentId(), e.actorId(), e.actingFor(), e.role(), e.action(),
                e.node(), e.fromStatus(), e.toStatus(), payload, e.createdAt(), e.prevHash(), e.hash());
    }

    private static AuditEvent withHash(AuditEvent e, String hash) {
        return new AuditEvent(e.id(), e.invoiceId(), e.paymentId(), e.actorId(), e.actingFor(), e.role(), e.action(),
                e.node(), e.fromStatus(), e.toStatus(), e.payloadJson(), e.createdAt(), e.prevHash(), hash);
    }
}
