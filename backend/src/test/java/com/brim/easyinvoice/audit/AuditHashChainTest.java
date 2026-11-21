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
        assertThat(buildChain(1).getFirst().getPrevHash()).isEqualTo(AuditHasher.GENESIS);
    }

    @Test
    void eachRowPointsAtThePreviousHash() {
        List<AuditEvent> chain = buildChain(3);
        assertThat(chain.get(1).getPrevHash()).isEqualTo(chain.get(0).getHash());
        assertThat(chain.get(2).getPrevHash()).isEqualTo(chain.get(1).getHash());
    }

    @Test
    void editingARowIsDetectedAtThatRow() {
        List<AuditEvent> chain = new ArrayList<>(buildChain(5));
        AuditEvent original = chain.get(2);
        chain.set(2, withPayload(original, "{\"amount\":999999}")); // someone edits the amount in place

        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo(original.getId());
        assertThat(result.problem()).contains("edited");
    }

    @Test
    void editingARowAndRecomputingItsHashBreaksTheNextRow() {
        List<AuditEvent> chain = new ArrayList<>(buildChain(5));
        AuditEvent edited = withPayload(chain.get(2), "{\"amount\":999999}");
        String newHash = AuditHasher.hash(edited.getPrevHash(), edited);
        chain.set(2, withHash(edited, newHash)); // a smarter attacker fixes the row's own hash...

        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo(chain.get(3).getId()); // ...but row 4 still points at the old hash
    }

    @Test
    void deletingARowIsDetected() {
        List<AuditEvent> chain = new ArrayList<>(buildChain(5));
        AuditEvent removed = chain.remove(1);

        AuditHasher.VerifyResult result = AuditHasher.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtId()).isEqualTo(removed.getId() + 1);
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
        AuditEvent a = AuditEvent.builder().id(1L).invoiceId("ab").paymentId("c").actorId("U1").role("ADMIN")
                .action("x").createdAt(Instant.parse("2026-11-21T10:00:00Z")).prevHash(AuditHasher.GENESIS).build();
        AuditEvent b = AuditEvent.builder().id(1L).invoiceId("a").paymentId("bc").actorId("U1").role("ADMIN")
                .action("x").createdAt(Instant.parse("2026-11-21T10:00:00Z")).prevHash(AuditHasher.GENESIS).build();
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
            prev = stored.getHash();
        }
        return chain;
    }

    private static AuditEvent event(long id, String action, String payload, String prevHash) {
        return AuditEvent.builder().id(id).invoiceId("EI-00001").actorId("system").role("SYSTEM").action(action)
                .node("EXTRACTION").fromStatus("RECEIVED").toStatus("EXTRACTED").payloadJson(payload)
                .createdAt(Instant.parse("2026-11-21T10:00:00Z").plusSeconds(id)).prevHash(prevHash).build();
    }

    private static AuditEvent withPayload(AuditEvent e, String payload) {
        return e.toBuilder().payloadJson(payload).build();
    }

    private static AuditEvent withHash(AuditEvent e, String hash) {
        return e.toBuilder().hash(hash).build();
    }
}
