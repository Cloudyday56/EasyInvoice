package com.brim.easyinvoice.db;

import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.payments.MockBankAdapter;
import com.brim.easyinvoice.pipeline.PipelineRunner;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * "Reset demo": wipe every table and reseed from /data.
 * TRUNCATE is used because the audit table refuses DELETE; this is the one deliberate way
 * to start a fresh audit chain, and it is an Admin-only, audited action.
 */
@Service
public class DemoResetService {

    private static final String ALL_TABLES = String.join(", ",
            "audit_events", "jobs", "notifications", "card_transactions", "virtual_cards", "payments",
            "approval_steps", "flags", "invoice_lines", "invoices", "source_documents", "policies",
            "bank_statement_lines", "fx_rates", "tax_codes", "contracts", "purchase_orders",
            "bank_change_requests", "vendors", "gl_accounts", "users", "departments");

    private final JdbcTemplate jdbc;
    private final SeedLoader seedLoader;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final PipelineRunner runner;
    private final MockBankAdapter bank;
    private final TransactionTemplate transaction;

    public DemoResetService(JdbcTemplate jdbc, SeedLoader seedLoader, AuthorizationService authorization,
                            AuditService audit, PipelineRunner runner, MockBankAdapter bank,
                            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
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
            jdbc.execute("TRUNCATE " + ALL_TABLES + " RESTART IDENTITY CASCADE");
            jdbc.execute("ALTER SEQUENCE new_invoice_seq RESTART");
            jdbc.execute("ALTER SEQUENCE new_payment_seq RESTART");
            audit.record(Actor.of(user), null, null, "demo.reset", null, null, null,
                    Map.of("by", user.getFullName()));
            seedLoader.seedAll();
        }));
        bank.clear();
    }
}
