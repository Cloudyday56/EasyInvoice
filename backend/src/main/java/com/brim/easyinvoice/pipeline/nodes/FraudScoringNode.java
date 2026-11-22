package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.BankChangeRequest;
import com.brim.easyinvoice.model.Flag;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.pipeline.FlagDraft;
import com.brim.easyinvoice.pipeline.FraudScoringConfig;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.BankChangeRequestRepository;
import com.brim.easyinvoice.repo.FlagRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Job: catch payment fraud before money moves. Weighted signals -> 0..100 risk score.
 *
 * Signals: remit-to bank mismatch (immediate block), unverified recent bank change, lookalike /
 * free-mail sender domain, duplicate invoice number, split invoices, and amount outlier.
 * Outcomes: score < passBelow -> PASS, < blockAt -> FLAG, otherwise -> BLOCK (FRAUD_HOLD).
 */
@Component
public class FraudScoringNode implements Node {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+)");
    private static final Set<String> BANK_IDENTIFIERS = Set.of("bank", "institution", "transit", "routing", "account", "iban");

    private final FraudScoringConfig config;
    private final InvoiceRepository invoices;
    private final BankChangeRequestRepository bankChanges;
    private final FlagRepository flags;

    public FraudScoringNode(FraudScoringConfig config, InvoiceRepository invoices,
                            BankChangeRequestRepository bankChanges, FlagRepository flags) {
        this.config = config;
        this.invoices = invoices;
        this.bankChanges = bankChanges;
        this.flags = flags;
    }

    @Override
    public NodeName name() {
        return NodeName.FRAUD_SCORING;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        if (invoice == null || invoice.getVendor() == null) {
            return StageResult.pass("No scored fraud signals (vendor unknown)", Map.of("riskScore", 0));
        }

        Set<String> overridden = overriddenCodes(invoice);
        List<Signal> signals = new ArrayList<>();
        Vendor vendor = invoice.getVendor();

        Signal bankMismatch = bankMismatch(vendor, invoice);
        if (bankMismatch != null && !overridden.contains(bankMismatch.code())) signals.add(bankMismatch);

        Signal bankChange = unverifiedBankChange(vendor, invoice);
        if (bankChange != null && !overridden.contains(bankChange.code())) signals.add(bankChange);

        signals.addAll(senderSignals(invoice, ctx, overridden));

        Signal duplicate = duplicateInvoice(invoice, vendor);
        if (duplicate != null && !overridden.contains(duplicate.code())) signals.add(duplicate);

        Signal split = splitInvoice(invoice, vendor);
        if (split != null && !overridden.contains(split.code())) signals.add(split);

        Signal outlier = amountOutlier(invoice, vendor);
        if (outlier != null && !overridden.contains(outlier.code())) signals.add(outlier);

        int score = Math.min(100, signals.stream().mapToInt(Signal::points).sum());
        List<FlagDraft> drafts = signals.stream()
                .map(s -> FlagDraft.warning(s.code(), s.message()))
                .toList();
        Map<String, Object> patch = Map.of("riskScore", score);
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        signals.forEach(s -> breakdown.merge(s.code(), s.points(), Integer::sum));
        Map<String, Object> auditDetails = Map.of(
                "fraudSignals", signals.stream().map(Signal::code).toList(),
                "fraudScoreBreakdown", breakdown,
                "scoreThresholds", Map.of("passBelow", config.getPassBelow(), "blockAt", config.getBlockAt()));
        String summary = signals.isEmpty()
                ? "No fraud signals detected"
                : "Fraud risk score " + score + " — "
                    + signals.stream().map(Signal::code).reduce((a, b) -> a + ", " + b).orElse("");

        boolean bankMismatchBlock = signals.stream().anyMatch(s -> s.code().equals("BANK_DETAILS_MISMATCH"));
        if (bankMismatchBlock || score >= config.getBlockAt()) {
            return StageResult.blockWithFlags(HoldQueue.FRAUD_HOLD, "FRAUD_RISK", summary, patch, drafts)
                    .withAuditDetails(auditDetails);
        }
        if (score >= config.getPassBelow()) {
            return StageResult.flag(summary, drafts, patch, null).withAuditDetails(auditDetails);
        }
        return StageResult.passWithFlags(summary, patch, drafts).withAuditDetails(auditDetails);
    }

    private Set<String> overriddenCodes(Invoice invoice) {
        Set<String> codes = new LinkedHashSet<>();
        for (Flag flag : flags.findByInvoice_IdOrderById(invoice.getId())) {
            if (NodeName.FRAUD_SCORING.name().equals(flag.getNode()) && flag.isOverridden()) {
                codes.add(flag.getCode());
            }
        }
        return codes;
    }

    // ---------- signal detection ----------

    private Signal bankMismatch(Vendor vendor, Invoice invoice) {
        if (!hasText(invoice.getRemitBankInstitution()) && !hasText(invoice.getRemitBankTransit())
                && !hasText(invoice.getRemitBankAccount())) {
            return null;
        }
        if (same(vendor.getBankInstitution(), invoice.getRemitBankInstitution())
                && same(vendor.getBankTransit(), invoice.getRemitBankTransit())
                && same(vendor.getBankAccount(), invoice.getRemitBankAccount())) {
            return null;
        }
        return new Signal("BANK_DETAILS_MISMATCH", config.getBankMismatchWeight(),
                "Remit-to bank details on the invoice do not match the vendor master");
    }

    private Signal unverifiedBankChange(Vendor vendor, Invoice invoice) {
        LocalDate from = (invoice.getInvoiceDate() == null ? LocalDate.now() : invoice.getInvoiceDate())
                .minusDays(config.getBankChangeWindowDays());
        List<BankChangeRequest> changes = bankChanges
                .findByVendor_IdAndChangedAtGreaterThanEqualOrderByChangedAtDesc(vendor.getId(), from);
        boolean unverified = changes.stream().anyMatch(c -> !c.isVerifiedByCallback()
                || !"VERIFIED".equalsIgnoreCase(c.getStatus()));
        if (!unverified) return null;
        return new Signal("UNVERIFIED_BANK_CHANGE", config.getUnverifiedBankChangeWeight(),
                "Vendor bank details changed without a verified callback in the last "
                        + config.getBankChangeWindowDays() + " days");
    }

    private List<Signal> senderSignals(Invoice invoice, NodeContext ctx, Set<String> overridden) {
        List<Signal> signals = new ArrayList<>();
        SourceDocument document = ctx == null ? null : ctx.document();
        String sender = document == null ? null : document.getSender();
        Matcher matcher = sender == null ? null : EMAIL_PATTERN.matcher(sender);
        if (matcher == null || !matcher.find()) return signals;

        String domain = matcher.group(2).toLowerCase(Locale.ROOT);
        String localPart = matcher.group(1).toLowerCase(Locale.ROOT);
        boolean freeMail = config.getFreeMailDomains().stream()
                .anyMatch(d -> domain.equals(d.toLowerCase(Locale.ROOT)));
        boolean lookalike = isLookalikeDomain(domain, invoice.getVendor().getLegalName(),
                invoice.getVendor().getApContactEmail());
        boolean vendorMismatch = hasText(invoice.getVendor().getApContactEmail())
                && !domain.equals(domainOf(invoice.getVendor().getApContactEmail()))
                && !lookalike;

        if (freeMail && !overridden.contains("FREE_MAIL_DOMAIN")) {
            signals.add(new Signal("FREE_MAIL_DOMAIN", config.getFreeMailDomainWeight(),
                    "Invoice came from a free-mail sender domain: " + domain));
        }
        if (lookalike && !overridden.contains("LOOKALIKE_SENDER_DOMAIN")) {
            signals.add(new Signal("LOOKALIKE_SENDER_DOMAIN", config.getLookalikeDomainWeight(),
                    "Sender domain " + domain + " looks like a lookalike of the vendor's domain"));
        }
        if (vendorMismatch && !overridden.contains("SENDER_DOMAIN_MISMATCH")) {
            signals.add(new Signal("SENDER_DOMAIN_MISMATCH", config.getSenderDomainMismatchWeight(),
                    "Sender domain " + domain + " differs from the vendor's AP contact domain"));
        }
        // A display name pretending to be the bank also counts as a bank-related signal.
        if (BANK_IDENTIFIERS.stream().anyMatch(localPart::contains)
                && !overridden.contains("BANK_DETAILS_MISMATCH")) {
            signals.add(new Signal("BANK_DETAILS_MISMATCH", config.getBankMismatchWeight(),
                    "Sender display name suggests a bank: " + matcher.group(1)));
        }
        return signals;
    }

    private Signal duplicateInvoice(Invoice invoice, Vendor vendor) {
        if (!hasText(invoice.getCanonicalInvoiceNumber())) return null;
        boolean repeated = invoices.existsByVendor_IdAndCanonicalInvoiceNumberAndIdNot(
                vendor.getId(), invoice.getCanonicalInvoiceNumber(), invoice.getId());
        if (!repeated) return null;
        return new Signal("DUPLICATE_INVOICE_NUMBER", config.getDuplicateInvoiceWeight(),
                "Vendor " + vendor.getLegalName() + " already has invoice "
                        + invoice.getCanonicalInvoiceNumber());
    }

    private Signal splitInvoice(Invoice invoice, Vendor vendor) {
        if (invoice.getInvoiceDate() == null || invoice.getTotalCad() == null
                || invoice.getTotalCad().compareTo(config.getSplitLimitCad()) >= 0) {
            return null;
        }
        BigDecimal tolerance = config.getSplitLimitCad()
                .multiply(BigDecimal.valueOf(config.getSplitTolerancePercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        LocalDate from = invoice.getInvoiceDate().minusDays(config.getSplitWindowDays());
        LocalDate to = invoice.getInvoiceDate().plusDays(config.getSplitWindowDays());
        long matches = invoices.findByVendorAndDateWindow(vendor.getId(), invoice.getId(), from, to).stream()
                .filter(i -> i.getTotalCad() != null && i.getTotalCad().compareTo(config.getSplitLimitCad()) < 0)
                .filter(i -> i.getTotalCad().subtract(invoice.getTotalCad()).abs().compareTo(tolerance) <= 0)
                .count();
        if (matches == 0) return null;
        return new Signal("SPLIT_INVOICE", config.getSplitInvoiceWeight(),
                matches + " similarly sized invoice(s) from this vendor within "
                        + config.getSplitWindowDays() + " day(s)");
    }

    private Signal amountOutlier(Invoice invoice, Vendor vendor) {
        if (invoice.getTotalCad() == null) return null;
        List<BigDecimal> history = invoices.findByVendor_IdAndIdNot(vendor.getId(), invoice.getId()).stream()
                .map(Invoice::getTotalCad)
                .filter(java.util.Objects::nonNull)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (history.size() < config.getMinHistorySamples()) return null;

        BigDecimal sum = history.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(history.size()), 6, RoundingMode.HALF_UP);
        double variance = history.stream()
                .mapToDouble(v -> v.subtract(mean).pow(2).doubleValue())
                .sum() / history.size();
        double deviation = Math.sqrt(variance);
        if (deviation <= 0
                || invoice.getTotalCad().doubleValue() <= mean.doubleValue()
                || invoice.getTotalCad().doubleValue() <= mean.doubleValue() + config.getOutlierSigma() * deviation) {
            return null;
        }
        return new Signal("AMOUNT_OUTLIER", config.getAmountOutlierWeight(),
                "Invoice total is more than " + config.getOutlierSigma()
                        + " standard deviations above this vendor's average");
    }

    // ---------- small helpers ----------

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean same(String left, String right) {
        if (!hasText(left) || !hasText(right)) return false;
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String domainOf(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isLookalikeDomain(String domain, String legalName, String apEmail) {
        String normalizedDomain = domain.replaceAll("[^a-z0-9]", "");
        List<String> expected = new ArrayList<>();
        if (hasText(apEmail)) expected.add(domainOf(apEmail).replaceAll("[^a-z0-9]", ""));
        if (hasText(legalName)) {
            String name = normalize(legalName);
            expected.add(name);
            int firstWordEnd = name.indexOf('c');
            if (firstWordEnd > 3) expected.add(name.substring(0, firstWordEnd));
        }
        return expected.stream().filter(s -> s.length() >= 4).anyMatch(candidate ->
                !candidate.equals(normalizedDomain)
                        && (normalizedDomain.contains(candidate) || candidate.contains(normalizedDomain)
                            || editDistance(normalizedDomain, candidate) <= 2));
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private record Signal(String code, int points, String message) {}
}
