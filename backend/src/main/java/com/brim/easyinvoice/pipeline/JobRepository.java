package com.brim.easyinvoice.pipeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JobRepository {

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Queues a node run. Does nothing if the same run is already waiting (no double work). */
    public void enqueue(String invoiceId, NodeName node, long delayMs) {
        jdbc.update("""
                INSERT INTO jobs (invoice_id, node, run_after)
                SELECT ?, ?, now() + (? * interval '1 millisecond')
                WHERE NOT EXISTS (SELECT 1 FROM jobs WHERE invoice_id = ? AND node = ? AND status = 'PENDING')
                """, invoiceId, node.name(), delayMs, invoiceId, node.name());
    }

    /**
     * Takes the next due job and marks it RUNNING in one statement.
     * SKIP LOCKED means two runners would never grab the same job.
     */
    public Optional<Job> claimNext() {
        return jdbc.query("""
                UPDATE jobs SET status = 'RUNNING', attempts = attempts + 1
                WHERE id = (
                    SELECT id FROM jobs
                    WHERE status = 'PENDING' AND run_after <= now()
                    ORDER BY run_after, id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, invoice_id, node, attempts
                """, (rs, i) -> new Job(rs.getLong("id"), rs.getString("invoice_id"),
                NodeName.valueOf(rs.getString("node")), rs.getInt("attempts"))).stream().findFirst();
    }

    public void markDone(long jobId) {
        jdbc.update("UPDATE jobs SET status = 'DONE' WHERE id = ?", jobId);
    }

    public void retryLater(long jobId, String error, long delayMs) {
        jdbc.update("UPDATE jobs SET status = 'PENDING', last_error = ?, run_after = now() + (? * interval '1 millisecond') "
                + "WHERE id = ?", error, delayMs, jobId);
    }

    public void markFailed(long jobId, String error) {
        jdbc.update("UPDATE jobs SET status = 'FAILED', last_error = ? WHERE id = ?", error, jobId);
    }

    /** After a restart, anything left RUNNING was interrupted; run it again. */
    public void requeueInterrupted() {
        jdbc.update("UPDATE jobs SET status = 'PENDING' WHERE status = 'RUNNING'");
    }

    public boolean hasPendingJob(String invoiceId, NodeName node) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM jobs WHERE invoice_id = ? AND node = ? "
                + "AND status IN ('PENDING','RUNNING')", Integer.class, invoiceId, node.name());
        return n != null && n > 0;
    }
}
