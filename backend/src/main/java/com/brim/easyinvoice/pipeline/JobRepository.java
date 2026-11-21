package com.brim.easyinvoice.pipeline;

import com.brim.easyinvoice.model.Invoice;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** The pipeline's work queue, on plain JPA. */
public interface JobRepository extends JpaRepository<Job, Long> {

    boolean existsByInvoice_IdAndNodeAndStatus(String invoiceId, NodeName node, JobStatus status);

    /**
     * The next due job, locked with SELECT ... FOR UPDATE SKIP LOCKED (lock timeout -2 means "skip locked"),
     * so two runners would never grab the same job.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<Job> findFirstByStatusAndRunAfterLessThanEqualOrderByRunAfterAscIdAsc(JobStatus status, OffsetDateTime now);

    List<Job> findByStatus(JobStatus status);

    /** Queues a node run. Does nothing if the same run is already waiting (no double work). */
    @Transactional
    default void enqueue(Invoice invoice, NodeName node, long delayMs) {
        if (existsByInvoice_IdAndNodeAndStatus(invoice.getId(), node, JobStatus.PENDING)) return;
        Job job = new Job();
        job.setInvoice(invoice);
        job.setNode(node);
        job.setRunAfter(OffsetDateTime.now().plusNanos(delayMs * 1_000_000));
        save(job);
    }

    /** Takes the next due job and marks it RUNNING. */
    @Transactional
    default Optional<Job> claimNext() {
        Optional<Job> next = findFirstByStatusAndRunAfterLessThanEqualOrderByRunAfterAscIdAsc(JobStatus.PENDING, OffsetDateTime.now());
        next.ifPresent(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setAttempts(job.getAttempts() + 1);
        });
        return next;
    }

    @Transactional
    default void markDone(long jobId) {
        findById(jobId).ifPresent(job -> job.setStatus(JobStatus.DONE));
    }

    @Transactional
    default void retryLater(long jobId, String error, long delayMs) {
        findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.PENDING);
            job.setLastError(error);
            job.setRunAfter(OffsetDateTime.now().plusNanos(delayMs * 1_000_000));
        });
    }

    @Transactional
    default void markFailed(long jobId, String error) {
        findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setLastError(error);
        });
    }

    /** After a restart, anything left RUNNING was interrupted; run it again. */
    @Transactional
    default void requeueInterrupted() {
        findByStatus(JobStatus.RUNNING).forEach(job -> job.setStatus(JobStatus.PENDING));
    }
}
