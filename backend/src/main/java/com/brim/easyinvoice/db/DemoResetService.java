package com.brim.easyinvoice.db;

import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditEvent;
import com.brim.easyinvoice.audit.AuditHead;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.*;
import com.brim.easyinvoice.payments.MockBankAdapter;
import com.brim.easyinvoice.pipeline.Job;
import com.brim.easyinvoice.pipeline.PipelineRunner;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * "Reset demo": empty every table and reseed from /data. It is an Admin-only, audited action,
 * and it starts a fresh audit chain.
 */
@Service
public class DemoResetService {

    /**
     * Every entity, children before the rows they point at (foreign keys refuse the opposite order).
     * Add new entities here; the end-to-end test fails if a table is forgotten.
     */
    private static final List<Class<?>> DELETE_ORDER = List.of(
            AuditEvent.class, AuditHead.class,
            CardTransaction.class, VirtualCard.class, Job.class, Notification.class,
            Payment.class, Flag.class, ApprovalStep.class, InvoiceLine.class,
            Invoice.class, SourceDocument.class,
            Contract.class, PurchaseOrder.class, BankChangeRequest.class, Vendor.class,
            User.class, Department.class,            // after the loop below has cut their links to each other
            GlAccount.class, TaxCode.class, FxRate.class, Policy.class, BankStatementLine.class,
            InvoiceNumber.class, PaymentNumber.class);

    private final EntityManager entityManager;
    private final SeedLoader seedLoader;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final PipelineRunner runner;
    private final MockBankAdapter bank;
    private final TransactionTemplate transaction;

    public DemoResetService(EntityManager entityManager, SeedLoader seedLoader, AuthorizationService authorization,
                            AuditService audit, PipelineRunner runner, MockBankAdapter bank,
                            PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.seedLoader = seedLoader;
        this.authorization = authorization;
        this.audit = audit;
        this.runner = runner;
        this.bank = bank;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void reset(User user) {
        authorization.authorize(user, Action.RUN_ADMIN_TOOLS, null);
        runner.pauseWhile(() -> transaction.executeWithoutResult(tx -> {
            deleteEverything();
            audit.record(Actor.of(user), null, null, "demo.reset", null, null, null,
                    Map.of("by", user.getFullName()));
            seedLoader.seedAll();
        }));
        bank.clear();
    }

    /** Invoice-side tables only, children first. Users, vendors, POs, contracts and other master data stay. */
    private static final List<Class<?>> INVOICE_TABLES = List.of(
            AuditEvent.class, AuditHead.class,
            CardTransaction.class, VirtualCard.class, Job.class, Notification.class,
            Payment.class, Flag.class, ApprovalStep.class, InvoiceLine.class,
            Invoice.class, SourceDocument.class, InvoiceNumber.class, PaymentNumber.class);

    /** Removes every invoice (seeded history and uploads) with its payments, flags, steps and audit trail. */
    public void clearInvoices(User user) {
        authorization.authorize(user, Action.RUN_ADMIN_TOOLS, null);
        runner.pauseWhile(() -> transaction.executeWithoutResult(tx -> {
            for (Class<?> entity : INVOICE_TABLES) {
                entityManager.createQuery("delete from " + entity.getSimpleName()).executeUpdate();
            }
            entityManager.clear();
            audit.record(Actor.of(user), null, null, "invoices.cleared", null, null, null,
                    Map.of("by", user.getFullName()));
        }));
        bank.clear();
    }

    private void deleteEverything() {
        // Users and departments point at each other (and users at users): cut those links first.
        entityManager.createQuery("update User u set u.manager = null, u.delegate = null, u.department = null")
                .executeUpdate();
        entityManager.createQuery("update Department d set d.manager = null").executeUpdate();
        for (Class<?> entity : DELETE_ORDER) {
            entityManager.createQuery("delete from " + entity.getSimpleName()).executeUpdate();
        }
        entityManager.clear();   // bulk deletes bypass the persistence context
    }
}
