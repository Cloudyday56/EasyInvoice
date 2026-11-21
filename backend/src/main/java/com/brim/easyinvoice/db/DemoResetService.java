package com.brim.easyinvoice.db;

import com.brim.easyinvoice.audit.Actor;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.payments.MockBankAdapter;
import com.brim.easyinvoice.pipeline.PipelineRunner;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.data.jpa.repository.JpaRepository;
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

    private final List<JpaRepository<?, ?>> allRepositories;   // Spring injects every repository
    private final SeedLoader seedLoader;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final PipelineRunner runner;
    private final MockBankAdapter bank;
    private final TransactionTemplate transaction;

    public DemoResetService(List<JpaRepository<?, ?>> allRepositories, SeedLoader seedLoader,
                            AuthorizationService authorization, AuditService audit, PipelineRunner runner,
                            MockBankAdapter bank, PlatformTransactionManager transactionManager) {
        this.allRepositories = allRepositories;
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
            allRepositories.forEach(JpaRepository::deleteAllInBatch);
            audit.record(Actor.of(user), null, null, "demo.reset", null, null, null,
                    Map.of("by", user.getFullName()));
            seedLoader.seedAll();
        }));
        bank.clear();
    }
}
