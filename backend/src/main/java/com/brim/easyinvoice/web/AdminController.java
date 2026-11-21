package com.brim.easyinvoice.web;

import com.brim.easyinvoice.db.DemoResetService;
import com.brim.easyinvoice.intake.IntakeService;
import com.brim.easyinvoice.llm.LlmClient;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record PromptRequest(String prompt) {
    }

    private final CurrentUser currentUser;
    private final DemoResetService demoReset;
    private final IntakeService intake;
    private final LlmClient llm;
    private final AuthorizationService authorization;
    private final String extractor;

    public AdminController(CurrentUser currentUser, DemoResetService demoReset, IntakeService intake, LlmClient llm,
                           AuthorizationService authorization, @Value("${app.extractor}") String extractor) {
        this.currentUser = currentUser;
        this.demoReset = demoReset;
        this.intake = intake;
        this.llm = llm;
        this.authorization = authorization;
        this.extractor = extractor;
    }

    @PostMapping("/reset-demo")
    public Map<String, String> resetDemo(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        demoReset.reset(currentUser.require(userId));
        return Map.of("status", "reset");
    }

    /** Deletes every invoice but keeps users, vendors and other master data. */
    @PostMapping("/clear-invoices")
    public Map<String, String> clearInvoices(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        demoReset.clearInvoices(currentUser.require(userId));
        return Map.of("status", "cleared");
    }

    @PostMapping("/poll-inbox")
    public Map<String, Integer> pollInbox(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        return intake.pollInbox(currentUser.require(userId));
    }

    /** Is an OpenRouter key configured, and which extractor/model is active? Never returns the key. */
    @GetMapping("/llm")
    public Map<String, Object> llmStatus(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        currentUser.require(userId);
        return Map.of("configured", llm.isConfigured(), "model", llm.model(), "extractor", extractor);
    }

    /** Sends one small prompt to check the key works end to end. */
    @PostMapping("/llm/test")
    public Map<String, String> llmTest(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
                                       @RequestBody(required = false) PromptRequest body) {
        authorization.authorize(currentUser.require(userId), Action.RUN_ADMIN_TOOLS, null);
        String prompt = body == null || body.prompt() == null ? "Reply with exactly: EasyInvoice LLM OK" : body.prompt();
        return Map.of("model", llm.model(), "reply", llm.complete("You are a terse assistant.", prompt));
    }
}
